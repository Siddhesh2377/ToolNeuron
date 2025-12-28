#include "model_state.h"
#include "../utils/logger.h"

#include <cstring>      // memcpy
#include <algorithm>
#include <sstream>
#include <vector>
#include <jni.h>
#include <src/llama-sampling.h>
#include "llama.h"

//////////////////////////////////////////////////////////////////////
// Basic helpers
//////////////////////////////////////////////////////////////////////

void ModelState::rebuild_sampler(
        int topK,
        float topP,
        float temp,
        float minP,
        int mirostat,
        float mirostatTau,
        float mirostatEta,
        int seed) {

    // Free existing sampler
    if (sampler) {
        llama_sampler_free(sampler);
        sampler = nullptr;
    }

    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (!vocab) {
        LOG_ERROR("Failed to get vocab for sampler rebuild");
        return;
    }

    // Initialize default chain parameters
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *chain = llama_sampler_chain_init(sparams);

    // Add grammar sampler first (if any tools exist)
    if (tools_enabled && grammar_sampler)
        llama_sampler_chain_add(chain, grammar_sampler);

    // --- Mirostat branch ---
    if (mirostat > 0) {
        auto *mirostatSampler = llama_sampler_init_mirostat(
                llama_vocab_n_tokens(vocab),
                seed,
                mirostatTau,
                mirostatEta,
                100 // m window
        );
        llama_sampler_chain_add(chain, mirostatSampler);
    }
        // --- Standard sampling branch ---
    else {
        llama_sampler_chain_add(chain, llama_sampler_init_top_k(topK));

        if (topP < 1.0f)
            llama_sampler_chain_add(chain, llama_sampler_init_top_p(topP, 1));

        if (std::abs(temp - 1.0f) > 1e-3f)
            llama_sampler_chain_add(chain, llama_sampler_init_temp(temp));

        if (temp > 0.0f)
            llama_sampler_chain_add(chain, llama_sampler_init_dist(-1));

        if (minP > 0.0f)
            llama_sampler_chain_add(chain, llama_sampler_init_min_p(minP, 1));
    }

    sampler = chain;
    llama_sampler_reset(sampler);

    LOG_INFO("Sampler rebuilt: topK=%d, topP=%.2f, temp=%.2f, minP=%.2f, "
             "mirostat=%d, tau=%.2f, eta=%.2f, seed=%d",
             topK, topP, temp, minP,
             mirostat, mirostatTau, mirostatEta, seed);
}



std::vector<llama_token> ModelState::tokenize(const std::string &text) const {
    if (!model) return {};

    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (!vocab) return {};

    int32_t guess = static_cast<int32_t>(text.size() + 8);
    std::vector<llama_token> toks((size_t) guess);

    int32_t n = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), toks.data(),
                               static_cast<int32_t>(toks.size()), true, true);
    if (n < 0) {
        // Error – allocate exact space and retry
        toks.resize((size_t) (-n));
        n = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), toks.data(),
                           static_cast<int32_t>(toks.size()), true, true);
    }
    if (n < 0) {
        LOG_ERROR("ModelState::tokenize: tokenisation failed");
        return {};
    }
    toks.resize((size_t) n);
    return toks;
}

std::string ModelState::detokenize_single(llama_token t) const {
    if (!model) return {};
    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (!vocab) return {};

    char buffer[512];
    int n = llama_token_to_piece(vocab, t, buffer, sizeof(buffer) - 1, 0, false);

    if (n < 0) {
        std::string out((size_t) (-n), '\0');
        n = llama_token_to_piece(vocab, t, out.data(), -n, 0, false);
        if (n < 0) {
            LOG_ERROR("Failed to detokenize token %d", t);
            return {};
        }
        return out;
    }

    return std::string(buffer, (size_t) n);
}

std::string ModelState::detokenize_buffered(llama_token t) {
    // Get raw token bytes
    std::string piece = detokenize_single(t);
    if (piece.empty()) return {};

    // Add to carry buffer
    utf8_carry_buffer += piece;

    // Extract complete UTF-8 characters
    std::string complete_chars;
    size_t i = 0;

    while (i < utf8_carry_buffer.size()) {
        auto c = static_cast<unsigned char>(utf8_carry_buffer[i]);
        size_t char_len = 0;

        // Determine UTF-8 character length
        if ((c & 0x80) == 0x00) {
            char_len = 1; // ASCII (0xxxxxxx)
        } else if ((c & 0xE0) == 0xC0) {
            char_len = 2; // 2-byte (110xxxxx)
        } else if ((c & 0xF0) == 0xE0) {
            char_len = 3; // 3-byte (1110xxxx)
        } else if ((c & 0xF8) == 0xF0) {
            char_len = 4; // 4-byte (11110xxx) - EMOJIS!
        } else {
            // Invalid UTF-8 start byte - skip it
            LOG_WARN("Invalid UTF-8 start byte: 0x%02X at position %zu", c, i);
            i++;
            continue;
        }

        // Check if we have enough bytes for complete character
        if (i + char_len > utf8_carry_buffer.size()) {
            // Incomplete character - keep in buffer
            break;
        }

        // Validate continuation bytes
        bool valid = true;
        for (size_t j = 1; j < char_len; ++j) {
            auto cont = static_cast<unsigned char>(utf8_carry_buffer[i + j]);
            if ((cont & 0xC0) != 0x80) { // Must be 10xxxxxx
                valid = false;
                LOG_WARN("Invalid UTF-8 continuation byte: 0x%02X", cont);
                break;
            }
        }

        if (valid) {
            // Complete valid UTF-8 character
            complete_chars.append(utf8_carry_buffer.substr(i, char_len));
            i += char_len;
        } else {
            // Invalid sequence - skip the start byte
            i++;
        }
    }

    // Remove processed characters from buffer
    utf8_carry_buffer = utf8_carry_buffer.substr(i);

    return complete_chars;
}

std::string ModelState::flush_utf8_buffer() {
    std::string remaining = utf8_carry_buffer;
    utf8_carry_buffer.clear();

    // If there are incomplete bytes, log a warning
    if (!remaining.empty()) {
        LOG_WARN("Flushing incomplete UTF-8 sequence: %zu bytes", remaining.size());
        for (unsigned char c: remaining) {
            LOG_WARN("  Byte: 0x%02X", c);
        }
    }

    return remaining;
}

llama_token ModelState::space_token() const {
    if (!model) return 0;
    const llama_vocab *vocab = llama_model_get_vocab(model);
    llama_token out[4];
    int n = llama_tokenize(vocab, " ", 1, out, 4, true, true);
    return (n > 0) ? out[0] : 0;
}

// ------------------------------------------------------------------
// Resource management
// ------------------------------------------------------------------
void ModelState::release() {
    if (grammar_sampler) {
        llama_sampler_free(grammar_sampler);
        grammar_sampler = nullptr;
    }
    if (sampler) {
        llama_sampler_free(sampler);
        sampler = nullptr;
    }
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }

    // ✅ Clear UTF-8 buffer on release
    utf8_carry_buffer.clear();

    llama_backend_free();
    LOG_INFO("ModelState: all resources released");
}

// ------------------------------------------------------------------
// KV cache / sampler reset
// ------------------------------------------------------------------
void ModelState::prepare_for_generation() {
    if (!ctx) return;
    llama_memory_t mem = llama_get_memory(ctx);
    if (mem) llama_memory_clear(mem, true);   // wipe KV cache

    if (sampler) llama_sampler_reset(sampler);

    // ✅ Clear UTF-8 buffer when starting new generation
    utf8_carry_buffer.clear();
}

// ------------------------------------------------------------------
// Prompt decoding
// ------------------------------------------------------------------
bool ModelState::decode_prompt(const std::vector<llama_token> &toks) const {
    if (!ctx || toks.empty()) return true;

    llama_batch batch = llama_batch_init(batch_size, 0, 1);
    int32_t pos = 0, idx = 0;
    while (idx < toks.size()) {
        int32_t take = std::min<int32_t>(batch_size, static_cast<int32_t>(toks.size() - idx));
        batch.n_tokens = take;
        for (int i = 0; i < take; ++i) {
            batch.token[i] = toks[idx + i];
            batch.pos[i] = pos + i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = (i == take - 1);
        }
        if (llama_decode(ctx, batch) != 0) {
            LOG_ERROR("ModelState::decode_prompt: llama_decode failed");
            llama_batch_free(batch);
            return false;
        }
        pos += take;
        idx += static_cast<size_t>(take);
    }
    llama_batch_free(batch);
    return true;
}

// ------------------------------------------------------------------
// Warm‑up to prime the model
// ------------------------------------------------------------------
void ModelState::warmup_context() const {
    llama_token space = space_token();
    if (space == 0) return;

    llama_batch batch = llama_batch_init(1, 0, 1);
    batch.n_tokens = 1;
    batch.token[0] = space;
    batch.pos[0] = 0;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0][0] = 0;
    batch.logits[0] = true;

    llama_decode(ctx, batch);
    llama_batch_free(batch);
}

// ------------------------------------------------------------------
// State persistence
// ------------------------------------------------------------------
jlong ModelState::get_state_size() const {
    return llama_state_get_size(ctx);
}

void *ModelState::get_state_data(void *buffer, size_t size) const {
    if (!ctx) return nullptr;
    return reinterpret_cast<void *>(llama_state_get_data(ctx, static_cast<uint8_t *>(buffer),
                                                         size));
}

bool ModelState::load_state_data(const void *data, size_t size) const {
    if (!ctx) return false;
    size_t n = llama_state_set_data(ctx, static_cast<const uint8_t *>(data), size);
    return n == size;
}
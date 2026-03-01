/**
 * Tool-Neuron JNI Bridge
 *
 * Drop-in replacement for ai_gguf-release.aar native methods.
 * Matches the exact GGUFNativeLib interface expected by GGUFEngine.kt.
 * Uses llama.cpp APIs directly for full feature parity.
 */

#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <thread>
#include <cstring>
#include <cstdlib>
#include <cmath>
#include <chrono>

#include <android/log.h>

#include "llama.h"
#include "common.h"
#include "sampling.h"
#include "chat.h"

#include "tool-manager.h"
#include "character-engine.h"

#include <nlohmann/json.hpp>

#define TAG "ToolNeuron-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using json = nlohmann::ordered_json;

// ============================================================================
// Global engine state (singleton — one model at a time, matches AAR behavior)
// ============================================================================

static struct {
    llama_model   * model   = nullptr;
    llama_context * ctx     = nullptr;
    common_sampler * sampler = nullptr;

    common_chat_templates_ptr chat_templates;

    // Sampling params (set via nativeSetSampling, updated via nativeUpdateSamplerParams)
    common_params_sampling sampling_params;

    // Config
    std::string system_prompt;
    std::string chat_template_override;

    // Tool calling
    std::string tools_json;
    int grammar_mode = 1; // 0=STRICT, 1=LAZY
    bool typed_grammar = true;

    // Engine subsystems
    tool_manager_t     * tool_mgr  = nullptr;
    character_engine_t * char_eng  = nullptr;

    // Control vectors
    std::vector<llama_adapter_lora *> lora_adapters;

    // Generation state
    std::atomic<bool> cancel_flag{false};
    std::mutex gen_mutex;

    // Conversation tokens for state save/load
    std::vector<llama_token> session_tokens;

    // Track context position
    int n_past = 0;

    // Persona logit biases (set via nativeSetLogitBias, preserved across uncensored toggle)
    std::vector<llama_logit_bias> persona_biases;

} g_state;

// ============================================================================
// Helper: GGML type string to enum
// ============================================================================

static ggml_type cache_type_from_string(const std::string & s) {
    if (s == "f32")  return GGML_TYPE_F32;
    if (s == "f16")  return GGML_TYPE_F16;
    if (s == "q8_0") return GGML_TYPE_Q8_0;
    if (s == "q4_0") return GGML_TYPE_Q4_0;
    if (s == "q4_1") return GGML_TYPE_Q4_1;
    if (s == "q5_0") return GGML_TYPE_Q5_0;
    if (s == "q5_1") return GGML_TYPE_Q5_1;
    return GGML_TYPE_Q8_0; // default
}

// ============================================================================
// Helper: Auto-detect thread count for Android
// ============================================================================

static int auto_thread_count() {
    int n = std::thread::hardware_concurrency();
    return std::max(1, (n * 3) / 4);
}

// ============================================================================
// Helper: Rebuild sampler from current params
// ============================================================================

static void rebuild_sampler() {
    if (g_state.sampler) {
        common_sampler_free(g_state.sampler);
        g_state.sampler = nullptr;
    }
    if (g_state.model) {
        g_state.sampler = common_sampler_init(g_state.model, g_state.sampling_params);
    }
}

// ============================================================================
// Helper: Build chat messages from JSON
// ============================================================================

static std::vector<common_chat_msg> parse_messages_json(const std::string & messages_json) {
    std::vector<common_chat_msg> msgs;
    try {
        auto j = json::parse(messages_json);
        if (j.is_array()) {
            for (auto & msg : j) {
                common_chat_msg cm;
                cm.role = msg.value("role", "user");
                cm.content = msg.value("content", "");

                // Remap non-standard roles to "assistant"
                // The Kotlin app sends persona names (e.g. "Luna", "Nova") as the role
                // for assistant messages. Chat templates only understand:
                // "system", "user", "assistant", "tool"
                if (cm.role != "system" && cm.role != "user" &&
                    cm.role != "assistant" && cm.role != "tool") {
                    LOGI("Remapping role '%s' -> 'assistant'", cm.role.c_str());
                    cm.role = "assistant";
                }

                msgs.push_back(cm);
            }
        }
    } catch (const std::exception & e) {
        LOGE("Failed to parse messages JSON: %s", e.what());
    }
    return msgs;
}

// ============================================================================
// Common EOS strings (safety net, like ChatterUI's commonStopStrings)
// These catch model turn boundaries across all template formats
// ============================================================================

static const std::vector<std::string> COMMON_STOP_STRINGS = {
    "</s>",
    "<|end|>",
    "<|eot_id|>",
    "<|end_of_text|>",
    "<|im_end|>",
    "<|EOT|>",
    "<|END_OF_TURN_TOKEN|>",
    "<|end_of_turn|>",
    "<|endoftext|>",
    "<end_of_turn>",
    "<eos>",
};

// ============================================================================
// Helper: Apply chat template to build prompt + stop sequences
// ============================================================================

struct chat_template_result {
    std::string prompt;
    std::vector<std::string> stops;
    common_chat_format format = COMMON_CHAT_FORMAT_CONTENT_ONLY;
    // Grammar constraints for tool calling
    std::string grammar;
    bool grammar_lazy = false;
    std::vector<common_grammar_trigger> grammar_triggers;
    std::vector<std::string> preserved_tokens;
};

static chat_template_result apply_chat_template(const std::vector<common_chat_msg> & messages, bool add_generation_prompt = true) {
    chat_template_result out;

    if (!g_state.chat_templates) {
        // Fallback: simple concatenation
        std::string prompt;
        for (auto & msg : messages) {
            if (msg.role == "system") {
                prompt += msg.content + "\n";
            } else if (msg.role == "user") {
                prompt += "User: " + msg.content + "\n";
            } else if (msg.role == "assistant") {
                prompt += "Assistant: " + msg.content + "\n";
            } else if (msg.role == "tool") {
                prompt += "Tool result: " + msg.content + "\n";
            }
        }
        if (add_generation_prompt) {
            prompt += "Assistant:";
        }
        out.prompt = prompt;
        out.stops = {"\nUser:", "\nuser:", "\n\nUser:"};
        // Add common EOS strings as safety net
        out.stops.insert(out.stops.end(), COMMON_STOP_STRINGS.begin(), COMMON_STOP_STRINGS.end());
        return out;
    }

    common_chat_templates_inputs inputs;
    inputs.messages = messages;
    inputs.add_generation_prompt = add_generation_prompt;
    inputs.use_jinja = true;

    // Add tools if configured
    if (!g_state.tools_json.empty()) {
        try {
            auto tools_j = json::parse(g_state.tools_json);
            inputs.tools = common_chat_tools_parse_oaicompat(tools_j);
            if (g_state.grammar_mode == 0) {
                inputs.tool_choice = COMMON_CHAT_TOOL_CHOICE_REQUIRED;
            } else {
                inputs.tool_choice = COMMON_CHAT_TOOL_CHOICE_AUTO;
            }
        } catch (...) {
            LOGW("Failed to parse tools JSON for template");
        }
    }

    auto result = common_chat_templates_apply(g_state.chat_templates.get(), inputs);
    out.prompt = result.prompt;
    out.format = result.format;
    out.grammar = result.grammar;
    out.grammar_lazy = result.grammar_lazy;
    out.grammar_triggers = result.grammar_triggers;
    out.preserved_tokens = result.preserved_tokens;

    // If the template returned CONTENT_ONLY even though tools are configured,
    // the model's template doesn't natively support tools.
    // Inject our ToolManager's tool description as a fallback via prompt engineering.
    if (out.format == COMMON_CHAT_FORMAT_CONTENT_ONLY &&
        !g_state.tools_json.empty() && g_state.tool_mgr) {
        char * tool_prompt = tool_manager_get_prompt(g_state.tool_mgr);
        if (tool_prompt && tool_prompt[0]) {
            // Rebuild prompt with tool descriptions injected into the system message
            std::vector<common_chat_msg> augmented = messages;
            bool found_system = false;
            for (auto & m : augmented) {
                if (m.role == "system") {
                    m.content += "\n\n" + std::string(tool_prompt);
                    found_system = true;
                    break;
                }
            }
            if (!found_system) {
                augmented.insert(augmented.begin(), {"system", std::string(tool_prompt)});
            }

            // Re-apply template with augmented messages (no tools this time, we handle it via prompt)
            common_chat_templates_inputs aug_inputs;
            aug_inputs.messages = augmented;
            aug_inputs.add_generation_prompt = add_generation_prompt;
            aug_inputs.use_jinja = true;
            auto aug_result = common_chat_templates_apply(g_state.chat_templates.get(), aug_inputs);
            out.prompt = aug_result.prompt;
            LOGI("ToolManager prompt injected (model template doesn't support tools natively)");
        }
        tool_manager_free_string(tool_prompt);
    }

    // Collect stop sequences from template
    out.stops = result.additional_stops;

    // Always add common EOS strings as safety net (like ChatterUI)
    out.stops.insert(out.stops.end(), COMMON_STOP_STRINGS.begin(), COMMON_STOP_STRINGS.end());

    LOGI("Template applied: format=%d, %zu stop sequences", (int)out.format, out.stops.size());
    return out;
}

// ============================================================================
// Antiprompt detector (two-phase: full match + partial match buffering)
// Modeled after ChatterUI / llama.rn's findStoppingStrings approach
// ============================================================================

enum stop_type { STOP_FULL, STOP_PARTIAL };

struct antiprompt_state {
    std::vector<std::string> stops;
    std::string stopping_word;
    bool stopped = false;

    void set_stops(const std::vector<std::string> & s) {
        stops = s;
        stopping_word.clear();
        stopped = false;
    }

    // Check for a full stop string in the tail of the text.
    // Only searches within the region that could contain the stop string
    // (last token_size + max_stop_len chars).
    size_t find_stop(const std::string & text, size_t last_token_size, stop_type type) {
        size_t stop_pos = std::string::npos;

        for (auto & word : stops) {
            if (word.empty()) continue;
            size_t pos;

            if (type == STOP_FULL) {
                // Search only in the tail region that could contain the stop string
                size_t window = word.size() + last_token_size;
                size_t from = text.size() > window ? text.size() - window : 0;
                pos = text.find(word, from);
            } else {
                // Check if the end of text is a prefix of this stop string
                pos = find_partial(word, text);
            }

            if (pos != std::string::npos && (stop_pos == std::string::npos || pos < stop_pos)) {
                if (type == STOP_FULL) {
                    stopping_word = word;
                    stopped = true;
                }
                stop_pos = pos;
            }
        }
        return stop_pos;
    }

private:
    // Check if the end of text is a partial match (prefix) of a stop string
    size_t find_partial(const std::string & word, const std::string & text) {
        if (text.empty() || word.empty()) return std::string::npos;

        // Check if text ends with any prefix of word (length 1..word.size()-1)
        size_t max_check = std::min(word.size() - 1, text.size());
        for (size_t len = max_check; len >= 1; len--) {
            if (text.compare(text.size() - len, len, word, 0, len) == 0) {
                return text.size() - len;
            }
        }
        return std::string::npos;
    }
};

// ============================================================================
// Helper: Safe NewStringUTF that handles incomplete UTF-8 sequences
// JNI's NewStringUTF crashes on incomplete multi-byte sequences (e.g. partial emoji).
// This buffers incomplete trailing bytes and only sends complete UTF-8 to JNI.
// ============================================================================

static std::string g_utf8_buffer; // persistent buffer for incomplete UTF-8 bytes

// Returns the number of valid UTF-8 bytes from the start of the string.
// Trailing incomplete multi-byte sequences are excluded.
static size_t utf8_valid_prefix_len(const std::string & s) {
    size_t len = s.size();
    if (len == 0) return 0;

    // Check last 1-3 bytes for incomplete multi-byte sequence
    for (size_t i = 1; i <= std::min((size_t)3, len); i++) {
        unsigned char c = (unsigned char)s[len - i];
        if ((c & 0x80) == 0) {
            // ASCII byte - everything up to here is valid
            return len;
        }
        if ((c & 0xC0) == 0xC0) {
            // Start of multi-byte sequence
            int expected;
            if      ((c & 0xE0) == 0xC0) expected = 2;
            else if ((c & 0xF0) == 0xE0) expected = 3;
            else if ((c & 0xF8) == 0xF0) expected = 4;
            else return len - i; // invalid start byte, skip it

            if (i < (size_t)expected) {
                // Incomplete sequence - exclude it
                return len - i;
            }
            // Complete sequence
            return len;
        }
        // Continuation byte (0x10xxxxxx) - keep scanning backwards
    }
    // All trailing bytes are continuation bytes with no start byte visible
    return len > 3 ? len - 3 : 0;
}

// Send text to JNI callback, buffering incomplete UTF-8.
// Returns true if any text was sent.
static bool send_token_safe(JNIEnv * env, jobject callback, jmethodID onToken, const std::string & text) {
    std::string combined = g_utf8_buffer + text;
    g_utf8_buffer.clear();

    // Strip embedded null bytes — Modified UTF-8 (used by JNI) cannot represent 0x00
    // as a single byte; it requires the two-byte overlong form (0xC0 0x80).
    combined.erase(std::remove(combined.begin(), combined.end(), '\0'), combined.end());

    size_t valid_len = utf8_valid_prefix_len(combined);
    if (valid_len < combined.size()) {
        g_utf8_buffer = combined.substr(valid_len);
        combined = combined.substr(0, valid_len);
    }

    if (combined.empty()) return false;

    jstring jtoken = env->NewStringUTF(combined.c_str());
    if (!jtoken) {
        // Last resort: if NewStringUTF still fails, clear the buffer and skip
        env->ExceptionClear();
        g_utf8_buffer.clear();
        return false;
    }
    env->CallVoidMethod(callback, onToken, jtoken);
    env->DeleteLocalRef(jtoken);
    return true;
}

// ============================================================================
// Helper: Tokenize a string
// ============================================================================

static std::vector<llama_token> tokenize_string(const std::string & text, bool add_special = true) {
    if (!g_state.model) return {};
    const llama_vocab * vocab = llama_model_get_vocab(g_state.model);
    int n_tokens = text.size() + 256;
    std::vector<llama_token> tokens(n_tokens);
    int n = llama_tokenize(vocab, text.c_str(), text.size(),
                           tokens.data(), tokens.size(), add_special, true);
    if (n < 0) {
        tokens.resize(-n);
        n = llama_tokenize(vocab, text.c_str(), text.size(),
                           tokens.data(), tokens.size(), add_special, true);
    }
    tokens.resize(std::max(0, n));
    return tokens;
}

// ============================================================================
// Helper: Decode batch of tokens
// ============================================================================

static bool eval_tokens(const std::vector<llama_token> & tokens, int & n_past) {
    if (tokens.empty()) return true;

    const int n_batch = llama_n_batch(g_state.ctx);

    for (size_t i = 0; i < tokens.size(); i += n_batch) {
        int n_eval = std::min((int)(tokens.size() - i), n_batch);

        llama_batch batch = llama_batch_init(n_eval, 0, 1);
        for (int j = 0; j < n_eval; j++) {
            common_batch_add(batch, tokens[i + j], n_past + j, {0}, false);
        }
        batch.logits[batch.n_tokens - 1] = true;

        if (llama_decode(g_state.ctx, batch) != 0) {
            llama_batch_free(batch);
            LOGE("Failed to decode batch at position %d", n_past);
            return false;
        }

        n_past += n_eval;
        llama_batch_free(batch);
    }

    return true;
}

// ============================================================================
// JNI: nativeLoadModel
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeLoadModel(
        JNIEnv * env, jobject,
        jstring jpath, jint nCtx, jint nThreads,
        jboolean flashAttn, jstring jCacheTypeK, jstring jCacheTypeV) {

    std::lock_guard<std::mutex> lock(g_state.gen_mutex);

    // Clean up any existing model
    if (g_state.sampler) { common_sampler_free(g_state.sampler); g_state.sampler = nullptr; }
    if (g_state.ctx) { llama_free(g_state.ctx); g_state.ctx = nullptr; }
    if (g_state.model) { llama_model_free(g_state.model); g_state.model = nullptr; }
    g_state.chat_templates.reset();
    g_state.n_past = 0;
    g_state.session_tokens.clear();

    const char * path = env->GetStringUTFChars(jpath, nullptr);
    const char * cacheK = env->GetStringUTFChars(jCacheTypeK, nullptr);
    const char * cacheV = env->GetStringUTFChars(jCacheTypeV, nullptr);

    LOGI("Loading model: %s (ctx=%d threads=%d flash=%d)", path, nCtx, nThreads, flashAttn);

    // Model params
    auto mparams = llama_model_default_params();
    mparams.use_mmap = true;

    g_state.model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (!g_state.model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(jCacheTypeK, cacheK);
        env->ReleaseStringUTFChars(jCacheTypeV, cacheV);
        return JNI_FALSE;
    }

    // Context params
    auto cparams = llama_context_default_params();
    cparams.n_ctx = nCtx > 0 ? nCtx : 4096;
    cparams.n_threads = nThreads > 0 ? nThreads : auto_thread_count();
    cparams.n_threads_batch = cparams.n_threads;
    cparams.n_batch = 512;

    if (flashAttn) {
        cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    }

    cparams.type_k = cache_type_from_string(cacheK);
    cparams.type_v = cache_type_from_string(cacheV);

    env->ReleaseStringUTFChars(jCacheTypeK, cacheK);
    env->ReleaseStringUTFChars(jCacheTypeV, cacheV);

    g_state.ctx = llama_init_from_model(g_state.model, cparams);
    if (!g_state.ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_state.model);
        g_state.model = nullptr;
        return JNI_FALSE;
    }

    // Initialize chat templates from model
    g_state.chat_templates = common_chat_templates_init(
        g_state.model,
        g_state.chat_template_override);

    // Initialize default sampler
    rebuild_sampler();

    LOGI("Model loaded successfully (ctx=%d threads=%d)",
         (int)llama_n_ctx(g_state.ctx), cparams.n_threads);

    return JNI_TRUE;
}

// ============================================================================
// JNI: nativeLoadModelFromFd
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeLoadModelFromFd(
        JNIEnv * env, jobject thiz,
        jint fd, jint nCtx, jint nThreads,
        jboolean flashAttn, jstring jCacheTypeK, jstring jCacheTypeV) {

    // Convert FD to path via /proc/self/fd/<fd>
    char path[64];
    snprintf(path, sizeof(path), "/proc/self/fd/%d", fd);

    jstring jpath = env->NewStringUTF(path);
    jboolean result = Java_com_mp_ai_1gguf_GGUFNativeLib_nativeLoadModel(
        env, thiz, jpath, nCtx, nThreads, flashAttn, jCacheTypeK, jCacheTypeV);
    env->DeleteLocalRef(jpath);
    return result;
}

// ============================================================================
// JNI: nativeSetSampling
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetSampling(
        JNIEnv *, jobject,
        jfloat temperature, jint topK, jfloat topP, jfloat minP,
        jint mirostat, jfloat mirostatTau, jfloat mirostatEta, jint seed) {

    std::lock_guard<std::mutex> lock(g_state.gen_mutex);
    g_state.sampling_params.temp = temperature;
    g_state.sampling_params.top_k = topK;
    g_state.sampling_params.top_p = topP;
    g_state.sampling_params.min_p = minP;
    g_state.sampling_params.mirostat = mirostat;
    g_state.sampling_params.mirostat_tau = mirostatTau;
    g_state.sampling_params.mirostat_eta = mirostatEta;
    g_state.sampling_params.seed = (seed < 0) ? LLAMA_DEFAULT_SEED : (uint32_t)seed;

    rebuild_sampler();

    LOGI("Sampling set: temp=%.2f top_k=%d top_p=%.2f min_p=%.2f mirostat=%d seed=%d",
         temperature, topK, topP, minP, mirostat, seed);
}

// ============================================================================
// JNI: nativeSetSystemPrompt
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetSystemPrompt(
        JNIEnv * env, jobject, jstring jprompt) {
    const char * prompt = env->GetStringUTFChars(jprompt, nullptr);
    g_state.system_prompt = prompt;
    env->ReleaseStringUTFChars(jprompt, prompt);
    LOGI("System prompt set (%zu chars)", g_state.system_prompt.size());
}

// ============================================================================
// JNI: nativeSetChatTemplate
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetChatTemplate(
        JNIEnv * env, jobject, jstring jtemplate) {
    const char * tmpl = env->GetStringUTFChars(jtemplate, nullptr);
    g_state.chat_template_override = tmpl;
    env->ReleaseStringUTFChars(jtemplate, tmpl);

    // Reinitialize chat templates with override
    if (g_state.model) {
        g_state.chat_templates = common_chat_templates_init(
            g_state.model, g_state.chat_template_override);
    }

    LOGI("Chat template override set (%zu chars)", g_state.chat_template_override.size());
}

// ============================================================================
// JNI: nativeGenerateStream (single-turn)
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGenerateStream(
        JNIEnv * env, jobject, jstring jprompt, jint maxTokens, jobject callback) {

    std::lock_guard<std::mutex> lock(g_state.gen_mutex);

    if (!g_state.model || !g_state.ctx) {
        LOGE("Model not loaded");
        return JNI_FALSE;
    }

    g_state.cancel_flag = false;
    g_utf8_buffer.clear();

    const char * prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    std::string user_prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);

    // Get callback methods
    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onToolCall = env->GetMethodID(cbClass, "onToolCall", "(Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID onDone = env->GetMethodID(cbClass, "onDone", "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");
    jmethodID onMetrics = env->GetMethodID(cbClass, "onMetrics",
        "(FFFIIFFFF)V");

    if (!onToken || !onDone || !onError) {
        LOGE("Failed to find callback methods");
        return JNI_FALSE;
    }

    // Build prompt using chat template
    std::vector<common_chat_msg> messages;
    if (!g_state.system_prompt.empty()) {
        messages.push_back({"system", g_state.system_prompt});
    }
    messages.push_back({"user", user_prompt});

    auto tmpl_result = apply_chat_template(messages, true);
    auto tokens = tokenize_string(tmpl_result.prompt, true);

    if (tokens.empty()) {
        jstring jerr = env->NewStringUTF("Failed to tokenize prompt");
        env->CallVoidMethod(callback, onError, jerr);
        env->DeleteLocalRef(jerr);
        return JNI_FALSE;
    }

    // Set up antiprompt detector with template stops
    antiprompt_state antiprompt;
    antiprompt.set_stops(tmpl_result.stops);

    // Clear context for single-turn
    llama_memory_clear(llama_get_memory(g_state.ctx), true);
    g_state.n_past = 0;

    // Apply grammar constraints for tool calling if available
    bool grammar_applied = false;
    common_params_sampling saved_params;
    if (!tmpl_result.grammar.empty() && !g_state.tools_json.empty()) {
        saved_params = g_state.sampling_params; // save for restore after generation
        g_state.sampling_params.grammar = tmpl_result.grammar;
        g_state.sampling_params.grammar_lazy = tmpl_result.grammar_lazy;
        g_state.sampling_params.grammar_triggers = tmpl_result.grammar_triggers;
        // Resolve preserved_tokens strings to token IDs
        const llama_vocab * v = llama_model_get_vocab(g_state.model);
        for (auto & tok_str : tmpl_result.preserved_tokens) {
            auto ids = tokenize_string(tok_str, false);
            for (auto id : ids) {
                g_state.sampling_params.preserved_tokens.insert(id);
            }
        }
        grammar_applied = true;
        LOGI("Grammar constraints applied for tool calling (lazy=%d, %zu triggers)",
             tmpl_result.grammar_lazy, tmpl_result.grammar_triggers.size());
    }

    // Reset sampler (with or without grammar)
    rebuild_sampler();

    // Track timing
    auto t_start = std::chrono::high_resolution_clock::now();

    // Evaluate prompt
    int prompt_tokens = tokens.size();
    if (!eval_tokens(tokens, g_state.n_past)) {
        if (grammar_applied) {
            g_state.sampling_params = saved_params;
            rebuild_sampler();
        }
        jstring jerr = env->NewStringUTF("Failed to evaluate prompt");
        env->CallVoidMethod(callback, onError, jerr);
        env->DeleteLocalRef(jerr);
        return JNI_FALSE;
    }

    auto t_prompt_done = std::chrono::high_resolution_clock::now();

    // Generate tokens with two-phase antiprompt detection
    const llama_vocab * vocab = llama_model_get_vocab(g_state.model);
    int n_generated = 0;
    std::string generated_text;
    size_t sent_count = 0; // how much of generated_text has been sent to callback

    while (n_generated < maxTokens && !g_state.cancel_flag.load()) {
        if (!g_state.sampler) break;

        llama_token id = common_sampler_sample(g_state.sampler, g_state.ctx, -1);
        common_sampler_accept(g_state.sampler, id, true);

        // Check for EOS/EOG token
        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }

        // Detokenize
        char buf[256];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf) - 1, 0, true);
        if (n > 0) {
            buf[n] = '\0';
            std::string piece(buf);
            generated_text += piece;

            // Two-phase antiprompt detection (like ChatterUI / llama.rn)
            size_t pos = std::min(sent_count, generated_text.size());
            std::string unsent = generated_text.substr(pos);

            // Phase 1: Check for FULL stop string match
            size_t stop_pos = antiprompt.find_stop(unsent, piece.size(), STOP_FULL);
            if (stop_pos != std::string::npos) {
                generated_text.erase(generated_text.begin() + pos + stop_pos, generated_text.end());
                std::string remaining = generated_text.substr(sent_count);
                if (!remaining.empty()) {
                    send_token_safe(env, callback, onToken, remaining);
                }
                LOGI("Antiprompt hit: '%s'", antiprompt.stopping_word.c_str());
                break;
            }

            // Phase 2: Check for PARTIAL stop string match - buffer if partial
            stop_pos = antiprompt.find_stop(unsent, piece.size(), STOP_PARTIAL);
            if (stop_pos == std::string::npos) {
                std::string to_send = generated_text.substr(sent_count);
                if (!to_send.empty()) {
                    send_token_safe(env, callback, onToken, to_send);
                    sent_count = generated_text.size();
                }
            }

            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                break;
            }
        }

        std::vector<llama_token> single = {id};
        if (!eval_tokens(single, g_state.n_past)) {
            break;
        }

        n_generated++;
    }

    // Flush any remaining buffered text (including UTF-8 buffer)
    if (sent_count < generated_text.size()) {
        std::string remaining = generated_text.substr(sent_count);
        if (!remaining.empty()) {
            send_token_safe(env, callback, onToken, remaining);
        }
    }
    // Flush UTF-8 buffer
    if (!g_utf8_buffer.empty()) {
        send_token_safe(env, callback, onToken, "");
        g_utf8_buffer.clear();
    }

    auto t_end = std::chrono::high_resolution_clock::now();

    // Restore sampling params if grammar was applied
    if (grammar_applied) {
        g_state.sampling_params = saved_params;
        rebuild_sampler();
    }

    // Check for tool calls in output (two strategies: template parser + our ToolManager)
    // The Kotlin side expects argsJson as: {"name":"tool_name","arguments":{...}}
    if (onToolCall && !g_state.tools_json.empty()) {
        bool found_tool_call = false;

        // Strategy 1: llama.cpp template-aware parser (works with models that follow their template)
        if (g_state.chat_templates) {
            try {
                common_chat_parser_params parser_params;
                parser_params.format = tmpl_result.format;

                auto parsed = common_chat_parse(generated_text, false, parser_params);
                for (auto & tc : parsed.tool_calls) {
                    // Wrap in the format Kotlin expects
                    json wrapped;
                    wrapped["name"] = tc.name;
                    try {
                        wrapped["arguments"] = json::parse(tc.arguments);
                    } catch (...) {
                        wrapped["arguments"] = tc.arguments;
                    }
                    std::string wrapped_str = wrapped.dump();

                    jstring jname = env->NewStringUTF(tc.name.c_str());
                    jstring jargs = env->NewStringUTF(wrapped_str.c_str());
                    env->CallVoidMethod(callback, onToolCall, jname, jargs);
                    env->DeleteLocalRef(jname);
                    env->DeleteLocalRef(jargs);
                    found_tool_call = true;
                    LOGI("Template parsed tool call: %s args=%s", tc.name.c_str(), wrapped_str.c_str());
                }
            } catch (const std::exception & e) {
                LOGW("Template tool call parsing failed: %s", e.what());
            }
        }

        // Strategy 2: Our ToolManager (multi-format: JSON + XML + function-call style)
        if (!found_tool_call && g_state.tool_mgr) {
            auto result = tool_manager_parse_output(g_state.tool_mgr, generated_text.c_str());
            if (result.is_valid) {
                json wrapped;
                wrapped["name"] = result.tool_name;
                try {
                    wrapped["arguments"] = json::parse(result.arguments_json);
                } catch (...) {
                    wrapped["arguments"] = result.arguments_json;
                }
                std::string wrapped_str = wrapped.dump();

                jstring jname = env->NewStringUTF(result.tool_name);
                jstring jargs = env->NewStringUTF(wrapped_str.c_str());
                env->CallVoidMethod(callback, onToolCall, jname, jargs);
                env->DeleteLocalRef(jname);
                env->DeleteLocalRef(jargs);
                tool_manager_free_string((char *)result.tool_name);
                tool_manager_free_string((char *)result.arguments_json);
                found_tool_call = true;
                LOGI("ToolManager fallback parsed tool call: %s", wrapped_str.c_str());
            }
        }
    }

    // Calculate metrics
    float prompt_ms = std::chrono::duration<float, std::milli>(t_prompt_done - t_start).count();
    float gen_ms = std::chrono::duration<float, std::milli>(t_end - t_prompt_done).count();
    float total_ms = std::chrono::duration<float, std::milli>(t_end - t_start).count();
    float tps = gen_ms > 0 ? (n_generated / (gen_ms / 1000.0f)) : 0;
    float ttft_ms = prompt_ms; // time to first token ~= prompt eval time

    // Estimate memory usage
    float model_mb = 0, ctx_mb = 0, peak_mb = 0, mem_pct = 0;

    // Send metrics
    if (onMetrics) {
        env->CallVoidMethod(callback, onMetrics,
            tps, ttft_ms, total_ms,
            prompt_tokens, n_generated,
            model_mb, ctx_mb, peak_mb, mem_pct);
    }

    // Send done
    env->CallVoidMethod(callback, onDone);

    LOGI("Generation complete: %d tokens, %.1f t/s, %.1f ms total",
         n_generated, tps, total_ms);

    return JNI_TRUE;
}

// ============================================================================
// JNI: nativeGenerateStreamMultiTurn
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGenerateStreamMultiTurn(
        JNIEnv * env, jobject, jstring jmessagesJson, jint maxTokens, jobject callback) {

    std::lock_guard<std::mutex> lock(g_state.gen_mutex);

    if (!g_state.model || !g_state.ctx) {
        LOGE("Model not loaded");
        return JNI_FALSE;
    }

    g_state.cancel_flag = false;
    g_utf8_buffer.clear();

    const char * msgs_cstr = env->GetStringUTFChars(jmessagesJson, nullptr);
    std::string messages_json(msgs_cstr);
    env->ReleaseStringUTFChars(jmessagesJson, msgs_cstr);

    // Get callback methods
    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onToolCall = env->GetMethodID(cbClass, "onToolCall", "(Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID onDone = env->GetMethodID(cbClass, "onDone", "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");
    jmethodID onMetrics = env->GetMethodID(cbClass, "onMetrics",
        "(FFFIIFFFF)V");

    // Parse messages
    auto messages = parse_messages_json(messages_json);

    // Prepend system prompt if not already present
    if (!g_state.system_prompt.empty()) {
        if (messages.empty() || messages[0].role != "system") {
            messages.insert(messages.begin(), {"system", g_state.system_prompt});
        }
    }

    // Apply chat template
    auto tmpl_result = apply_chat_template(messages, true);
    auto tokens = tokenize_string(tmpl_result.prompt, true);

    if (tokens.empty()) {
        jstring jerr = env->NewStringUTF("Failed to tokenize prompt");
        env->CallVoidMethod(callback, onError, jerr);
        env->DeleteLocalRef(jerr);
        return JNI_FALSE;
    }

    // Set up antiprompt detector with template stops
    antiprompt_state antiprompt;
    antiprompt.set_stops(tmpl_result.stops);

    LOGI("Multi-turn prompt length: %d tokens, %zu stop seqs", (int)tokens.size(), tmpl_result.stops.size());

    // Clear context for full re-evaluation
    llama_memory_clear(llama_get_memory(g_state.ctx), true);
    g_state.n_past = 0;

    // Apply grammar constraints for tool calling if available
    bool grammar_applied = false;
    common_params_sampling saved_params;
    if (!tmpl_result.grammar.empty() && !g_state.tools_json.empty()) {
        saved_params = g_state.sampling_params;
        g_state.sampling_params.grammar = tmpl_result.grammar;
        g_state.sampling_params.grammar_lazy = tmpl_result.grammar_lazy;
        g_state.sampling_params.grammar_triggers = tmpl_result.grammar_triggers;
        const llama_vocab * v = llama_model_get_vocab(g_state.model);
        for (auto & tok_str : tmpl_result.preserved_tokens) {
            auto ids = tokenize_string(tok_str, false);
            for (auto id : ids) {
                g_state.sampling_params.preserved_tokens.insert(id);
            }
        }
        grammar_applied = true;
        LOGI("Grammar constraints applied for tool calling (lazy=%d)", tmpl_result.grammar_lazy);
    }

    rebuild_sampler();

    auto t_start = std::chrono::high_resolution_clock::now();

    int prompt_tokens = tokens.size();
    if (!eval_tokens(tokens, g_state.n_past)) {
        if (grammar_applied) {
            g_state.sampling_params = saved_params;
            rebuild_sampler();
        }
        jstring jerr = env->NewStringUTF("Failed to evaluate prompt");
        env->CallVoidMethod(callback, onError, jerr);
        env->DeleteLocalRef(jerr);
        return JNI_FALSE;
    }

    auto t_prompt_done = std::chrono::high_resolution_clock::now();

    const llama_vocab * vocab = llama_model_get_vocab(g_state.model);
    int n_generated = 0;
    std::string generated_text;
    size_t sent_count = 0;

    while (n_generated < maxTokens && !g_state.cancel_flag.load()) {
        if (!g_state.sampler) break;

        llama_token id = common_sampler_sample(g_state.sampler, g_state.ctx, -1);
        common_sampler_accept(g_state.sampler, id, true);

        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf) - 1, 0, true);
        if (n > 0) {
            buf[n] = '\0';
            std::string piece(buf);
            generated_text += piece;

            // Two-phase antiprompt detection
            size_t pos = std::min(sent_count, generated_text.size());
            std::string unsent = generated_text.substr(pos);

            // Phase 1: Check for FULL stop string match
            size_t stop_pos = antiprompt.find_stop(unsent, piece.size(), STOP_FULL);
            if (stop_pos != std::string::npos) {
                generated_text.erase(generated_text.begin() + pos + stop_pos, generated_text.end());
                std::string remaining = generated_text.substr(sent_count);
                if (!remaining.empty()) {
                    send_token_safe(env, callback, onToken, remaining);
                }
                LOGI("Antiprompt hit: '%s'", antiprompt.stopping_word.c_str());
                break;
            }

            // Phase 2: Check for PARTIAL stop string match - buffer if partial
            stop_pos = antiprompt.find_stop(unsent, piece.size(), STOP_PARTIAL);
            if (stop_pos == std::string::npos) {
                std::string to_send = generated_text.substr(sent_count);
                if (!to_send.empty()) {
                    send_token_safe(env, callback, onToken, to_send);
                    sent_count = generated_text.size();
                }
            }

            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                break;
            }
        }

        std::vector<llama_token> single = {id};
        if (!eval_tokens(single, g_state.n_past)) {
            break;
        }

        n_generated++;
    }

    // Flush remaining buffered text + UTF-8 buffer
    if (sent_count < generated_text.size()) {
        std::string remaining = generated_text.substr(sent_count);
        if (!remaining.empty()) {
            send_token_safe(env, callback, onToken, remaining);
        }
    }
    if (!g_utf8_buffer.empty()) {
        send_token_safe(env, callback, onToken, "");
        g_utf8_buffer.clear();
    }

    auto t_end = std::chrono::high_resolution_clock::now();

    // Restore sampling params if grammar was applied
    if (grammar_applied) {
        g_state.sampling_params = saved_params;
        rebuild_sampler();
    }

    // Check for tool calls (two strategies: template parser + our ToolManager)
    // Wrap args in {"name":"...","arguments":{...}} format for Kotlin
    if (onToolCall && !g_state.tools_json.empty()) {
        bool found_tool_call = false;

        // Strategy 1: llama.cpp template-aware parser
        if (g_state.chat_templates) {
            try {
                common_chat_parser_params parser_params;
                parser_params.format = tmpl_result.format;

                auto parsed = common_chat_parse(generated_text, false, parser_params);
                for (auto & tc : parsed.tool_calls) {
                    json wrapped;
                    wrapped["name"] = tc.name;
                    try {
                        wrapped["arguments"] = json::parse(tc.arguments);
                    } catch (...) {
                        wrapped["arguments"] = tc.arguments;
                    }
                    std::string wrapped_str = wrapped.dump();

                    jstring jname = env->NewStringUTF(tc.name.c_str());
                    jstring jargs = env->NewStringUTF(wrapped_str.c_str());
                    env->CallVoidMethod(callback, onToolCall, jname, jargs);
                    env->DeleteLocalRef(jname);
                    env->DeleteLocalRef(jargs);
                    found_tool_call = true;
                    LOGI("Template parsed tool call: %s args=%s", tc.name.c_str(), wrapped_str.c_str());
                }
            } catch (const std::exception & e) {
                LOGW("Template tool call parsing failed: %s", e.what());
            }
        }

        // Strategy 2: Our ToolManager fallback (JSON + XML + function-call)
        if (!found_tool_call && g_state.tool_mgr) {
            auto result = tool_manager_parse_output(g_state.tool_mgr, generated_text.c_str());
            if (result.is_valid) {
                json wrapped;
                wrapped["name"] = result.tool_name;
                try {
                    wrapped["arguments"] = json::parse(result.arguments_json);
                } catch (...) {
                    wrapped["arguments"] = result.arguments_json;
                }
                std::string wrapped_str = wrapped.dump();

                jstring jname = env->NewStringUTF(result.tool_name);
                jstring jargs = env->NewStringUTF(wrapped_str.c_str());
                env->CallVoidMethod(callback, onToolCall, jname, jargs);
                env->DeleteLocalRef(jname);
                env->DeleteLocalRef(jargs);
                tool_manager_free_string((char *)result.tool_name);
                tool_manager_free_string((char *)result.arguments_json);
                LOGI("ToolManager fallback parsed tool call: %s", wrapped_str.c_str());
            }
        }
    }

    // Metrics
    float prompt_ms = std::chrono::duration<float, std::milli>(t_prompt_done - t_start).count();
    float gen_ms = std::chrono::duration<float, std::milli>(t_end - t_prompt_done).count();
    float total_ms = std::chrono::duration<float, std::milli>(t_end - t_start).count();
    float tps = gen_ms > 0 ? (n_generated / (gen_ms / 1000.0f)) : 0;
    float ttft_ms = prompt_ms;

    if (onMetrics) {
        env->CallVoidMethod(callback, onMetrics,
            tps, ttft_ms, total_ms,
            prompt_tokens, n_generated,
            0.0f, 0.0f, 0.0f, 0.0f);
    }

    env->CallVoidMethod(callback, onDone);

    LOGI("Multi-turn generation complete: %d tokens, %.1f t/s", n_generated, tps);

    return JNI_TRUE;
}

// ============================================================================
// JNI: nativeStopGeneration
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeStopGeneration(JNIEnv *, jobject) {
    g_state.cancel_flag = true;
    LOGI("Generation stop requested");
}

// ============================================================================
// JNI: nativeRelease
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeRelease(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_state.gen_mutex);

    if (g_state.sampler) {
        common_sampler_free(g_state.sampler);
        g_state.sampler = nullptr;
    }
    if (g_state.ctx) {
        llama_free(g_state.ctx);
        g_state.ctx = nullptr;
    }
    if (g_state.model) {
        llama_model_free(g_state.model);
        g_state.model = nullptr;
    }
    g_state.chat_templates.reset();
    g_state.n_past = 0;
    g_state.session_tokens.clear();
    g_state.system_prompt.clear();
    g_state.chat_template_override.clear();
    g_state.tools_json.clear();

    // Clean up engine subsystems
    if (g_state.tool_mgr) {
        tool_manager_free(g_state.tool_mgr);
        g_state.tool_mgr = nullptr;
    }
    if (g_state.char_eng) {
        character_engine_free(g_state.char_eng);
        g_state.char_eng = nullptr;
    }

    LOGI("Model released");
}

// ============================================================================
// JNI: nativeGetModelInfo
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGetModelInfo(JNIEnv * env, jobject) {
    if (!g_state.model) return nullptr;

    try {
        json info;

        // Get model description
        char desc[256] = {0};
        llama_model_desc(g_state.model, desc, sizeof(desc));
        info["description"] = desc;

        // Context size
        if (g_state.ctx) {
            info["n_ctx"] = (int)llama_n_ctx(g_state.ctx);
        }

        // Model size
        info["n_params"] = (int64_t)llama_model_n_params(g_state.model);
        info["model_size"] = (int64_t)llama_model_size(g_state.model);

        // Get metadata via llama_model_meta_val_str
        char buf[256];
        if (llama_model_meta_val_str(g_state.model, "general.name", buf, sizeof(buf)) > 0) {
            info["name"] = buf;
        }
        if (llama_model_meta_val_str(g_state.model, "general.architecture", buf, sizeof(buf)) > 0) {
            info["architecture"] = buf;
        }
        if (llama_model_meta_val_str(g_state.model, "general.file_type", buf, sizeof(buf)) > 0) {
            info["file_type"] = buf;
        }

        // Vocab info
        const llama_vocab * vocab = llama_model_get_vocab(g_state.model);
        if (vocab) {
            info["n_vocab"] = llama_vocab_n_tokens(vocab);
        }

        std::string json_str = info.dump();
        return env->NewStringUTF(json_str.c_str());
    } catch (const std::exception & e) {
        LOGE("Failed to get model info: %s", e.what());
        return nullptr;
    }
}

// ============================================================================
// JNI: nativeIsToolCallingSupported
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeIsToolCallingSupported(JNIEnv *, jobject) {
    if (!g_state.model) return JNI_FALSE;

    // Check if model has a chat template (indicates tool calling support)
    if (g_state.chat_templates) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

// ============================================================================
// JNI: nativeSetToolsJson
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetToolsJson(
        JNIEnv * env, jobject, jstring jtoolsJson) {
    const char * json_cstr = env->GetStringUTFChars(jtoolsJson, nullptr);
    g_state.tools_json = json_cstr;
    env->ReleaseStringUTFChars(jtoolsJson, json_cstr);

    // Also register tools with our ToolManager for fallback multi-format parsing
    if (g_state.tools_json.empty()) {
        LOGI("Tools JSON set (empty)");
        return;
    }

    if (!g_state.tool_mgr) {
        g_state.tool_mgr = tool_manager_create();
    }
    tool_manager_clear(g_state.tool_mgr);

    try {
        auto tools_j = json::parse(g_state.tools_json);
        if (tools_j.is_array()) {
            for (auto & t : tools_j) {
                std::string name = t.value("name", "");
                std::string desc;

                // Handle OpenAI-style {"type":"function","function":{...}} format
                if (t.contains("function") && t["function"].is_object()) {
                    auto & func = t["function"];
                    name = func.value("name", name);
                    desc = func.value("description", "");
                } else {
                    desc = t.value("description", "");
                }

                if (name.empty()) continue;

                // Build param defs from JSON schema
                std::vector<tool_param_def> params;
                std::vector<std::string> param_names; // keep strings alive
                std::vector<std::string> param_descs;

                json props;
                std::vector<std::string> required_params;

                if (t.contains("function") && t["function"].contains("parameters")) {
                    auto & schema = t["function"]["parameters"];
                    if (schema.contains("properties")) props = schema["properties"];
                    if (schema.contains("required") && schema["required"].is_array()) {
                        for (auto & r : schema["required"]) required_params.push_back(r.get<std::string>());
                    }
                } else if (t.contains("parameters")) {
                    auto & schema = t["parameters"];
                    if (schema.contains("properties")) props = schema["properties"];
                    if (schema.contains("required") && schema["required"].is_array()) {
                        for (auto & r : schema["required"]) required_params.push_back(r.get<std::string>());
                    }
                }

                for (auto & [pname, pval] : props.items()) {
                    param_names.push_back(pname);
                    param_descs.push_back(pval.value("description", ""));

                    tool_param_type ptype = TOOL_PARAM_STRING;
                    std::string type_str = pval.value("type", "string");
                    if (type_str == "number" || type_str == "integer") ptype = TOOL_PARAM_NUMBER;
                    else if (type_str == "boolean") ptype = TOOL_PARAM_BOOLEAN;
                    else if (type_str == "array") ptype = TOOL_PARAM_ARRAY;
                    else if (type_str == "object") ptype = TOOL_PARAM_OBJECT;

                    bool is_required = false;
                    for (auto & r : required_params) {
                        if (r == pname) { is_required = true; break; }
                    }

                    params.push_back({
                        param_names.back().c_str(),
                        param_descs.back().c_str(),
                        ptype,
                        is_required
                    });
                }

                tool_def td;
                td.name = name.c_str();
                td.description = desc.c_str();
                td.params = params.empty() ? nullptr : params.data();
                td.n_params = (int32_t)params.size();
                tool_manager_register(g_state.tool_mgr, &td);
            }
        }
    } catch (const std::exception & e) {
        LOGW("Failed to register tools with ToolManager: %s", e.what());
    }

    LOGI("Tools JSON set (%zu chars), ToolManager registered", g_state.tools_json.size());
}

// ============================================================================
// JNI: nativeSetGrammarMode
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetGrammarMode(JNIEnv *, jobject, jint mode) {
    g_state.grammar_mode = mode;
    LOGI("Grammar mode set to %d", mode);
}

// ============================================================================
// JNI: nativeSetTypedGrammar
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetTypedGrammar(JNIEnv *, jobject, jboolean enabled) {
    g_state.typed_grammar = enabled;
    LOGI("Typed grammar %s", enabled ? "enabled" : "disabled");
}

// ============================================================================
// JNI: nativeUpdateSamplerParams
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeUpdateSamplerParams(
        JNIEnv * env, jobject, jstring jparamsJson) {
    std::lock_guard<std::mutex> lock(g_state.gen_mutex);
    const char * json_cstr = env->GetStringUTFChars(jparamsJson, nullptr);

    try {
        auto j = json::parse(json_cstr);
        env->ReleaseStringUTFChars(jparamsJson, json_cstr);

        if (j.contains("temperature"))     g_state.sampling_params.temp = j["temperature"].get<float>();
        if (j.contains("topK"))            g_state.sampling_params.top_k = j["topK"].get<int>();
        if (j.contains("top_k"))           g_state.sampling_params.top_k = j["top_k"].get<int>();
        if (j.contains("topP"))            g_state.sampling_params.top_p = j["topP"].get<float>();
        if (j.contains("top_p"))           g_state.sampling_params.top_p = j["top_p"].get<float>();
        if (j.contains("minP"))            g_state.sampling_params.min_p = j["minP"].get<float>();
        if (j.contains("min_p"))           g_state.sampling_params.min_p = j["min_p"].get<float>();
        if (j.contains("mirostat"))        g_state.sampling_params.mirostat = j["mirostat"].get<int>();
        if (j.contains("mirostatTau"))     g_state.sampling_params.mirostat_tau = j["mirostatTau"].get<float>();
        if (j.contains("mirostat_tau"))    g_state.sampling_params.mirostat_tau = j["mirostat_tau"].get<float>();
        if (j.contains("mirostatEta"))     g_state.sampling_params.mirostat_eta = j["mirostatEta"].get<float>();
        if (j.contains("mirostat_eta"))    g_state.sampling_params.mirostat_eta = j["mirostat_eta"].get<float>();
        if (j.contains("seed"))            g_state.sampling_params.seed = j["seed"].get<uint32_t>();
        if (j.contains("repeatPenalty"))   g_state.sampling_params.penalty_repeat = j["repeatPenalty"].get<float>();
        if (j.contains("repeat_penalty"))  g_state.sampling_params.penalty_repeat = j["repeat_penalty"].get<float>();
        if (j.contains("frequencyPenalty"))g_state.sampling_params.penalty_freq = j["frequencyPenalty"].get<float>();
        if (j.contains("frequency_penalty"))g_state.sampling_params.penalty_freq = j["frequency_penalty"].get<float>();
        if (j.contains("presencePenalty")) g_state.sampling_params.penalty_present = j["presencePenalty"].get<float>();
        if (j.contains("presence_penalty"))g_state.sampling_params.penalty_present = j["presence_penalty"].get<float>();
        if (j.contains("penaltyLastN"))    g_state.sampling_params.penalty_last_n = j["penaltyLastN"].get<int>();
        if (j.contains("penalty_last_n"))  g_state.sampling_params.penalty_last_n = j["penalty_last_n"].get<int>();

        // DRY sampler params
        if (j.contains("dryMultiplier"))   g_state.sampling_params.dry_multiplier = j["dryMultiplier"].get<float>();
        if (j.contains("dry_multiplier"))  g_state.sampling_params.dry_multiplier = j["dry_multiplier"].get<float>();
        if (j.contains("dryBase"))         g_state.sampling_params.dry_base = j["dryBase"].get<float>();
        if (j.contains("dry_base"))        g_state.sampling_params.dry_base = j["dry_base"].get<float>();
        if (j.contains("dryAllowedLength"))g_state.sampling_params.dry_allowed_length = j["dryAllowedLength"].get<int>();
        if (j.contains("dryPenaltyLastN")) g_state.sampling_params.dry_penalty_last_n = j["dryPenaltyLastN"].get<int>();

        // XTC sampler params
        if (j.contains("xtcProbability"))  g_state.sampling_params.xtc_probability = j["xtcProbability"].get<float>();
        if (j.contains("xtc_probability")) g_state.sampling_params.xtc_probability = j["xtc_probability"].get<float>();
        if (j.contains("xtcThreshold"))    g_state.sampling_params.xtc_threshold = j["xtcThreshold"].get<float>();
        if (j.contains("xtc_threshold"))   g_state.sampling_params.xtc_threshold = j["xtc_threshold"].get<float>();

        rebuild_sampler();
        LOGI("Sampler params updated");
        return JNI_TRUE;

    } catch (const std::exception & e) {
        env->ReleaseStringUTFChars(jparamsJson, json_cstr);
        LOGE("Failed to parse sampler params JSON: %s", e.what());
        return JNI_FALSE;
    }
}

// ============================================================================
// JNI: nativeSetLogitBias
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetLogitBias(
        JNIEnv * env, jobject, jstring jbiasJson) {
    std::lock_guard<std::mutex> lock(g_state.gen_mutex);
    const char * json_cstr = env->GetStringUTFChars(jbiasJson, nullptr);

    try {
        auto j = json::parse(json_cstr);
        env->ReleaseStringUTFChars(jbiasJson, json_cstr);

        g_state.sampling_params.logit_bias.clear();

        if (j.is_object()) {
            for (auto & [key, val] : j.items()) {
                llama_logit_bias bias;
                // Key can be token ID or token string
                try {
                    bias.token = std::stoi(key);
                } catch (...) {
                    // Try to tokenize the string to get token ID
                    auto tokens = tokenize_string(key, false);
                    if (!tokens.empty()) {
                        bias.token = tokens[0];
                    } else {
                        continue;
                    }
                }
                bias.bias = val.get<float>();
                g_state.sampling_params.logit_bias.push_back(bias);
            }
        } else if (j.is_array()) {
            for (auto & item : j) {
                if (item.contains("token") && item.contains("bias")) {
                    llama_logit_bias bias;
                    auto token_val = item["token"];
                    if (token_val.is_number()) {
                        bias.token = token_val.get<int>();
                    } else if (token_val.is_string()) {
                        auto tokens = tokenize_string(token_val.get<std::string>(), false);
                        if (!tokens.empty()) {
                            bias.token = tokens[0];
                        } else {
                            continue;
                        }
                    }
                    bias.bias = item["bias"].get<float>();
                    g_state.sampling_params.logit_bias.push_back(bias);
                }
            }
        }

        // Save a copy as persona biases so setUncensored can merge without losing them
        g_state.persona_biases = g_state.sampling_params.logit_bias;

        // If uncensored mode is active, re-merge refusal biases on top
        if (g_state.char_eng && character_engine_get_uncensored(g_state.char_eng)) {
            auto eff = character_engine_get_params(g_state.char_eng);
            for (int i = 0; i < eff.n_logit_biases; i++) {
                llama_logit_bias lb;
                lb.token = eff.logit_biases[i].token_id;
                lb.bias = eff.logit_biases[i].bias;
                g_state.sampling_params.logit_bias.push_back(lb);
            }
        }

        rebuild_sampler();
        LOGI("Logit bias set: %zu persona + merged", g_state.persona_biases.size());

    } catch (const std::exception & e) {
        env->ReleaseStringUTFChars(jbiasJson, json_cstr);
        LOGE("Failed to parse logit bias JSON: %s", e.what());
    }
}

// ============================================================================
// JNI: nativeLoadControlVectors
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeLoadControlVectors(
        JNIEnv * env, jobject, jstring jvectorsJson) {
    if (!g_state.model || !g_state.ctx) return JNI_FALSE;

    const char * json_cstr = env->GetStringUTFChars(jvectorsJson, nullptr);

    try {
        auto j = json::parse(json_cstr);
        env->ReleaseStringUTFChars(jvectorsJson, json_cstr);

        // Parse control vector paths and scales
        std::vector<common_control_vector_load_info> cvs;
        if (j.is_array()) {
            for (auto & item : j) {
                common_control_vector_load_info cv;
                cv.fname = item.value("path", "");
                cv.strength = item.value("scale", 1.0f);
                if (!cv.fname.empty()) {
                    cvs.push_back(cv);
                }
            }
        } else if (j.is_object()) {
            common_control_vector_load_info cv;
            cv.fname = j.value("path", "");
            cv.strength = j.value("scale", 1.0f);
            if (!cv.fname.empty()) {
                cvs.push_back(cv);
            }
        }

        if (cvs.empty()) {
            LOGW("No valid control vectors found in JSON");
            return JNI_FALSE;
        }

        // Load control vectors
        auto cvec = common_control_vector_load(cvs);
        if (cvec.n_embd == -1) {
            LOGE("Failed to load control vectors");
            return JNI_FALSE;
        }

        int n_embd = llama_model_n_embd(g_state.model);
        if (cvec.n_embd != n_embd) {
            LOGE("Control vector dimension mismatch: %d vs %d", cvec.n_embd, n_embd);
            return JNI_FALSE;
        }

        int err = llama_set_adapter_cvec(g_state.ctx,
                                          cvec.data.data(),
                                          cvec.data.size(),
                                          cvec.n_embd,
                                          -1, -1);
        if (err) {
            LOGE("Failed to apply control vector");
            return JNI_FALSE;
        }

        LOGI("Control vectors loaded and applied");
        return JNI_TRUE;

    } catch (const std::exception & e) {
        env->ReleaseStringUTFChars(jvectorsJson, json_cstr);
        LOGE("Failed to load control vectors: %s", e.what());
        return JNI_FALSE;
    }
}

// ============================================================================
// JNI: nativeClearControlVector
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeClearControlVector(JNIEnv *, jobject) {
    if (!g_state.ctx) return;

    // Pass nullptr to clear the control vector
    llama_set_adapter_cvec(g_state.ctx, nullptr, 0, 0, -1, -1);

    LOGI("Control vector cleared");
}

// ============================================================================
// JNI: nativeGetStateSize
// ============================================================================

extern "C" JNIEXPORT jlong JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGetStateSize(JNIEnv *, jobject) {
    if (!g_state.ctx) return 0;
    return (jlong)llama_state_get_size(g_state.ctx);
}

// ============================================================================
// JNI: nativeStateSaveToFile
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeStateSaveToFile(
        JNIEnv * env, jobject, jstring jpath) {
    if (!g_state.ctx) return JNI_FALSE;

    const char * path = env->GetStringUTFChars(jpath, nullptr);
    bool ok = llama_state_save_file(g_state.ctx, path,
                                     g_state.session_tokens.data(),
                                     g_state.session_tokens.size());
    LOGI("State save to %s: %s", path, ok ? "success" : "failed");
    env->ReleaseStringUTFChars(jpath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ============================================================================
// JNI: nativeStateLoadFromFile
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeStateLoadFromFile(
        JNIEnv * env, jobject, jstring jpath) {
    if (!g_state.ctx) return JNI_FALSE;

    const char * path = env->GetStringUTFChars(jpath, nullptr);

    size_t n_token_count = 0;
    g_state.session_tokens.resize(llama_n_ctx(g_state.ctx));

    bool ok = llama_state_load_file(g_state.ctx, path,
                                     g_state.session_tokens.data(),
                                     g_state.session_tokens.size(),
                                     &n_token_count);

    if (ok) {
        g_state.session_tokens.resize(n_token_count);
        g_state.n_past = n_token_count;
        LOGI("State loaded from %s: %zu tokens", path, n_token_count);
    } else {
        g_state.session_tokens.clear();
        LOGE("Failed to load state from %s", path);
    }

    env->ReleaseStringUTFChars(jpath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ============================================================================
// Embedding Engine (separate model instance for text embeddings)
// ============================================================================

static struct {
    llama_model   * model = nullptr;
    llama_context * ctx   = nullptr;
    std::mutex mutex;
} g_embed;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeLoadEmbeddingModel(
        JNIEnv * env, jobject,
        jstring jpath, jint nThreads, jint nCtx) {

    std::lock_guard<std::mutex> lock(g_embed.mutex);

    if (g_embed.ctx) { llama_free(g_embed.ctx); g_embed.ctx = nullptr; }
    if (g_embed.model) { llama_model_free(g_embed.model); g_embed.model = nullptr; }

    const char * path = env->GetStringUTFChars(jpath, nullptr);

    auto mparams = llama_model_default_params();
    mparams.use_mmap = true;

    g_embed.model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (!g_embed.model) {
        LOGE("Failed to load embedding model");
        return JNI_FALSE;
    }

    auto cparams = llama_context_default_params();
    cparams.n_ctx = nCtx > 0 ? nCtx : 512;
    cparams.n_threads = nThreads > 0 ? nThreads : auto_thread_count();
    cparams.n_threads_batch = cparams.n_threads;
    cparams.n_batch = 512;
    cparams.embeddings = true;

    g_embed.ctx = llama_init_from_model(g_embed.model, cparams);
    if (!g_embed.ctx) {
        LOGE("Failed to create embedding context");
        llama_model_free(g_embed.model);
        g_embed.model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Embedding model loaded (ctx=%d)", nCtx);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeEncodeText(
        JNIEnv * env, jobject,
        jstring jtext, jboolean normalize, jobject callback) {

    std::lock_guard<std::mutex> lock(g_embed.mutex);

    if (!g_embed.model || !g_embed.ctx) {
        jclass cbClass = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");
        jstring jerr = env->NewStringUTF("Embedding model not loaded");
        env->CallVoidMethod(callback, onError, jerr);
        env->DeleteLocalRef(jerr);
        return JNI_FALSE;
    }

    const char * text_cstr = env->GetStringUTFChars(jtext, nullptr);
    std::string text(text_cstr);
    env->ReleaseStringUTFChars(jtext, text_cstr);

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onComplete = env->GetMethodID(cbClass, "onComplete", "(Lcom/mp/ai_gguf/models/EmbeddingResult;)V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    // Tokenize using embedding model's vocab
    const llama_vocab * vocab = llama_model_get_vocab(g_embed.model);
    int n_tokens_max = text.size() + 256;
    std::vector<llama_token> tokens(n_tokens_max);
    int n = llama_tokenize(vocab, text.c_str(), text.size(),
                           tokens.data(), tokens.size(), true, true);
    if (n < 0) {
        tokens.resize(-n);
        n = llama_tokenize(vocab, text.c_str(), text.size(),
                           tokens.data(), tokens.size(), true, true);
    }
    tokens.resize(std::max(0, n));

    if (tokens.empty()) {
        jstring jerr = env->NewStringUTF("Failed to tokenize text");
        env->CallVoidMethod(callback, onError, jerr);
        env->DeleteLocalRef(jerr);
        return JNI_FALSE;
    }

    llama_memory_clear(llama_get_memory(g_embed.ctx), true);

    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        common_batch_add(batch, tokens[i], i, {0}, i == tokens.size() - 1);
    }

    if (llama_decode(g_embed.ctx, batch) != 0) {
        llama_batch_free(batch);
        jstring jerr = env->NewStringUTF("Failed to decode for embeddings");
        env->CallVoidMethod(callback, onError, jerr);
        env->DeleteLocalRef(jerr);
        return JNI_FALSE;
    }

    llama_batch_free(batch);

    int n_embd = llama_model_n_embd(g_embed.model);
    const float * embd = llama_get_embeddings_seq(g_embed.ctx, 0);
    if (!embd) {
        embd = llama_get_embeddings_ith(g_embed.ctx, tokens.size() - 1);
    }

    if (!embd) {
        jstring jerr = env->NewStringUTF("Failed to get embeddings");
        env->CallVoidMethod(callback, onError, jerr);
        env->DeleteLocalRef(jerr);
        return JNI_FALSE;
    }

    std::vector<float> result(embd, embd + n_embd);
    if (normalize) {
        float norm = 0.0f;
        for (float v : result) norm += v * v;
        norm = std::sqrt(norm);
        if (norm > 0.0f) {
            for (float & v : result) v /= norm;
        }
    }

    jclass resultClass = env->FindClass("com/mp/ai_gguf/models/EmbeddingResult");
    jmethodID resultCtor = env->GetMethodID(resultClass, "<init>", "([F)V");
    jfloatArray jembd = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(jembd, 0, n_embd, result.data());
    jobject resultObj = env->NewObject(resultClass, resultCtor, jembd);

    env->CallVoidMethod(callback, onComplete, resultObj);

    env->DeleteLocalRef(jembd);
    env->DeleteLocalRef(resultObj);

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeReleaseEmbeddingModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_embed.mutex);

    if (g_embed.ctx) { llama_free(g_embed.ctx); g_embed.ctx = nullptr; }
    if (g_embed.model) { llama_model_free(g_embed.model); g_embed.model = nullptr; }

    LOGI("Embedding model released");
}

// ============================================================================
// Character Engine JNI bindings
// ============================================================================

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetPersonality(
        JNIEnv * env, jobject, jstring jparamsJson) {
    std::lock_guard<std::mutex> lock(g_state.gen_mutex);
    const char * json_cstr = env->GetStringUTFChars(jparamsJson, nullptr);

    try {
        auto j = json::parse(json_cstr);
        env->ReleaseStringUTFChars(jparamsJson, json_cstr);

        if (!g_state.char_eng) {
            g_state.char_eng = character_engine_create();
        }

        char_personality p = {};
        std::string name = j.value("name", "");
        std::string persona = j.value("persona", "");
        p.name = name.c_str();
        p.persona = persona.c_str();
        p.temperature = j.value("temperature", 0.7f);
        p.top_p = j.value("topP", j.value("top_p", 0.9f));
        p.repetition_penalty = j.value("repetitionPenalty", j.value("repetition_penalty", 1.1f));
        p.creativity = j.value("creativity", 0.5f);
        p.verbosity = j.value("verbosity", 0.5f);
        p.formality = j.value("formality", 0.5f);

        character_engine_set_personality(g_state.char_eng, &p);

        // Apply the effective params to the sampler
        auto eff = character_engine_get_params(g_state.char_eng);
        g_state.sampling_params.temp = eff.temperature;
        g_state.sampling_params.top_p = eff.top_p;
        g_state.sampling_params.min_p = eff.min_p;
        g_state.sampling_params.top_k = eff.top_k;
        g_state.sampling_params.penalty_repeat = eff.repetition_penalty;

        // Apply logit biases from character engine
        g_state.sampling_params.logit_bias.clear();
        for (int i = 0; i < eff.n_logit_biases; i++) {
            llama_logit_bias lb;
            lb.token = eff.logit_biases[i].token_id;
            lb.bias = eff.logit_biases[i].bias;
            g_state.sampling_params.logit_bias.push_back(lb);
        }

        rebuild_sampler();
        LOGI("CharacterEngine personality set: %s (temp=%.2f top_p=%.2f)",
             name.c_str(), eff.temperature, eff.top_p);

    } catch (const std::exception & e) {
        env->ReleaseStringUTFChars(jparamsJson, json_cstr);
        LOGE("Failed to set personality: %s", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetMood(JNIEnv *, jobject, jint mood) {
    std::lock_guard<std::mutex> lock(g_state.gen_mutex);
    if (!g_state.char_eng) {
        g_state.char_eng = character_engine_create();
    }

    character_engine_set_mood(g_state.char_eng, (char_mood)mood);

    // Re-apply effective params to sampler
    auto eff = character_engine_get_params(g_state.char_eng);
    g_state.sampling_params.temp = eff.temperature;
    g_state.sampling_params.top_p = eff.top_p;
    g_state.sampling_params.min_p = eff.min_p;
    g_state.sampling_params.top_k = eff.top_k;
    g_state.sampling_params.penalty_repeat = eff.repetition_penalty;

    rebuild_sampler();
    LOGI("CharacterEngine mood set: %d (temp=%.2f)", mood, eff.temperature);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGetCharacterContext(JNIEnv * env, jobject) {
    if (!g_state.char_eng) return env->NewStringUTF("");

    char * ctx = character_engine_get_context(g_state.char_eng);
    jstring result = env->NewStringUTF(ctx ? ctx : "");
    character_engine_free_string(ctx);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetUncensored(JNIEnv *, jobject, jboolean enabled) {
    std::lock_guard<std::mutex> lock(g_state.gen_mutex);
    if (!g_state.char_eng) {
        g_state.char_eng = character_engine_create();
    }
    character_engine_set_uncensored(g_state.char_eng, enabled);

    // Scan vocab for refusal pattern tokens and supply them to the engine
    // This is where the real uncensoring happens - at the logit/weight level
    if (enabled && g_state.model) {
        const llama_vocab * vocab = llama_model_get_vocab(g_state.model);
        std::vector<int32_t> refusal_ids;
        // Single-word refusal/deflection tokens that appear in BPE vocabularies.
        // Covers: hard refusal, soft refusal, deflection, disclaimers, moralizing.
        static const char * refusal_patterns[] = {
            // Hard refusal
            "cannot", "can't", "refus", "unable", "won't",
            // Soft refusal / deflection
            "Sorry", "sorry", "apologize", "instead",
            "suggest", "recommend", "encourage",
            "clarify", "elaborate", "rephrase",
            // Safety/moral vocabulary
            "inappropriate", "unethical", "illegal",
            "dangerous", "harmful", "unsafe", "immoral",
            "guidelines", "disclaimer", "advisable",
            "irresponsible", "unacceptable", "condemn",
            "consequences", "authorities",
            // Concern trolling
            "wellbeing", "well-being", "welfare",
            "concerned", "distress",
            nullptr
        };

        int32_t n_vocab = llama_vocab_n_tokens(vocab);
        for (int32_t id = 0; id < n_vocab; id++) {
            char buf[256] = {};
            int len = llama_token_to_piece(vocab, id, buf, sizeof(buf) - 1, 0, true);
            if (len <= 0) continue;
            buf[len] = '\0';
            std::string tok(buf);

            for (int pi = 0; refusal_patterns[pi]; pi++) {
                if (tok.find(refusal_patterns[pi]) != std::string::npos) {
                    refusal_ids.push_back(id);
                    break;
                }
            }
        }

        if (!refusal_ids.empty()) {
            character_engine_set_refusal_tokens(g_state.char_eng,
                refusal_ids.data(), (int32_t)refusal_ids.size());
            LOGI("Uncensored: suppressing %zu refusal tokens", refusal_ids.size());
        }

        // Merge persona biases + refusal biases (don't wipe persona biases)
        g_state.sampling_params.logit_bias = g_state.persona_biases;
        auto eff = character_engine_get_params(g_state.char_eng);
        for (int i = 0; i < eff.n_logit_biases; i++) {
            llama_logit_bias lb;
            lb.token = eff.logit_biases[i].token_id;
            lb.bias = eff.logit_biases[i].bias;
            g_state.sampling_params.logit_bias.push_back(lb);
        }
        rebuild_sampler();
        LOGI("Uncensored ON: %zu persona + %d refusal biases",
             g_state.persona_biases.size(), eff.n_logit_biases);
    } else if (!enabled) {
        // Restore only persona biases, removing refusal ones
        g_state.sampling_params.logit_bias = g_state.persona_biases;
        rebuild_sampler();
        LOGI("Uncensored OFF: restored %zu persona biases", g_state.persona_biases.size());
    }

    LOGI("CharacterEngine uncensored mode: %s", enabled ? "ON" : "OFF");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGetUncensored(JNIEnv *, jobject) {
    if (!g_state.char_eng) return JNI_FALSE;
    return character_engine_get_uncensored(g_state.char_eng) ? JNI_TRUE : JNI_FALSE;
}

// ============================================================================
// JNI: nativeSupportsThinking — detect from chat template
// ============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSupportsThinking(JNIEnv *, jobject) {
    if (!g_state.chat_templates) return JNI_FALSE;
    return common_chat_templates_support_enable_thinking(g_state.chat_templates.get())
           ? JNI_TRUE : JNI_FALSE;
}

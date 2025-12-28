#ifndef MODEL_STATE_H
#define MODEL_STATE_H

#include "llama.h"
#include <string>
#include <vector>
#include <atomic>
#include <jni.h>

struct ModelState {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    llama_sampler* sampler = nullptr;
    llama_sampler* grammar_sampler = nullptr;

    int32_t ctx_size = 0;
    int32_t batch_size = 512;

    std::string system_prompt;
    std::string chat_template_override;
    std::string tools_json;
    std::atomic<bool> tools_enabled{false};

    // ✅ ADD THIS: UTF-8 streaming buffer
    std::string utf8_carry_buffer;

    // Existing methods...
    bool is_ready() const { return model && ctx; }
    void release();
    void prepare_for_generation();

    void warmup_context() const;

    std::vector<llama_token> tokenize(const std::string& text) const;
    std::string detokenize_single(llama_token t) const;

    // ✅ ADD THIS: Buffered detokenization
    std::string detokenize_buffered(llama_token t);
    std::string flush_utf8_buffer();

    llama_token space_token() const;
    bool decode_prompt(const std::vector<llama_token>& toks) const;

    jlong get_state_size() const;
    void* get_state_data(void* buffer, size_t size) const;
    bool load_state_data(const void* data, size_t size) const;
    void rebuild_sampler(
            int topK,
            float topP,
            float temp,
            float minP,
            int mirostat,
            float mirostatTau,
            float mirostatEta,
            int seed);
};



#endif // MODEL_STATE_H
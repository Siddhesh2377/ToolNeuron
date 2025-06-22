#include "LLMInference.h"
#include "llama.h"
#include "gguf.h"
#include <android/log.h>
#include <cstring>
#include <iostream>

#define TAG "[SmolLMAndroid-Cpp]"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

std::vector<llama_token> common_tokenize(const struct llama_vocab *vocab, const std::string &text, bool add_special, bool parse_special = false);
std::string common_token_to_piece(const struct llama_context *ctx, llama_token token, bool special = true);

void LLMInference::loadModel(const char* model_path, float minP, float temperature, bool storeChats, long contextSize, const char* chatTemplate, int nThreads, bool useMmap, bool useMlock) {
    LOGi("loading model with\n\tmodel_path = %s\n\tminP = %f\n\ttemperature = %f\n\tstoreChats = %d\n\tcontextSize = %li\n\tchatTemplate = %s\n\tnThreads = %d\n\tuseMmap = %d\n\tuseMlock = %d",model_path, minP, temperature, storeChats, contextSize, chatTemplate, nThreads, useMmap, useMlock);

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = useMmap;
    model_params.use_mlock = useMlock;

    FILE* f = fopen(model_path, "rb");
    if (!f) {
        LOGe("‼️ Model file not found or unreadable at path: %s", model_path);
        throw std::runtime_error("Model file not found");
    }
    fclose(f);

    _model = llama_model_load_from_file(model_path, model_params);
    if (!_model) throw std::runtime_error("loadModel() failed");

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = nThreads;
    ctx_params.no_perf = false;
    _ctx = llama_init_from_model(_model, ctx_params);
    if (!_ctx) throw std::runtime_error("llama_new_context_with_model() returned null");

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = false;
    _sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(_sampler, llama_sampler_init_min_p(minP, 1));
    llama_sampler_chain_add(_sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    _formattedMessages.resize(llama_n_ctx(_ctx));
    _chatTemplate = strdup(chatTemplate);
    _storeChats = storeChats;
    _messages.clear();
}

void LLMInference::addChatMessage(const char* message, const char* role) {
    _messages.push_back({ strdup(role), strdup(message) });
}

float LLMInference::getResponseGenerationTime() const {
    return static_cast<float>(_responseNumTokens) / (_responseGenerationTime / 1e6);
}

int LLMInference::getContextSizeUsed() const {
    return _nCtxUsed;
}

void LLMInference::startCompletion(const char* query) {
    _response.clear();
    _cacheResponseTokens.clear();

    if (!_storeChats) {
        LOGi("clearing messages");
        for (llama_chat_message& message : _messages) {
            free(const_cast<char*>(message.role));
            free(const_cast<char*>(message.content));
        }
        _messages.clear();
        _prevLen = 0;
        _formattedMessages.resize(llama_n_ctx(_ctx));
    }

    _responseGenerationTime = 0;
    _responseNumTokens = 0;

    addChatMessage(query, "user");

    int newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true, _formattedMessages.data(), _formattedMessages.size());
    if (newLen > static_cast<int>(_formattedMessages.size())) {
        _formattedMessages.resize(newLen);
        newLen = llama_chat_apply_template(_chatTemplate, _messages.data(), _messages.size(), true, _formattedMessages.data(), _formattedMessages.size());
    }
    if (newLen < 0) throw std::runtime_error("llama_chat_apply_template() failed");

    std::string prompt(_formattedMessages.begin() + _prevLen, _formattedMessages.begin() + newLen);
    _promptTokens = common_tokenize(llama_model_get_vocab(_model), prompt, true, true);

    _batch.token = _promptTokens.data();
    _batch.n_tokens = _promptTokens.size();
}

bool LLMInference::_isValidUtf8(const char* response) {
    if (!response) return true;
    const unsigned char* bytes = reinterpret_cast<const unsigned char*>(response);
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) num = 1;
        else if ((*bytes & 0xE0) == 0xC0) num = 2;
        else if ((*bytes & 0xF0) == 0xE0) num = 3;
        else if ((*bytes & 0xF8) == 0xF0) num = 4;
        else return false;

        bytes++;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes++;
        }
    }
    return true;
}

std::string LLMInference::completionLoop() {

    uint32_t contextSize = llama_n_ctx(_ctx);
    _nCtxUsed = llama_kv_self_used_cells(_ctx);



    LOGi("CTX USED: %d / %d", _nCtxUsed + _batch.n_tokens, contextSize);
    if (_nCtxUsed + _batch.n_tokens > contextSize)
        throw std::runtime_error("context size reached");

    auto start = ggml_time_us();
    if (llama_decode(_ctx, _batch) < 0)
        throw std::runtime_error("llama_decode() failed");

    _currToken = llama_sampler_sample(_sampler, _ctx, -1);
    if (llama_vocab_is_eog(llama_model_get_vocab(_model), _currToken)) {
        if (_storeChats)
            addChatMessage(strdup(_response.data()), "assistant");
        _response.clear();
        return "[EOG]";
    }

    std::string piece = common_token_to_piece(_ctx, _currToken, true);
    LOGi("common_token_to_piece: %s", piece.c_str());

    auto end = ggml_time_us();
    _responseGenerationTime += (end - start);
    _responseNumTokens++;
    _cacheResponseTokens += piece;

    _batch.token = &_currToken;
    _batch.n_tokens = 1;

    if (_isValidUtf8(_cacheResponseTokens.c_str())) {
        _response += _cacheResponseTokens;
        std::string valid_utf8_piece = _cacheResponseTokens;
        _cacheResponseTokens.clear();
        return valid_utf8_piece;
    }

    return "";
}

void LLMInference::stopCompletion() {
    if (!_storeChats){
        llama_kv_self_clear(_ctx);
    }
    if (_storeChats)
        addChatMessage(_response.c_str(), "assistant");
    else
        _messages.clear();
    _response.clear();

    const char* tmpl = llama_model_chat_template(_model, nullptr);
    _prevLen = llama_chat_apply_template(tmpl, _messages.data(), _messages.size(), false, nullptr, 0);
    if (_prevLen < 0)
        throw std::runtime_error("llama_chat_apply_template() in stopCompletion() failed");
}

LLMInference::~LLMInference() {
    LOGi("deallocating LLMInference instance");
    for (llama_chat_message& message : _messages) {
        free(const_cast<char*>(message.role));
        free(const_cast<char*>(message.content));
    }
    free(const_cast<char*>(_chatTemplate));
    llama_model_free(_model);
    llama_free(_ctx);
}

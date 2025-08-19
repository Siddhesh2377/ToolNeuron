// ai_core_streaming.cpp
// Streaming generation for Android with llama.cpp + UTF-safe JNI bridging
// Package: com.mp.ai_core

#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <memory>
#include <cstring>
#include <cstdint>

#include "llama.h"
#include "cpu_helper.h"

#if defined(__ANDROID__)
#include <android/log.h>
#define LOG_TAG "ai_core"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...)
#define LOGE(...)
#endif

// -----------------------------------------------------------------------------
// Tunables (quick switches for debugging)
// -----------------------------------------------------------------------------
// 1) Greedy-only to prove streaming works (set to 1). Set back to 0 after.
#ifndef AI_CORE_GREEDY_ONLY
#define AI_CORE_GREEDY_ONLY 0
#endif
// 2) Add assistant stub via chat template. Some models need false.
#ifndef AI_CORE_ADD_ASSISTANT_STUB
#define AI_CORE_ADD_ASSISTANT_STUB 0
#endif

// -----------------------------------------------------------------------------
// Globals
// -----------------------------------------------------------------------------
static llama_model*   g_model   = nullptr;
static llama_context* g_ctx     = nullptr;
static llama_sampler* g_sampler = nullptr;
static std::string    g_system_prompt = "You are a helpful assistant.";
static std::string g_model_info;
static std::string g_chat_template_override;
static std::atomic<bool> g_stop_requested(false);


// Carry buffer for streaming UTF-8 that may be split mid-codepoint.
static thread_local std::string g_utf8_carry;

std::string get_model_info(llama_model *pModel);

// -----------------------------------------------------------------------------
// UTF helpers (UTF-16 <-> UTF-8, streaming-safe)
// -----------------------------------------------------------------------------
static inline void push_u16(std::u16string &o, uint32_t cp) {
    if (cp <= 0xFFFFu) {
        if (cp >= 0xD800u && cp <= 0xDFFFu) cp = 0xFFFDu; // disallow lone surrogates
        o.push_back((char16_t)cp);
    } else {
        cp -= 0x10000u;
        o.push_back((char16_t)(0xD800u + (cp >> 10)));
        o.push_back((char16_t)(0xDC00u + (cp & 0x3FF)));
    }
}

static inline bool decode_one_utf8(const std::string &s, size_t &i, uint32_t &cp) {
    if (i >= s.size()) return false;
    unsigned char b0 = (unsigned char)s[i];
    size_t rem = s.size() - i;

    if (b0 < 0x80) { cp = b0; i += 1; return true; }
    if ((b0 >> 5) == 0x6) { if (rem < 2) return false; unsigned char b1 = (unsigned char)s[i+1];
        if ((b1 & 0xC0) != 0x80) { i++; cp = 0xFFFDu; return true; }
        cp = ((b0 & 0x1F) << 6) | (b1 & 0x3F); i += 2; return true; }
    if ((b0 >> 4) == 0xE) { if (rem < 3) return false; unsigned char b1 = (unsigned char)s[i+1], b2 = (unsigned char)s[i+2];
        if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80) { i++; cp = 0xFFFDu; return true; }
        cp = ((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F); i += 3; return true; }
    if ((b0 >> 3) == 0x1E) { if (rem < 4) return false; unsigned char b1 = (unsigned char)s[i+1], b2 = (unsigned char)s[i+2], b3 = (unsigned char)s[i+3];
        if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) { i++; cp = 0xFFFDu; return true; }
        cp = ((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F); i += 4; return true; }
    i++; cp = 0xFFFDu; return true;
}

static void utf8_to_utf16_with_carry(const std::string &in, std::u16string &out, std::string &carry) {
    std::string s = carry + in;
    carry.clear();
    size_t i = 0;
    while (i < s.size()) {
        size_t before = i; uint32_t cp = 0; bool ok = decode_one_utf8(s, i, cp);
        if (!ok) { carry.assign(s.begin() + before, s.end()); break; }
        push_u16(out, cp);
    }
}

static std::string jstr_to_utf8(JNIEnv* env, jstring js) {
    if (!js) return {};
    jsize n = env->GetStringLength(js);
    const jchar* p = env->GetStringChars(js, nullptr); // UTF-16
    std::string out; out.reserve((size_t)n);
    for (jsize i = 0; i < n; ) {
        uint32_t cp; uint16_t w1 = p[i++];
        if (w1 >= 0xD800 && w1 <= 0xDBFF && i < n) {
            uint16_t w2 = p[i];
            if (w2 >= 0xDC00 && w2 <= 0xDFFF) { ++i; cp = 0x10000u + (((w1 - 0xD800u) << 10) | (w2 - 0xDC00u)); }
            else cp = 0xFFFDu;
        } else if (w1 >= 0xDC00 && w1 <= 0xDFFF) {
            cp = 0xFFFDu;
        } else {
            cp = w1;
        }
        if (cp < 0x80) out.push_back((char)cp);
        else if (cp < 0x800) { out.push_back((char)(0xC0 | (cp >> 6))); out.push_back((char)(0x80 | (cp & 0x3F))); }
        else if (cp < 0x10000) { out.push_back((char)(0xE0 | (cp >> 12))); out.push_back((char)(0x80 | ((cp >> 6) & 0x3F))); out.push_back((char)(0x80 | (cp & 0x3F))); }
        else { out.push_back((char)(0xF0 | (cp >> 18))); out.push_back((char)(0x80 | ((cp >> 12) & 0x3F))); out.push_back((char)(0x80 | ((cp >> 6) & 0x3F))); out.push_back((char)(0x80 | (cp & 0x3F))); }
    }
    env->ReleaseStringChars(js, p);
    return out;
}

static jstring utf8_to_jstring(JNIEnv* env, const std::string &utf8, std::string &carry) {
    std::u16string u16; utf8_to_utf16_with_carry(utf8, u16, carry);
    if (u16.empty()) return nullptr;
    return env->NewString(reinterpret_cast<const jchar*>(u16.data()), (jsize)u16.size());
}

static void flush_utf8_carry(JNIEnv* env, jobject cb) {
    if (g_utf8_carry.empty()) return;
    std::string tmp = g_utf8_carry;
    tmp.append("\xEF\xBF\xBD", 3); // append U+FFFD in UTF-8
    jclass cls = env->GetObjectClass(cb);
    if (!cls) { g_utf8_carry.clear(); return; }
    jmethodID mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    if (!mid) { g_utf8_carry.clear(); return; }
    std::string dummy;
    jstring jtok = utf8_to_jstring(env, tmp, dummy);
    if (jtok) { env->CallVoidMethod(cb, mid, jtok); env->DeleteLocalRef(jtok); }
    g_utf8_carry.clear();
}

// -----------------------------------------------------------------------------
// Resource management
// -----------------------------------------------------------------------------
static void free_everything() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);             g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);     g_model   = nullptr; }
    llama_backend_free();
}

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1core_NativeLib_nativeStopGeneration(JNIEnv*, jobject) {
    g_stop_requested = true;
    LOGI("Stop generation requested");
}


// -----------------------------------------------------------------------------
// JNI API
// -----------------------------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1core_NativeLib_nativeSetSystemPrompt(JNIEnv* env, jobject /*thiz*/, jstring jprompt) {
    g_system_prompt = jstr_to_utf8(env, jprompt);
    LOGI("System prompt updated (%zu bytes)", g_system_prompt.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1core_NativeLib_nativeSetChatTemplate(JNIEnv* env, jobject, jstring jtemplate) {
    g_chat_template_override = jstr_to_utf8(env, jtemplate);
    LOGI("Chat template override set (%zu bytes)", g_chat_template_override.size());
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1core_NativeLib_nativeRelease(JNIEnv*, jobject) {
    free_everything();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1core_NativeLib_nativeInit(
        JNIEnv* env, jobject,
        jstring jpath, jint jthreads,
        jint gpuLayers, jboolean useMMAP,
        jboolean useMLOCK, jint ctxSize,
        jfloat temp, jint topK, jfloat topP, jfloat minP) {
    const std::string path = jstr_to_utf8(env, jpath);
    LOGI("supports_gpu_offload = %d", llama_supports_gpu_offload());

    free_everything();
    llama_backend_init();

    int physCores = count_physical_cores();
    LOGI("physical cores = %d", physCores);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers  = gpuLayers;
    mparams.use_mmap      = useMMAP;
    mparams.use_mlock     = useMLOCK;
    mparams.check_tensors = true;

    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) {
        LOGE("Failed to load model: %s", path.c_str());
        free_everything();
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = ctxSize;
    cparams.n_batch         = 2048;
    cparams.n_ubatch        = 512;
    cparams.n_seq_max       = 1;
    cparams.n_threads       = jthreads > 0 ? jthreads : physCores;
    cparams.n_threads_batch = cparams.n_threads;
    cparams.offload_kqv     = true;
    cparams.no_perf         = true;

    g_ctx = llama_init_from_model(g_model, cparams);

   // g_model_info = get_model_info(g_model);
    if (!g_ctx) {
        LOGE("Failed to create context");
        free_everything();
        return JNI_FALSE;
    }

    // Sampler chain
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sp);

#if AI_CORE_GREEDY_ONLY
    // Deterministic baseline: prove streaming end-to-end
    llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());
#else
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(topK));
    if (topP < 1.0f) llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(topP, /*min_keep*/1));
    if (temp != 1.0f) llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temp));

    if (temp > 0.0f)  llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(/*seed*/ -1));
    if (minP > 0.0f) llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(minP, 1));

    else              llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());
#endif

    LOGI("supports_gpu_offload = %d", llama_supports_gpu_offload());
    LOGI("model_n_layer       = %d", llama_model_n_layer(g_model));
    LOGI("model_n_embd        = %d", llama_model_n_embd(g_model));
    LOGI("model_ctx_train     = %d", llama_model_n_ctx_train(g_model));
    LOGI("system: %s", llama_print_system_info());

    LOGI("Model initialized");
    return JNI_TRUE;
}

std::string get_model_info(llama_model *model) {
    if (!model) return "<no model loaded>";

    std::ostringstream oss;
    oss << "# Model Info\n\n";

    // Core dimensions
    oss << "## Core Dimensions\n";
    oss << "- **n_vocab**: `" << llama_vocab_n_tokens(llama_model_get_vocab(model)) << "`\n";
    oss << "- **n_ctx_train**: `" << llama_model_n_ctx_train(model) << "`\n";
    oss << "- **n_embd**: `" << llama_model_n_embd(model) << "`\n";
    oss << "- **n_layer**: `" << llama_model_n_layer(model) << "`\n";
    oss << "- **n_head**: `" << llama_model_n_head(model) << "`\n";
    oss << "- **n_head_kv**: `" << llama_model_n_head_kv(model) << "`\n\n";

    // Context / seq limits
    oss << "## Context / Sequence Limits\n";
    oss << "- **n_ctx_train**: `" << llama_model_n_ctx_train(model) << "`\n\n";

    // Vocab specials (supported ones)
    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (vocab) {
        oss << "## Vocab Special Tokens\n";
        oss << "- **BOS**: `" << llama_vocab_bos(vocab) << "`\n";
        oss << "- **EOS**: `" << llama_vocab_eos(vocab) << "`\n";
        oss << "- **EOT**: `" << llama_vocab_eot(vocab) << "`\n";
        oss << "- **NL**: `" << llama_vocab_nl(vocab) << "`\n\n";
    }

    // System info (from llama_print_system_info)
    oss << "## System Info\n";
    oss << "```\n";
    oss << llama_print_system_info() << "\n";
    oss << "```\n";

    return oss.str();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mp_ai_1core_NativeLib_nativeGetModelInfo(JNIEnv* env, jobject) {
    if (g_model_info.empty()) {
        return env->NewStringUTF("<no model loaded>");
    }
    return env->NewStringUTF(g_model_info.c_str());
}


// Kotlin callback helpers ------------------------------------------------------
static void jni_on_error(JNIEnv* env, jobject cb, const char* msg) {
    jclass cls = env->GetObjectClass(cb); if (!cls) return;
    jmethodID mid = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
    if (!mid) return;
    std::string dummy; jstring jmsg = utf8_to_jstring(env, std::string(msg ? msg : "error"), dummy);
    if (!jmsg) jmsg = env->NewStringUTF("error");
    env->CallVoidMethod(cb, mid, jmsg);
    env->DeleteLocalRef(jmsg);
}

static void jni_on_token(JNIEnv* env, jobject cb, const std::string& s) {
    jclass cls = env->GetObjectClass(cb); if (!cls) return;
    jmethodID mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    if (!mid) return;
    jstring jtok = utf8_to_jstring(env, s, g_utf8_carry);
    if (jtok) { env->CallVoidMethod(cb, mid, jtok); env->DeleteLocalRef(jtok); }
}

static void jni_on_done(JNIEnv* env, jobject cb) {
    jclass cls = env->GetObjectClass(cb); if (!cls) return;
    jmethodID mid = env->GetMethodID(cls, "onDone", "()V");
    if (!mid) return;
    env->CallVoidMethod(cb, mid);
}

// -----------------------------------------------------------------------------
// Chat template handling
// -----------------------------------------------------------------------------
static std::string apply_chat_template(const llama_model *model,
                                       const std::string &system_msg,
                                       const std::string &user_msg,
                                       bool add_assistant) {
    const char *tmpl = nullptr;

    // Prefer override if set
    if (!g_chat_template_override.empty()) {
        tmpl = g_chat_template_override.c_str();
    } else {
        tmpl = llama_model_chat_template(model, nullptr);
    }

    if (!tmpl || *tmpl == '\0') {
        // Fallback
        std::string out;
        if (!system_msg.empty()) {
            out += "System: ";
            out += system_msg;
            out += "\n";
        }
        out += "User: ";
        out += user_msg;
        out += "\nAssistant: ";
        return out;
    }

    // Same logic as before...
    std::vector<llama_chat_message> msgs;
    if (!system_msg.empty()) {
        msgs.push_back({"system", system_msg.c_str()});
    }
    msgs.push_back({"user", user_msg.c_str()});

    int32_t need = llama_chat_apply_template(
            tmpl, msgs.data(), (int32_t)msgs.size(),
            1,
            nullptr, 0
    );
    if (need < 0) need = -need;

    std::string out((size_t)need, '\0');

    int32_t written = llama_chat_apply_template(
            tmpl, msgs.data(), (int32_t)msgs.size(),
            1,
            out.data(), need
    );
    if (written < 0) written = -written;

    out.resize((size_t)written);
    return out;
}



static std::string detok_piece(const llama_vocab* vocab, llama_token tok) {
    char tmp[512];
    int n = llama_token_to_piece(vocab, tok, tmp, (int)sizeof(tmp), 0, /*special*/true);
    if (n < 0) { std::string out; out.resize((size_t)(-n));
        llama_token_to_piece(vocab, tok, out.data(), -n, 0, true); return out; }
    return std::string(tmp, tmp + n);
}

// -----------------------------------------------------------------------------
// Generation
// -----------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1core_NativeLib_nativeGenerateStream(
        JNIEnv* env, jobject /*this*/, jstring jprompt, jint max_tokens, jobject jcallback) {
    if (!g_ctx || !g_model || !g_sampler) {
        jni_on_error(env, jcallback, "Not initialized");
        return JNI_FALSE;
    }

    // Reset between turns
    {
        llama_memory_t mem = llama_get_memory(g_ctx);
        if (mem) llama_memory_clear(mem, /*data=*/true);
        llama_sampler_reset(g_sampler);
    }
    g_stop_requested = false; // reset before new generation

    const std::string user_prompt = jstr_to_utf8(env, jprompt);
    const llama_vocab* vocab = llama_model_get_vocab(g_model);

    const char* tmpl = llama_model_chat_template(g_model, /*name*/ nullptr);
    LOGI("Chat template %s", tmpl ? "detected" : "missing (fallback)");

    std::string rendered = apply_chat_template(g_model, g_system_prompt, user_prompt, true);


    LOGI("rendered.size=%d", (int)rendered.size());

    // Tokenize
    std::vector<llama_token> toks;
    {
        int32_t guess = (int32_t)rendered.size() + 8;
        toks.resize((size_t)guess);
        int32_t n = llama_tokenize(
                vocab, rendered.c_str(), (int32_t)rendered.size(),
                toks.data(), (int32_t)toks.size(),
                /*add_special*/ true, /*parse_special*/ true);
        if (n < 0) {
            toks.resize((size_t)(-n));
            n = llama_tokenize(vocab, rendered.c_str(), (int32_t)rendered.size(),
                               toks.data(), (int32_t)toks.size(), true, true);
        }
        if (n < 0) { jni_on_error(env, jcallback, "tokenize failed"); return JNI_FALSE; }
        toks.resize((size_t)n);
        LOGI("prompt toks = %d", (int)toks.size());
    }

    // Feed prompt
    llama_batch batch = llama_batch_init((int32_t)std::max<size_t>(toks.size(), 1), 0, 1);
    for (int i = 0; i < (int)toks.size(); ++i) {
        batch.token[i]     = toks[(size_t)i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = (i == (int)toks.size() - 1);
    }
    batch.n_tokens = (int32_t)toks.size();

    if (batch.n_tokens > 0) {
        int rc = llama_decode(g_ctx, batch);
        LOGI("decode(prompt) rc=%d", rc);
        if (rc != 0) {
            llama_batch_free(batch);
            if (rc == 1) jni_on_error(env, jcallback, "decode failed: no KV slot (context overflow)");
            jni_on_error(env, jcallback, "decode() failed on prompt");
            return JNI_FALSE;
        }
    }

    // Streaming loop
    llama_batch one = llama_batch_init(1, 0, 1);
    auto cur_pos = (int32_t)toks.size();
    int to_gen = max_tokens > 0 ? max_tokens : 128;

    const llama_token eos = llama_vocab_eos(vocab);
    const llama_token eot = llama_vocab_eot(vocab);

    for (int i = 0; i < to_gen; ++i, ++cur_pos) {
        if (g_stop_requested) {
            LOGI("WARNING :: Generation stopped by user");
            break;
        }

        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        llama_sampler_accept(g_sampler, tok);

        if (i == 0 && (tok == eos || tok == eot)) {
            // First token was end-of-turn; try to nudge by injecting a space once
            llama_token sp_buf[8];
            int nsp = llama_tokenize(vocab, " ", 1, sp_buf, 8, /*add_special*/true, /*parse_special*/true);
            if (nsp > 0) {
                tok = sp_buf[0];
            }
        }

        if (tok == eos || tok == eot) break;

        std::string piece = detok_piece(vocab, tok);
        LOGI("TOKEN::%.*s", (int)piece.size(), piece.c_str());
        jni_on_token(env, jcallback, piece);

        one.n_tokens     = 1;
        one.token[0]     = tok;
        one.pos[0]       = cur_pos;
        one.n_seq_id[0]  = 1;
        one.seq_id[0][0] = 0;
        one.logits[0]    = true;
        int rc = llama_decode(g_ctx, one);
        if (rc != 0) {
            if (rc == 1) jni_on_error(env, jcallback, "decode failed during generation: no KV slot");
            else         jni_on_error(env, jcallback, "decode failed during generation");
            break;
        }

        if (env->ExceptionCheck()) {
            LOGE("Java exception during callback");
            env->ExceptionClear();
            break;
        }
    }

    // Clean up batches
    llama_batch_free(one);
    llama_batch_free(batch);

    // Flush any pending UTF-8 carry chunk
    flush_utf8_carry(env, jcallback);

    jni_on_done(env, jcallback);
    return JNI_TRUE;
}

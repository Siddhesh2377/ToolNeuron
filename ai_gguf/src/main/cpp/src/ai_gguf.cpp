/*=============================================================
 *   ai_core.cpp
 *=============================================================
 *
 *  Main Android JNI entry point for text generation.
 *  - No GPU support – all contexts are CPU‑only.
 *  - Keeps a global `ModelState` instance.
 *  - Provides callbacks to Kotlin for streaming, tool calls, errors,
 *    and completion.
 *  - Exposes convenience functions for KV‑cache clearing,
 *    state persistence, diagnostics, etc.
 *  - Completely thread‑safe via a single mutex for init/cleanup.
 *============================================================*/

#include "state/model_state.h"
#include "utils/jni_utils.h"
#include "utils/utf8_utils.h"
#include "chat/chat_template.h"

#include "llama.h"
#include "ggml-backend.h"
#include "cpu/cpu_helper.h"
#include "utils/logger.h"
#include "state/global_state.h"
#include "tool_calling/tool_call_state.h"
#include <sstream>
#include <algorithm>

#include <jni.h>
#include <string>
#include <mutex>

/*  --------------------------------------------------------------
 *      Global state and guard
 *  -------------------------------------------------------------- */
static std::mutex g_init_mtx;                  // guards init/release
static std::atomic<bool> g_stop_requested{false};

/*  --------------------------------------------------------------
 *      Helper – build & init grammar when tools enabled
 *  -------------------------------------------------------------- */
static void maybe_init_grammar() {
    if (!g_state.tools_enabled) return;
    LOG_INFO("Initializing tool‑call grammar");
    const std::string grammar = chat::build_tool_grammar(g_state.tools_json);
    if (!grammar.empty()) {
        if (g_state.grammar_sampler)
            llama_sampler_free(g_state.grammar_sampler);
        const llama_vocab* vocab = llama_model_get_vocab(g_state.model);
        g_state.grammar_sampler = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (!g_state.grammar_sampler) {
            LOG_ERROR("Tool grammar initialization failed");
            g_state.tools_enabled = false;
        }
    }
}

/*  --------------------------------------------------------------
 *      JNI: load model & init context
 *  -------------------------------------------------------------- */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeInit(JNIEnv* env, jobject,
                                          jstring jpath,
                                          jint jthreads,
                                          jint ctxSize,
                                          jfloat temp,
                                          jint topK,
                                          jfloat topP,
                                          jfloat minP,
                                          jint mirostat,
                                          jfloat mirostatTau,
                                          jfloat mirostatEta,
                                          jint seed) {
    std::lock_guard<std::mutex> lk(g_init_mtx);

    const std::string path = utf8::from_jstring(env, jpath);
    g_state.release();                 // clean old state
    llama_backend_init();

    int phys = count_physical_cores();
    int nthreads = (jthreads > 0) ? static_cast<int>(jthreads) : phys;
    LOG_INFO("Initializing model '%s' (threads=%d, ctx=%d)", path.c_str(), nthreads, ctxSize);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;                // CPU only by default
    mparams.use_mmap = true;
    mparams.use_mlock = false;
    mparams.check_tensors = true;

    g_state.model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_state.model) {
        LOG_ERROR("Failed to load model '%s'", path.c_str());
        g_state.release();
        return JNI_FALSE;
    }

    // Context params (same as before)
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = ctxSize;
    cparams.n_batch = 512;
    cparams.n_ubatch = 256;
    cparams.n_threads = nthreads;
    cparams.n_threads_batch = nthreads;
    cparams.offload_kqv = false;         // CPU only
    cparams.n_seq_max = 1;
    cparams.no_perf = false;

    g_state.ctx = llama_init_from_model(g_state.model, cparams);
    if (!g_state.ctx) {
        LOG_ERROR("Failed to create context");
        g_state.release();
        return JNI_FALSE;
    }

    // persist config into g_state so setters and other flows can read them
    g_state.ctx_size = ctxSize;
    g_state.batch_size = cparams.n_batch;


    g_state.rebuild_sampler(static_cast<int>(topK),
                            topP,
                            temp,
                            minP,
                            mirostat,
                            mirostatTau,
                            mirostatEta,
                            seed);

    g_state.warmup_context();

    // Optional tools – configure grammar chain
    maybe_init_grammar();

    LOG_INFO("Model initialized successfully");
    return JNI_TRUE;
}


/*  --------------------------------------------------------------
 *      JNI: release resources
 *  -------------------------------------------------------------- */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeRelease(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lk(g_init_mtx);
    g_state.release();
    return JNI_TRUE;
}

/*  --------------------------------------------------------------
 *      JNI: configuration setters
 *  -------------------------------------------------------------- */
extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetSystemPrompt(JNIEnv* env, jobject,
                                                     jstring jprompt) {
    g_state.system_prompt = utf8::from_jstring(env, jprompt);
    LOG_INFO("System prompt updated (%zu bytes)", g_state.system_prompt.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetChatTemplate(JNIEnv* env, jobject,
                                                     jstring jtemplate) {
    g_state.chat_template_override = utf8::from_jstring(env, jtemplate);
    LOG_INFO("Chat template override set (%zu bytes)", g_state.chat_template_override.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSetToolsJson(JNIEnv* env, jobject,
                                                  jstring jtools) {
    g_state.tools_json = utf8::from_jstring(env, jtools);
    g_state.tools_enabled = !g_state.tools_json.empty();
    LOG_INFO("Tools JSON set (%zu bytes), enabled=%d",
             g_state.tools_json.size(), static_cast<int>(g_state.tools_enabled));
    maybe_init_grammar();            // re‑build grammar on change
}

/*  --------------------------------------------------------------
 *      JNI: request stop
 *  -------------------------------------------------------------- */
extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeStopGeneration(JNIEnv*, jobject) {
    g_stop_requested.store(true);
    LOG_INFO("Stop generation requested");
}

/*  --------------------------------------------------------------
 *      JNI: clear KV cache (fast reset)
 *  -------------------------------------------------------------- */
extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeClearMemory(JNIEnv*, jobject) {
    if (g_state.ctx) {
        llama_memory_t mem = llama_get_memory(g_state.ctx);
        if (mem) llama_memory_clear(mem, true);
        LOG_INFO("KV cache cleared");
    }
}

/*  --------------------------------------------------------------
 *      JNI: streamable generation
 *  -------------------------------------------------------------- */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGenerateStream(JNIEnv* env, jobject,
                                                    jstring jprompt,
                                                    jint max_tokens,
                                                    jobject jcallback) {
    if (!g_state.is_ready()) {
        jni::on_error(env, jcallback, "Model not initialized");
        return JNI_FALSE;
    }

    // Reset state for a new turn
    g_state.prepare_for_generation();
    g_stop_requested.store(false);

    const std::string user_msg = utf8::from_jstring(env, jprompt);
    const llama_vocab* vocab = llama_model_get_vocab(g_state.model);
    if (!vocab) {
        jni::on_error(env, jcallback, "Failed to get vocab");
        return JNI_FALSE;
    }

    // Build the final prompt + template
    std::string system = g_state.system_prompt;
    if (g_state.tools_enabled) system += "\n" + chat::build_tool_preamble(g_state.tools_json);
    const std::string prompt = chat::apply_template(g_state.model,
                                                    system,
                                                    user_msg,
                                                    g_state.chat_template_override,
                                                    true);

    LOG_INFO("Rendered prompt size=%zu", prompt.size());

    // Tokenise prompt
    std::vector<llama_token> prompt_toks = g_state.tokenize(prompt);
    if (prompt_toks.empty()) {
        jni::on_error(env, jcallback, "Tokenisation failed");
        return JNI_FALSE;
    }

    // Limit generation by context
    int32_t available = g_state.ctx_size - static_cast<int32_t>(prompt_toks.size()) - 8;
    if (available <= 0) {
        jni::on_error(env, jcallback, "Context overflow – shorten your prompt");
        return JNI_TRUE;
    }
    auto to_generate = static_cast<int32_t>(max_tokens > 0 ? max_tokens : 128);
    to_generate = std::min(to_generate, available);

    // Feed prompt first
    if (!g_state.decode_prompt(prompt_toks)) {
        jni::on_error(env, jcallback, "Decoding prompt failed");
        return JNI_TRUE;
    }

    /* ---------------------------------------------------------
     *  Streaming loop – one token at a time
     * -------------------------------------------------------- */
    ToolCallState tool_state;
    llama_token eos = llama_vocab_eos(vocab);
    llama_token eot = llama_vocab_eot(vocab);

    llama_batch single = llama_batch_init(1, 0, 1);

// ✅ Clear UTF-8 buffer at start of generation
    g_state.utf8_carry_buffer.clear();

    for (int i = 0; i < to_generate && !g_stop_requested.load(); ++i) {

        // Sample & accept
        llama_token tok = llama_sampler_sample(g_state.sampler, g_state.ctx, -1);
        llama_sampler_accept(g_state.sampler, tok);

        // Turn EOS into space if first token
        if (i == 0 && (tok == eos || tok == eot)) {
            tok = g_state.space_token();
        }
        if (tok == eos || tok == eot) break;

        // ✅ Use buffered detokenization
        std::string complete_chars = g_state.detokenize_buffered(tok);

        // Only process if we got complete UTF-8 characters
        if (!complete_chars.empty()) {
            // Tool-call detection
            bool complete = false;
            if (g_state.tools_enabled) {
                complete = tool_state.accumulate(complete_chars);
                if (complete) {
                    std::string name, payload;
                    if (tool_state.extract_tool_call(name, payload)) {
                        jni::on_toolcall(env, jcallback, name, payload);
                        break;
                    }
                    tool_state.reset();
                }
            }

            // Emit complete UTF-8 characters
            if (!tool_state.is_collecting()) {
                jni::on_token(env, jcallback, complete_chars);
            }
        }

        // Prepare batch for next token
        single.n_tokens = 1;
        single.token[0] = tok;
        single.pos[0] = static_cast<int32_t>(prompt_toks.size() + i);
        single.n_seq_id[0] = 1;
        single.seq_id[0][0] = 0;
        single.logits[0] = true;

        if (llama_decode(g_state.ctx, single) != 0) {
            jni::on_error(env, jcallback, "llama_decode failed during generation");
            break;
        }

        if (env->ExceptionCheck()) {
            LOG_ERROR("Java exception during callback – aborting");
            env->ExceptionClear();
            break;
        }
    }

    std::string remaining = g_state.flush_utf8_buffer();
    if (!remaining.empty()) {
        jni::on_token(env, jcallback, remaining);
    }

    llama_batch_free(single);
    utf8::flush_carry(env, jcallback);
    jni::on_done(env, jcallback);
    return JNI_TRUE;
}

/*  --------------------------------------------------------------
 *      JNI: kernel‑level diagnostics
 *  -------------------------------------------------------------- */
extern "C" JNIEXPORT void JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_llamaPrintTimings(JNIEnv*, jobject) {
    llama_print_system_info();
    llama_perf_context_print(g_state.ctx);
}

/*  --------------------------------------------------------------
 *      JNI: model information
 *  -------------------------------------------------------------- */

static const char* detect_model_architecture(llama_model* model) {
    if (!model) return "unknown";

    static char arch_buf[128] = {0};
    int32_t arch_len = llama_model_meta_val_str(model, "general.architecture", arch_buf, sizeof(arch_buf));

    if (arch_len > 0) {
        return arch_buf;
    }

    return "unknown";
}

static const char* get_model_name(llama_model* model) {
    if (!model) return "";

    static char name_buf[256] = {0};
    llama_model_meta_val_str(model, "general.name", name_buf, sizeof(name_buf));
    return name_buf;
}

static const char* get_model_description(llama_model* model) {
    if (!model) return "";

    static char desc_buf[512] = {0};
    llama_model_meta_val_str(model, "general.description", desc_buf, sizeof(desc_buf));
    return desc_buf;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGetModelInfo(JNIEnv* env, jobject) {
    if (!g_state.model) return env->NewStringUTF("{}");

    const llama_vocab* vocab = llama_model_get_vocab(g_state.model);
    std::ostringstream json;

    json << "{";

    // Model identity
    const char* arch = detect_model_architecture(g_state.model);
    const char* name = get_model_name(g_state.model);
    const char* desc = get_model_description(g_state.model);

    json << R"("architecture":")" << chat::json_escape(arch) << "\",";
    json << R"("name":")" << chat::json_escape(name) << "\",";
    json << R"("description":")" << chat::json_escape(desc) << "\",";

    // Model dimensions
    json << "\"n_vocab\":" << (vocab ? llama_vocab_n_tokens(vocab) : 0) << ",";
    json << "\"n_ctx_train\":" << llama_model_n_ctx_train(g_state.model) << ",";
    json << "\"n_embd\":" << llama_model_n_embd(g_state.model) << ",";
    json << "\"n_layer\":" << llama_model_n_layer(g_state.model) << ",";
    json << "\"n_head\":" << llama_model_n_head(g_state.model) << ",";
    json << "\"n_head_kv\":" << llama_model_n_head_kv(g_state.model) << ",";

    // Vocabulary tokens
    if (vocab) {
        json << "\"bos\":" << llama_vocab_bos(vocab) << ",";
        json << "\"eos\":" << llama_vocab_eos(vocab) << ",";
        json << "\"eot\":" << llama_vocab_eot(vocab) << ",";
        json << "\"nl\":" << llama_vocab_nl(vocab) << ",";

        // Vocab type
        const char* vocab_type = "unknown";
        switch (llama_vocab_type(vocab)) {
            case LLAMA_VOCAB_TYPE_SPM: vocab_type = "spm"; break;
            case LLAMA_VOCAB_TYPE_BPE: vocab_type = "bpe"; break;
            case LLAMA_VOCAB_TYPE_WPM: vocab_type = "wpm"; break;
            default: vocab_type = "unknown"; break;  // ✅ Handles unhandled cases
        }
        json << R"("vocab_type":")" << vocab_type << "\",";
    }

    // Chat template - just return what the model has
    const char* tmpl = llama_model_chat_template(g_state.model, nullptr);
    json << R"("chat_template":")" << chat::json_escape(tmpl ? tmpl : "") << "\",";

    // System info
    json << R"("system":")" << chat::json_escape(llama_print_system_info()) << "\"";
    json << "}";

    return env->NewStringUTF(json.str().c_str());
}

/*  --------------------------------------------------------------
 *      JNI: persistence helpers
 *  -------------------------------------------------------------- */
extern "C" JNIEXPORT jlong JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGetStateSize(JNIEnv*, jobject) {
    return g_state.get_state_size();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeGetStateData(JNIEnv* env, jobject) {
    jlong sz = g_state.get_state_size();
    if (!sz) return nullptr;

    jbyteArray arr = env->NewByteArray(static_cast<jsize>(sz));
    if (!arr) return nullptr;

    void* buffer = env->GetByteArrayElements(arr, nullptr);
    g_state.get_state_data(buffer, static_cast<size_t>(sz));
    env->ReleaseByteArrayElements(arr, (jbyte*)buffer, 0);
    return arr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeLoadStateData(JNIEnv* env, jobject,
                                                   jbyteArray arr) {
    if (!arr) return JNI_FALSE;
    jbyte* buf = env->GetByteArrayElements(arr, nullptr);
    auto len = static_cast<size_t>(env->GetArrayLength(arr));
    bool ok = g_state.load_state_data(buf, len);
    env->ReleaseByteArrayElements(arr, buf, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeSaveStateFile(JNIEnv* env, jobject,
                                                   jstring jpath) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    bool ok = llama_state_save_file(g_state.ctx, path, nullptr, 0);
    env->ReleaseStringUTFChars(jpath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_ai_1gguf_GGUFNativeLib_nativeLoadStateFile(JNIEnv* env, jobject,
                                                   jstring jpath) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    bool ok = llama_state_load_file(g_state.ctx, path, nullptr, 0, nullptr);
    env->ReleaseStringUTFChars(jpath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

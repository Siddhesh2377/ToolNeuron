#include "jvm_bridge.h"

#include <android/log.h>

#include <atomic>
#include <mutex>

#define LOG_TAG "tn_server_bridge"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace tn::server::jvm {

    namespace {
        JavaVM*     g_vm              = nullptr;
        jobject     g_bridge          = nullptr;
        jclass      g_string_cls      = nullptr;
        jmethodID   g_m_start_gen     = nullptr;
        jmethodID   g_m_cancel        = nullptr;
        jmethodID   g_m_start_embed   = nullptr;
        jmethodID   g_m_start_tts     = nullptr;
        jmethodID   g_m_start_stt     = nullptr;
        jmethodID   g_m_start_imggen  = nullptr;
        jmethodID   g_m_start_upscale = nullptr;
        jmethodID   g_m_req_event     = nullptr;
        std::mutex  g_mu;

        struct ScopedEnv {
            JNIEnv* env = nullptr;
            bool    attached = false;

            ScopedEnv() {
                if (!g_vm) return;
                int st = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
                if (st == JNI_EDETACHED) {
                    if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                        attached = true;
                    } else {
                        env = nullptr;
                    }
                } else if (st != JNI_OK) {
                    env = nullptr;
                }
            }

            ~ScopedEnv() {
                if (attached && g_vm) g_vm->DetachCurrentThread();
            }
        };

        jobjectArray to_string_array(JNIEnv* env, const std::vector<std::string>& items) {
            jobjectArray arr = env->NewObjectArray(
                static_cast<jsize>(items.size()),
                g_string_cls,
                nullptr);
            if (arr == nullptr) return nullptr;
            for (size_t i = 0; i < items.size(); ++i) {
                jstring s = env->NewStringUTF(items[i].c_str());
                env->SetObjectArrayElement(arr, static_cast<jsize>(i), s);
                env->DeleteLocalRef(s);
            }
            return arr;
        }
    }

    void on_vm_load(JavaVM* vm) {
        g_vm = vm;
    }

    void attach_bridge(JNIEnv* env, jobject bridge) {
        std::lock_guard<std::mutex> lock(g_mu);
        if (g_bridge) {
            env->DeleteGlobalRef(g_bridge);
            g_bridge = nullptr;
        }
        if (g_string_cls) {
            env->DeleteGlobalRef(g_string_cls);
            g_string_cls = nullptr;
        }
        g_m_start_gen     = nullptr;
        g_m_cancel        = nullptr;
        g_m_start_embed   = nullptr;
        g_m_start_tts     = nullptr;
        g_m_start_stt     = nullptr;
        g_m_start_imggen  = nullptr;
        g_m_start_upscale = nullptr;
        g_m_req_event     = nullptr;

        if (bridge == nullptr) return;

        g_bridge = env->NewGlobalRef(bridge);
        jclass cls = env->GetObjectClass(g_bridge);
        if (cls == nullptr) {
            LOGE("attach_bridge: GetObjectClass failed");
            env->DeleteGlobalRef(g_bridge);
            g_bridge = nullptr;
            return;
        }

        jclass sCls = env->FindClass("java/lang/String");
        if (sCls != nullptr) {
            g_string_cls = static_cast<jclass>(env->NewGlobalRef(sCls));
            env->DeleteLocalRef(sCls);
        }

        g_m_start_gen     = env->GetMethodID(cls, "startGeneration",
            "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)Z");
        g_m_cancel        = env->GetMethodID(cls, "cancelGeneration", "(J)V");
        g_m_start_embed   = env->GetMethodID(cls, "startEmbedding",
            "(JLjava/lang/String;Ljava/lang/String;)Z");
        g_m_start_tts     = env->GetMethodID(cls, "startTts",
            "(JLjava/lang/String;Ljava/lang/String;IFLjava/lang/String;)Z");
        g_m_start_stt     = env->GetMethodID(cls, "startStt",
            "(JLjava/lang/String;Ljava/lang/String;)Z");
        g_m_start_imggen  = env->GetMethodID(cls, "startImageGen",
            "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z");
        g_m_start_upscale = env->GetMethodID(cls, "startImageUpscale",
            "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z");
        g_m_req_event     = env->GetMethodID(cls, "onRequestEvent", "(Ljava/lang/String;)V");
        if (env->ExceptionCheck()) env->ExceptionClear();

        env->DeleteLocalRef(cls);

        if (!g_m_start_gen || !g_m_cancel) {
            LOGE("attach_bridge: missing core method ids");
            env->DeleteGlobalRef(g_bridge);
            g_bridge = nullptr;
        }
    }

    void detach_bridge(JNIEnv* env) {
        std::lock_guard<std::mutex> lock(g_mu);
        if (g_bridge) {
            env->DeleteGlobalRef(g_bridge);
            g_bridge = nullptr;
        }
        if (g_string_cls) {
            env->DeleteGlobalRef(g_string_cls);
            g_string_cls = nullptr;
        }
        g_m_start_gen     = nullptr;
        g_m_cancel        = nullptr;
        g_m_start_embed   = nullptr;
        g_m_start_tts     = nullptr;
        g_m_start_stt     = nullptr;
        g_m_start_imggen  = nullptr;
        g_m_start_upscale = nullptr;
        g_m_req_event     = nullptr;
    }

    bool has_bridge() {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_bridge != nullptr && g_m_start_gen != nullptr;
    }

    bool start_generation(int64_t gen_id,
                          const std::string& model_id,
                          const std::string& messages_json,
                          const std::string& params_json,
                          const std::vector<std::string>& image_paths) {
        jobject   bridge = nullptr;
        jmethodID m      = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            bridge = g_bridge;
            m      = g_m_start_gen;
        }
        if (!bridge || !m) return false;

        ScopedEnv se;
        if (!se.env) return false;

        jstring jModel    = se.env->NewStringUTF(model_id.c_str());
        jstring jMessages = se.env->NewStringUTF(messages_json.c_str());
        jstring jParams   = se.env->NewStringUTF(params_json.c_str());
        jobjectArray jImages = to_string_array(se.env, image_paths);

        jboolean ok = se.env->CallBooleanMethod(
            bridge, m, static_cast<jlong>(gen_id), jModel, jMessages, jParams, jImages);

        if (se.env->ExceptionCheck()) {
            se.env->ExceptionDescribe();
            se.env->ExceptionClear();
            ok = JNI_FALSE;
        }

        se.env->DeleteLocalRef(jModel);
        se.env->DeleteLocalRef(jMessages);
        se.env->DeleteLocalRef(jParams);
        if (jImages) se.env->DeleteLocalRef(jImages);
        return ok == JNI_TRUE;
    }

    void cancel_generation(int64_t gen_id) {
        jobject   bridge = nullptr;
        jmethodID m      = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            bridge = g_bridge;
            m      = g_m_cancel;
        }
        if (!bridge || !m) return;

        ScopedEnv se;
        if (!se.env) return;

        se.env->CallVoidMethod(bridge, m, static_cast<jlong>(gen_id));
        if (se.env->ExceptionCheck()) {
            se.env->ExceptionDescribe();
            se.env->ExceptionClear();
        }
    }

    bool start_embedding(int64_t reply_id,
                         const std::string& model_id,
                         const std::string& inputs_json) {
        jobject   bridge = nullptr;
        jmethodID m      = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            bridge = g_bridge;
            m      = g_m_start_embed;
        }
        if (!bridge || !m) return false;

        ScopedEnv se;
        if (!se.env) return false;

        jstring jModel = se.env->NewStringUTF(model_id.c_str());
        jstring jIn    = se.env->NewStringUTF(inputs_json.c_str());
        jboolean ok = se.env->CallBooleanMethod(
            bridge, m, static_cast<jlong>(reply_id), jModel, jIn);
        if (se.env->ExceptionCheck()) {
            se.env->ExceptionDescribe();
            se.env->ExceptionClear();
            ok = JNI_FALSE;
        }
        se.env->DeleteLocalRef(jModel);
        se.env->DeleteLocalRef(jIn);
        return ok == JNI_TRUE;
    }

    bool start_tts(int64_t reply_id,
                   const std::string& model_id,
                   const std::string& text,
                   int speaker_id,
                   float speed,
                   const std::string& out_path) {
        jobject   bridge = nullptr;
        jmethodID m      = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            bridge = g_bridge;
            m      = g_m_start_tts;
        }
        if (!bridge || !m) return false;

        ScopedEnv se;
        if (!se.env) return false;

        jstring jModel = se.env->NewStringUTF(model_id.c_str());
        jstring jText  = se.env->NewStringUTF(text.c_str());
        jstring jOut   = se.env->NewStringUTF(out_path.c_str());

        jboolean ok = se.env->CallBooleanMethod(
            bridge, m, static_cast<jlong>(reply_id), jModel, jText,
            static_cast<jint>(speaker_id), static_cast<jfloat>(speed), jOut);
        if (se.env->ExceptionCheck()) {
            se.env->ExceptionDescribe();
            se.env->ExceptionClear();
            ok = JNI_FALSE;
        }
        se.env->DeleteLocalRef(jModel);
        se.env->DeleteLocalRef(jText);
        se.env->DeleteLocalRef(jOut);
        return ok == JNI_TRUE;
    }

    bool start_stt(int64_t reply_id,
                   const std::string& model_id,
                   const std::string& wav_path) {
        jobject   bridge = nullptr;
        jmethodID m      = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            bridge = g_bridge;
            m      = g_m_start_stt;
        }
        if (!bridge || !m) return false;

        ScopedEnv se;
        if (!se.env) return false;

        jstring jModel = se.env->NewStringUTF(model_id.c_str());
        jstring jPath  = se.env->NewStringUTF(wav_path.c_str());
        jboolean ok = se.env->CallBooleanMethod(
            bridge, m, static_cast<jlong>(reply_id), jModel, jPath);
        if (se.env->ExceptionCheck()) {
            se.env->ExceptionDescribe();
            se.env->ExceptionClear();
            ok = JNI_FALSE;
        }
        se.env->DeleteLocalRef(jModel);
        se.env->DeleteLocalRef(jPath);
        return ok == JNI_TRUE;
    }

    bool start_image_gen(int64_t reply_id,
                        const std::string& model_id,
                        const std::string& params_json,
                        const std::string& input_image_path,
                        const std::string& mask_path,
                        const std::string& out_path) {
        jobject   bridge = nullptr;
        jmethodID m      = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            bridge = g_bridge;
            m      = g_m_start_imggen;
        }
        if (!bridge || !m) return false;

        ScopedEnv se;
        if (!se.env) return false;

        jstring jModel = se.env->NewStringUTF(model_id.c_str());
        jstring jParam = se.env->NewStringUTF(params_json.c_str());
        jstring jIn    = se.env->NewStringUTF(input_image_path.c_str());
        jstring jMask  = se.env->NewStringUTF(mask_path.c_str());
        jstring jOut   = se.env->NewStringUTF(out_path.c_str());
        jboolean ok = se.env->CallBooleanMethod(
            bridge, m, static_cast<jlong>(reply_id), jModel, jParam, jIn, jMask, jOut);
        if (se.env->ExceptionCheck()) {
            se.env->ExceptionDescribe();
            se.env->ExceptionClear();
            ok = JNI_FALSE;
        }
        se.env->DeleteLocalRef(jModel);
        se.env->DeleteLocalRef(jParam);
        se.env->DeleteLocalRef(jIn);
        se.env->DeleteLocalRef(jMask);
        se.env->DeleteLocalRef(jOut);
        return ok == JNI_TRUE;
    }

    bool start_image_upscale(int64_t reply_id,
                            const std::string& model_id,
                            const std::string& image_path,
                            const std::string& out_path) {
        jobject   bridge = nullptr;
        jmethodID m      = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            bridge = g_bridge;
            m      = g_m_start_upscale;
        }
        if (!bridge || !m) return false;

        ScopedEnv se;
        if (!se.env) return false;

        jstring jModel = se.env->NewStringUTF(model_id.c_str());
        jstring jIn    = se.env->NewStringUTF(image_path.c_str());
        jstring jOut   = se.env->NewStringUTF(out_path.c_str());
        jboolean ok = se.env->CallBooleanMethod(
            bridge, m, static_cast<jlong>(reply_id), jModel, jIn, jOut);
        if (se.env->ExceptionCheck()) {
            se.env->ExceptionDescribe();
            se.env->ExceptionClear();
            ok = JNI_FALSE;
        }
        se.env->DeleteLocalRef(jModel);
        se.env->DeleteLocalRef(jIn);
        se.env->DeleteLocalRef(jOut);
        return ok == JNI_TRUE;
    }

    void emit_request_event(const std::string& event_json) {
        jobject   bridge = nullptr;
        jmethodID m      = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            bridge = g_bridge;
            m      = g_m_req_event;
        }
        if (!bridge || !m) return;

        ScopedEnv se;
        if (!se.env) return;

        jstring payload = se.env->NewStringUTF(event_json.c_str());
        se.env->CallVoidMethod(bridge, m, payload);
        if (se.env->ExceptionCheck()) {
            se.env->ExceptionDescribe();
            se.env->ExceptionClear();
        }
        se.env->DeleteLocalRef(payload);
    }

}

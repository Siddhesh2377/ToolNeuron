#include "jvm_bridge.h"

#include <android/log.h>

#include <atomic>
#include <mutex>

#define LOG_TAG "tn_server_bridge"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace tn::server::jvm {

    namespace {
        JavaVM*           g_vm          = nullptr;
        jobject           g_bridge      = nullptr;
        jmethodID         g_m_start     = nullptr;
        jmethodID         g_m_cancel    = nullptr;
        jmethodID         g_m_req_event = nullptr;
        std::mutex        g_mu;

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
        g_m_start  = nullptr;
        g_m_cancel = nullptr;

        if (bridge == nullptr) return;

        g_bridge = env->NewGlobalRef(bridge);
        jclass cls = env->GetObjectClass(g_bridge);
        if (cls == nullptr) {
            LOGE("attach_bridge: GetObjectClass failed");
            env->DeleteGlobalRef(g_bridge);
            g_bridge = nullptr;
            return;
        }

        g_m_start     = env->GetMethodID(cls, "startGeneration", "(JLjava/lang/String;Ljava/lang/String;)Z");
        g_m_cancel    = env->GetMethodID(cls, "cancelGeneration", "(J)V");
        g_m_req_event = env->GetMethodID(cls, "onRequestEvent", "(Ljava/lang/String;)V");
        if (env->ExceptionCheck()) env->ExceptionClear();

        env->DeleteLocalRef(cls);

        if (!g_m_start || !g_m_cancel) {
            LOGE("attach_bridge: missing method ids");
            env->DeleteGlobalRef(g_bridge);
            g_bridge      = nullptr;
            g_m_start     = nullptr;
            g_m_cancel    = nullptr;
            g_m_req_event = nullptr;
        }
    }

    void detach_bridge(JNIEnv* env) {
        std::lock_guard<std::mutex> lock(g_mu);
        if (g_bridge) {
            env->DeleteGlobalRef(g_bridge);
            g_bridge = nullptr;
        }
        g_m_start     = nullptr;
        g_m_cancel    = nullptr;
        g_m_req_event = nullptr;
    }

    bool has_bridge() {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_bridge != nullptr && g_m_start != nullptr;
    }

    bool start_generation(int64_t gen_id,
                          const std::string& messages_json,
                          const std::string& params_json) {
        jobject   bridge = nullptr;
        jmethodID m      = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            bridge = g_bridge;
            m      = g_m_start;
        }
        if (!bridge || !m) return false;

        ScopedEnv se;
        if (!se.env) return false;

        jstring jMessages = se.env->NewStringUTF(messages_json.c_str());
        jstring jParams   = se.env->NewStringUTF(params_json.c_str());

        jboolean ok = se.env->CallBooleanMethod(
            bridge, m, static_cast<jlong>(gen_id), jMessages, jParams);

        if (se.env->ExceptionCheck()) {
            se.env->ExceptionDescribe();
            se.env->ExceptionClear();
            ok = JNI_FALSE;
        }

        se.env->DeleteLocalRef(jMessages);
        se.env->DeleteLocalRef(jParams);
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

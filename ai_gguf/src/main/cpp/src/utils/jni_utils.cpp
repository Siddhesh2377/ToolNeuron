// src/utils/jni_utils.cpp
#include "jni_utils.h"
#include "utf8_utils.h"
#include "logger.h"

#include <jni.h>
#include <string>

namespace jni {

/* --------------------------------------------------------------------
 *  Caches the method IDs once and re‑uses them for every call.
 * -------------------------------------------------------------------- */
    static jclass cached_callback_cls  = nullptr;
    static jmethodID mid_onToken      = nullptr;
    static jmethodID mid_onError      = nullptr;
    static jmethodID mid_onTool       = nullptr;
    static jmethodID mid_onDone       = nullptr;

/* --------------------------------------------------------------------
 *  Helper that initialises the above cache.  It is called lazily the
 *  first time any callback is executed.
 * -------------------------------------------------------------------- */
    static void init_cache(JNIEnv* env, jobject callback) {
        if (cached_callback_cls) return;                // already cached

        jclass temp_cls = env->GetObjectClass(callback);
        if (!temp_cls) {
            LOG_ERROR("jni_utils: unable to find callback class");
            return;
        }

        // Keep a global reference so we can reuse it after the method ID lookup
        cached_callback_cls = static_cast<jclass>(env->NewGlobalRef(temp_cls));
        env->DeleteLocalRef(temp_cls);

        mid_onToken  = env->GetMethodID(cached_callback_cls, "onToken",   "(Ljava/lang/String;)V");
        mid_onError  = env->GetMethodID(cached_callback_cls, "onError",   "(Ljava/lang/String;)V");
        mid_onTool   = env->GetMethodID(cached_callback_cls, "onToolCall","(Ljava/lang/String;Ljava/lang/String;)V");
        mid_onDone   = env->GetMethodID(cached_callback_cls, "onDone",    "()V");
    }

/* --------------------------------------------------------------------
 *  on_token – invoke `onToken`.  The `utf8::to_jstring` helper already
 *  appends the replacement character for unfinished UTF‑8 sequences.
 * -------------------------------------------------------------------- */
    void on_token(JNIEnv* env, jobject cb, const std::string& txt) {
        if (!cb || txt.empty()) return;
        init_cache(env, cb);
        jstring jstr = utf8::to_jstring(env, txt, /*carry*/ *new std::string());
        env->CallVoidMethod(cb, mid_onToken, jstr);
        env->DeleteLocalRef(jstr);
    }

/* --------------------------------------------------------------------
 *  on_error – surface an error string.
 * -------------------------------------------------------------------- */
    void on_error(JNIEnv* env, jobject cb, const char* msg) {
        if (!cb) return;
        init_cache(env, cb);
        jstring jmsg = env->NewStringUTF(msg ? msg : "<unknown error>");
        env->CallVoidMethod(cb, mid_onError, jmsg);
        env->DeleteLocalRef(jmsg);
    }

/* --------------------------------------------------------------------
 *  on_toolcall – callback used when a tool‑call JSON has been parsed.
 * -------------------------------------------------------------------- */
    void on_toolcall(JNIEnv* env, jobject cb,
                     const std::string& name,
                     const std::string& payload) {
        if (!cb) return;
        init_cache(env, cb);
        jstring jname    = env->NewStringUTF(name.c_str());
        jstring jpayload = utf8::to_jstring(env, payload, /*carry*/ *new std::string());
        env->CallVoidMethod(cb, mid_onTool, jname, jpayload);
        env->DeleteLocalRef(jname);
        env->DeleteLocalRef(jpayload);
    }

/* --------------------------------------------------------------------
 *  on_done – tell the Java side that the stream generation has finished.
 * -------------------------------------------------------------------- */
    void on_done(JNIEnv* env, jobject cb) {
        if (!cb) return;
        init_cache(env, cb);
        env->CallVoidMethod(cb, mid_onDone);
    }

} // namespace jni
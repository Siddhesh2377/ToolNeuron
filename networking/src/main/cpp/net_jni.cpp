#include <jni.h>
#include <android/log.h>

#include <string>

#include "ddg_client.h"
#include "http_backend.h"

namespace {

constexpr const char* kTag = "networking";

jstring to_jstring(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

void throw_runtime(JNIEnv* env, const std::string& msg) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) env->ThrowNew(cls, msg.c_str());
}

}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dark_networking_WebNative_nativeSearch(
    JNIEnv* env,
    jobject,
    jstring jQuery,
    jstring jUserAgent,
    jint jMaxResults
) {
    const char* q = env->GetStringUTFChars(jQuery, nullptr);
    const char* ua = env->GetStringUTFChars(jUserAgent, nullptr);
    std::string query = q ? q : "";
    std::string user_agent = ua ? ua : "";
    if (q) env->ReleaseStringUTFChars(jQuery, q);
    if (ua) env->ReleaseStringUTFChars(jUserAgent, ua);

    auto outcome = net::ddg::search(query, user_agent, jMaxResults);

    if (!outcome.ok) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "search failed: %s", outcome.error.message.c_str());
        throw_runtime(env, outcome.error.message);
        return nullptr;
    }

    jclass stringCls = env->FindClass("java/lang/String");
    if (stringCls == nullptr) return nullptr;

    const jsize len = static_cast<jsize>(outcome.results.size() * 3);
    jobjectArray arr = env->NewObjectArray(len, stringCls, nullptr);
    if (arr == nullptr) return nullptr;

    jsize i = 0;
    for (const auto& r : outcome.results) {
        env->SetObjectArrayElement(arr, i++, to_jstring(env, r.title));
        env->SetObjectArrayElement(arr, i++, to_jstring(env, r.url));
        env->SetObjectArrayElement(arr, i++, to_jstring(env, r.snippet));
    }
    return arr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dark_networking_WebNative_nativeHasBackend(JNIEnv*, jobject) {
    return net::http_available() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dark_networking_WebNative_nativeBackendName(JNIEnv* env, jobject) {
    return env->NewStringUTF(net::http_backend_name());
}

extern "C" JNIEXPORT void JNICALL
Java_com_dark_networking_WebNative_nativeSetCaBundle(JNIEnv* env, jobject, jstring jPath) {
    const char* p = env->GetStringUTFChars(jPath, nullptr);
    std::string path = p ? p : "";
    if (p) env->ReleaseStringUTFChars(jPath, p);
    net::http_set_ca_bundle(path);
}

extern "C" JNIEXPORT void JNICALL
Java_com_dark_networking_WebNative_nativeSetProfile(JNIEnv* env, jobject, jstring jProfile) {
    const char* p = env->GetStringUTFChars(jProfile, nullptr);
    std::string prof = p ? p : "";
    if (p) env->ReleaseStringUTFChars(jProfile, p);
    net::http_set_impersonate_profile(prof);
}

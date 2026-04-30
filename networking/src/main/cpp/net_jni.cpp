#include <jni.h>
#include <android/log.h>

#include <cstdio>
#include <string>
#include <vector>

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

std::string host_of(const std::string& url) {
    auto scheme_end = url.find("://");
    auto start = (scheme_end == std::string::npos) ? 0 : scheme_end + 3;
    auto end = url.find('/', start);
    return url.substr(start, (end == std::string::npos) ? std::string::npos : end - start);
}

jobjectArray make_response_triple(JNIEnv* env, const std::string& status,
                                  const std::string& body, const std::string& error) {
    jclass stringCls = env->FindClass("java/lang/String");
    if (stringCls == nullptr) return nullptr;
    jobjectArray out = env->NewObjectArray(3, stringCls, nullptr);
    if (out == nullptr) return nullptr;
    env->SetObjectArrayElement(out, 0, env->NewStringUTF(status.c_str()));
    env->SetObjectArrayElement(out, 1, env->NewStringUTF(body.c_str()));
    env->SetObjectArrayElement(out, 2, env->NewStringUTF(error.c_str()));
    return out;
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

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dark_networking_WebNative_nativeFetch(
    JNIEnv* env,
    jobject,
    jstring jUrl,
    jstring jUserAgent,
    jint jTimeoutMs,
    jobjectArray jHeaderKeys,
    jobjectArray jHeaderVals
) {
    const char* u = env->GetStringUTFChars(jUrl, nullptr);
    const char* ua = env->GetStringUTFChars(jUserAgent, nullptr);
    std::string url = u ? u : "";
    std::string user_agent = ua ? ua : "";
    if (u) env->ReleaseStringUTFChars(jUrl, u);
    if (ua) env->ReleaseStringUTFChars(jUserAgent, ua);

    if (url.empty()) {
        return make_response_triple(env, "0", "", "empty url");
    }

    net::HttpRequest req;
    req.url = url;
    req.method = "GET";
    req.headers = {
        {"User-Agent", user_agent},
        {"Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"},
        {"Accept-Language", "en-US,en;q=0.9"},
        {"Sec-Fetch-Dest", "document"},
        {"Sec-Fetch-Mode", "navigate"},
        {"Sec-Fetch-Site", "none"},
        {"Sec-Fetch-User", "?1"},
        {"Upgrade-Insecure-Requests", "1"},
    };

    if (jHeaderKeys != nullptr && jHeaderVals != nullptr) {
        const jsize nk = env->GetArrayLength(jHeaderKeys);
        const jsize nv = env->GetArrayLength(jHeaderVals);
        const jsize count = (nk < nv) ? nk : nv;
        for (jsize i = 0; i < count; ++i) {
            jstring jk = (jstring) env->GetObjectArrayElement(jHeaderKeys, i);
            jstring jv = (jstring) env->GetObjectArrayElement(jHeaderVals, i);
            const char* kc = jk ? env->GetStringUTFChars(jk, nullptr) : nullptr;
            const char* vc = jv ? env->GetStringUTFChars(jv, nullptr) : nullptr;
            std::string ks = kc ? kc : "";
            std::string vs = vc ? vc : "";
            if (kc) env->ReleaseStringUTFChars(jk, kc);
            if (vc) env->ReleaseStringUTFChars(jv, vc);
            if (jk) env->DeleteLocalRef(jk);
            if (jv) env->DeleteLocalRef(jv);
            if (ks.empty()) continue;
            bool replaced = false;
            for (auto& h : req.headers) {
                if (h.first == ks) { h.second = vs; replaced = true; break; }
            }
            if (!replaced) req.headers.emplace_back(ks, vs);
        }
    }

    req.timeout_ms = jTimeoutMs > 0 ? jTimeoutMs : 15000;
    req.follow_redirects = true;

    auto resp = net::http_execute(req);
    if (!resp.has_value()) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "fetch transport-fail host=%s",
                            host_of(url).c_str());
        return make_response_triple(env, "0", "", "transport");
    }

    char status_buf[16];
    std::snprintf(status_buf, sizeof(status_buf), "%d", resp->status);

    if (!resp->error.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "fetch failed host=%s status=%d",
                            host_of(url).c_str(), resp->status);
        return make_response_triple(env, status_buf, resp->body, resp->error);
    }

    if (resp->status < 200 || resp->status >= 400) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "fetch http %d host=%s",
                            resp->status, host_of(url).c_str());
    }

    return make_response_triple(env, status_buf, resp->body, "");
}

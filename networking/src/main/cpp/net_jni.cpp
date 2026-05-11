#include <jni.h>
#include <android/log.h>

#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

#include "ddg_client.h"
#include "http_backend.h"

namespace {

constexpr const char* kTag = "networking";

jstring make_jstring(JNIEnv* env, const std::string& s) {
    std::vector<jchar> u16;
    u16.reserve(s.size());

    auto put_replacement = [&]() { u16.push_back(static_cast<jchar>(0xFFFD)); };

    const size_t n = s.size();
    size_t i = 0;
    while (i < n) {
        uint8_t b0 = static_cast<uint8_t>(s[i]);
        uint32_t cp = 0;
        size_t consumed = 1;

        if (b0 < 0x80) {
            cp = b0;
        } else if ((b0 & 0xE0) == 0xC0) {
            if (i + 1 >= n) { put_replacement(); break; }
            uint8_t b1 = static_cast<uint8_t>(s[i + 1]);
            if ((b1 & 0xC0) != 0x80) { put_replacement(); i++; continue; }
            cp = ((b0 & 0x1Fu) << 6) | (b1 & 0x3Fu);
            if (cp < 0x80) { put_replacement(); i++; continue; }
            consumed = 2;
        } else if ((b0 & 0xF0) == 0xE0) {
            if (i + 2 >= n) { put_replacement(); break; }
            uint8_t b1 = static_cast<uint8_t>(s[i + 1]);
            uint8_t b2 = static_cast<uint8_t>(s[i + 2]);
            if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80) { put_replacement(); i++; continue; }
            cp = ((b0 & 0x0Fu) << 12) | ((b1 & 0x3Fu) << 6) | (b2 & 0x3Fu);
            if (cp < 0x800 || (cp >= 0xD800 && cp <= 0xDFFF)) { put_replacement(); i++; continue; }
            consumed = 3;
        } else if ((b0 & 0xF8) == 0xF0) {
            if (i + 3 >= n) { put_replacement(); break; }
            uint8_t b1 = static_cast<uint8_t>(s[i + 1]);
            uint8_t b2 = static_cast<uint8_t>(s[i + 2]);
            uint8_t b3 = static_cast<uint8_t>(s[i + 3]);
            if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) {
                put_replacement(); i++; continue;
            }
            cp = ((b0 & 0x07u) << 18) | ((b1 & 0x3Fu) << 12) | ((b2 & 0x3Fu) << 6) | (b3 & 0x3Fu);
            if (cp < 0x10000 || cp > 0x10FFFF) { put_replacement(); i++; continue; }
            consumed = 4;
        } else {
            put_replacement(); i++; continue;
        }

        if (cp == 0) {
            put_replacement();
        } else if (cp < 0x10000) {
            u16.push_back(static_cast<jchar>(cp));
        } else {
            uint32_t v = cp - 0x10000;
            u16.push_back(static_cast<jchar>(0xD800u | (v >> 10)));
            u16.push_back(static_cast<jchar>(0xDC00u | (v & 0x3FFu)));
        }
        i += consumed;
    }

    return env->NewString(u16.data(), static_cast<jsize>(u16.size()));
}

jstring to_jstring(JNIEnv* env, const std::string& s) {
    return make_jstring(env, s);
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
    env->SetObjectArrayElement(out, 0, make_jstring(env, status));
    env->SetObjectArrayElement(out, 1, make_jstring(env, body));
    env->SetObjectArrayElement(out, 2, make_jstring(env, error));
    return out;
}

}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dark_networking_WebNative_nativeSearch(
    JNIEnv* env,
    jobject,
    jstring jQuery,
    jstring jUserAgent,
    jint jMaxResults,
    jstring jLocale
) {
    const char* q = env->GetStringUTFChars(jQuery, nullptr);
    const char* ua = env->GetStringUTFChars(jUserAgent, nullptr);
    const char* lc = jLocale ? env->GetStringUTFChars(jLocale, nullptr) : nullptr;
    std::string query = q ? q : "";
    std::string user_agent = ua ? ua : "";
    std::string locale = lc ? lc : "";
    if (q) env->ReleaseStringUTFChars(jQuery, q);
    if (ua) env->ReleaseStringUTFChars(jUserAgent, ua);
    if (lc) env->ReleaseStringUTFChars(jLocale, lc);

    auto outcome = net::ddg::search(query, user_agent, jMaxResults, locale);

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

namespace {

bool ascii_iequals(const std::string& a, const std::string& b) {
    if (a.size() != b.size()) return false;
    for (size_t i = 0; i < a.size(); ++i) {
        char ca = a[i]; char cb = b[i];
        if (ca >= 'A' && ca <= 'Z') ca = (char)(ca + 32);
        if (cb >= 'A' && cb <= 'Z') cb = (char)(cb + 32);
        if (ca != cb) return false;
    }
    return true;
}

std::string find_header(const std::vector<net::Header>& headers, const std::string& name) {
    for (const auto& h : headers) {
        if (ascii_iequals(h.first, name)) return h.second;
    }
    return "";
}

}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dark_networking_WebNative_nativeFetchBytes(
    JNIEnv* env,
    jobject,
    jstring jUrl,
    jstring jUserAgent,
    jint jTimeoutMs,
    jobjectArray jHeaderKeys,
    jobjectArray jHeaderVals
) {
    jclass objCls = env->FindClass("java/lang/Object");
    if (objCls == nullptr) return nullptr;

    auto build_result = [&](const std::string& status,
                             const std::string& body_bytes,
                             const std::string& error,
                             const std::string& content_type) -> jobjectArray {
        jobjectArray out = env->NewObjectArray(4, objCls, nullptr);
        if (out == nullptr) return nullptr;
        env->SetObjectArrayElement(out, 0, make_jstring(env, status));
        jbyteArray bodyArr = env->NewByteArray(static_cast<jsize>(body_bytes.size()));
        if (bodyArr == nullptr) return nullptr;
        if (!body_bytes.empty()) {
            env->SetByteArrayRegion(
                bodyArr, 0, static_cast<jsize>(body_bytes.size()),
                reinterpret_cast<const jbyte*>(body_bytes.data()));
        }
        env->SetObjectArrayElement(out, 1, bodyArr);
        env->SetObjectArrayElement(out, 2, make_jstring(env, error));
        env->SetObjectArrayElement(out, 3, make_jstring(env, content_type));
        return out;
    };

    const char* u = env->GetStringUTFChars(jUrl, nullptr);
    const char* ua = env->GetStringUTFChars(jUserAgent, nullptr);
    std::string url = u ? u : "";
    std::string user_agent = ua ? ua : "";
    if (u) env->ReleaseStringUTFChars(jUrl, u);
    if (ua) env->ReleaseStringUTFChars(jUserAgent, ua);

    if (url.empty()) {
        return build_result("0", "", "empty url", "");
    }

    net::HttpRequest req;
    req.url = url;
    req.method = "GET";
    req.headers = {
        {"User-Agent", user_agent},
        {"Accept", "*/*"},
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

    req.timeout_ms = jTimeoutMs > 0 ? jTimeoutMs : 30000;
    req.follow_redirects = true;

    auto resp = net::http_execute(req);
    if (!resp.has_value()) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "fetchBytes transport-fail host=%s",
                            host_of(url).c_str());
        return build_result("0", "", "transport", "");
    }

    char status_buf[16];
    std::snprintf(status_buf, sizeof(status_buf), "%d", resp->status);

    std::string content_type = find_header(resp->headers, "Content-Type");

    if (!resp->error.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "fetchBytes failed host=%s status=%d",
                            host_of(url).c_str(), resp->status);
        return build_result(status_buf, resp->body, resp->error, content_type);
    }

    if (resp->status < 200 || resp->status >= 400) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "fetchBytes http %d host=%s",
                            resp->status, host_of(url).c_str());
    }

    return build_result(status_buf, resp->body, "", content_type);
}

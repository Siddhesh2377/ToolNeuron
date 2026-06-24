#include <jni.h>
#include <string>
#include <vector>

#include "gen_session.h"
#include "jvm_bridge.h"
#include "reply_session.h"
#include "server_audit.h"
#include "server_auth.h"
#include "server_core.h"
#include "server_crypto.h"
#include "server_docs.h"
#include "server_models.h"
#include "server_rate_limit.h"
#include "server_staging.h"
#include "server_webui.h"

namespace {

    std::string jstringToStd(JNIEnv* env, jstring value) {
        if (value == nullptr) return {};
        const char* raw = env->GetStringUTFChars(value, nullptr);
        if (raw == nullptr) return {};
        std::string out(raw);
        env->ReleaseStringUTFChars(value, raw);
        return out;
    }

}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_dark_native_1server_NativeServer_nativeStart(
        JNIEnv* env, jobject, jstring host, jint port) {
    std::string h = jstringToStd(env, host);
    bool ok = tn::server::core().start(h, static_cast<int>(port));
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeStop(
        JNIEnv*, jobject) {
    tn::server::core().stop();
}

JNIEXPORT jboolean JNICALL
Java_com_dark_native_1server_NativeServer_nativeIsRunning(
        JNIEnv*, jobject) {
    return tn::server::core().isRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_dark_native_1server_NativeServer_nativeBoundPort(
        JNIEnv*, jobject) {
    return static_cast<jint>(tn::server::core().boundPort());
}

JNIEXPORT jstring JNICALL
Java_com_dark_native_1server_NativeServer_nativeGenerateToken(
        JNIEnv* env, jobject) {
    constexpr size_t kRawLen = 32;
    std::vector<uint8_t> raw = tn::server::crypto::random_bytes(kRawLen);
    if (raw.size() != kRawLen) return env->NewStringUTF("");
    std::string token = "tn_sk_" + tn::server::crypto::to_base64url(raw);
    tn::server::crypto::secure_zero(raw.data(), raw.size());
    return env->NewStringUTF(token.c_str());
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeSetToken(
        JNIEnv* env, jobject, jstring token) {
    std::string t = jstringToStd(env, token);
    if (t.empty()) {
        tn::server::auth::clear_token();
        return;
    }
    tn::server::auth::set_token(
        reinterpret_cast<const uint8_t*>(t.data()),
        t.size());
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeClearToken(
        JNIEnv*, jobject) {
    tn::server::auth::clear_token();
}

JNIEXPORT jboolean JNICALL
Java_com_dark_native_1server_NativeServer_nativeHasToken(
        JNIEnv*, jobject) {
    return tn::server::auth::has_token() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeSetModelsCatalog(
        JNIEnv* env, jobject, jstring dataArrayJson) {
    std::string payload = jstringToStd(env, dataArrayJson);
    tn::server::models::set_catalog_json(payload);
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeClearModelsCatalog(
        JNIEnv*, jobject) {
    tn::server::models::clear_catalog();
}

JNIEXPORT jstring JNICALL
Java_com_dark_native_1server_NativeServer_nativeModelsCatalogJson(
        JNIEnv* env, jobject) {
    std::string payload = tn::server::models::build_list_response();
    return env->NewStringUTF(payload.c_str());
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeAttachBridge(
        JNIEnv* env, jobject, jobject bridge) {
    tn::server::jvm::attach_bridge(env, bridge);
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeDetachBridge(
        JNIEnv* env, jobject) {
    tn::server::jvm::detach_bridge(env);
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeFeedToken(
        JNIEnv* env, jobject, jlong genId, jstring token) {
    auto session = tn::server::gen::registry().get(static_cast<int64_t>(genId));
    if (!session) return;
    std::string t = jstringToStd(env, token);
    session->push_token(std::move(t));
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeFeedDone(
        JNIEnv* env, jobject, jlong genId, jstring finishReason) {
    auto session = tn::server::gen::registry().get(static_cast<int64_t>(genId));
    if (!session) return;
    std::string r = jstringToStd(env, finishReason);
    session->push_done(std::move(r));
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeFeedError(
        JNIEnv* env, jobject, jlong genId, jstring message) {
    auto session = tn::server::gen::registry().get(static_cast<int64_t>(genId));
    if (!session) return;
    std::string m = jstringToStd(env, message);
    session->push_error(std::move(m));
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeFeedReplyText(
        JNIEnv* env, jobject, jlong replyId, jstring body, jstring mime) {
    auto session = tn::server::reply::registry().get(static_cast<int64_t>(replyId));
    if (!session) return;
    std::string b = jstringToStd(env, body);
    std::string m = mime ? jstringToStd(env, mime) : std::string("application/json");
    session->push_text(std::move(b), std::move(m));
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeFeedReplyBinary(
        JNIEnv* env, jobject, jlong replyId, jstring path, jstring mime) {
    auto session = tn::server::reply::registry().get(static_cast<int64_t>(replyId));
    if (!session) return;
    std::string p = jstringToStd(env, path);
    std::string m = jstringToStd(env, mime);
    session->push_binary_path(std::move(p), std::move(m));
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeFeedReplyError(
        JNIEnv* env, jobject, jlong replyId, jstring message) {
    auto session = tn::server::reply::registry().get(static_cast<int64_t>(replyId));
    if (!session) return;
    std::string m = jstringToStd(env, message);
    session->push_error(std::move(m));
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeSetStagingDir(
        JNIEnv* env, jobject, jstring path) {
    std::string p = jstringToStd(env, path);
    tn::server::staging::set_dir(p);
    tn::server::staging::purge_all();
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativePurgeStaging(
        JNIEnv*, jobject) {
    tn::server::staging::purge_all();
}

JNIEXPORT jstring JNICALL
Java_com_dark_native_1server_NativeServer_nativeRecentRequestsJson(
        JNIEnv* env, jobject, jint max) {
    std::string payload = tn::server::audit::recent_json(static_cast<int>(max));
    return env->NewStringUTF(payload.c_str());
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeClearAuditLog(
        JNIEnv*, jobject) {
    tn::server::audit::clear();
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeConfigureRateLimit(
        JNIEnv*, jobject,
        jdouble capacity, jdouble refillRps,
        jint authFailThreshold, jlong banDurationMs) {
    tn::server::rl::Config cfg;
    cfg.capacity             = static_cast<double>(capacity);
    cfg.refill_rps           = static_cast<double>(refillRps);
    cfg.auth_fail_threshold  = static_cast<int>(authFailThreshold);
    cfg.ban_duration_ms      = static_cast<long long>(banDurationMs);
    tn::server::rl::configure(cfg);
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeResetRateLimit(
        JNIEnv*, jobject) {
    tn::server::rl::reset_all();
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeSetWebUiHtml(
        JNIEnv* env, jobject, jstring html) {
    std::string h = jstringToStd(env, html);
    if (h.empty()) tn::server::webui::clear_html();
    else tn::server::webui::set_html(h);
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeSetWebUiCss(
        JNIEnv* env, jobject, jstring css) {
    std::string c = jstringToStd(env, css);
    if (c.empty()) tn::server::webui::clear_css();
    else tn::server::webui::set_css(c);
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeClearWebUi(
        JNIEnv*, jobject) {
    tn::server::webui::clear_html();
    tn::server::webui::clear_css();
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeSetDocsHtml(
        JNIEnv* env, jobject, jstring html) {
    std::string h = jstringToStd(env, html);
    if (h.empty()) tn::server::docs::clear_html();
    else tn::server::docs::set_html(h);
}

JNIEXPORT void JNICALL
Java_com_dark_native_1server_NativeServer_nativeClearDocs(
        JNIEnv*, jobject) {
    tn::server::docs::clear_html();
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    tn::server::jvm::on_vm_load(vm);
    return JNI_VERSION_1_6;
}

}

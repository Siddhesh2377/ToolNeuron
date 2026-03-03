#include <jni.h>
#include <cstring>
#include <algorithm>

#include "crypto_engine.h"
#include "memory_guard.h"

static tn::CryptoEngine g_engine;

struct JniBytes {
    JNIEnv* env;
    jbyteArray arr;
    jbyte* ptr;
    jsize len;

    JniBytes(JNIEnv* e, jbyteArray a) : env(e), arr(a), ptr(nullptr), len(0) {
        if (a) {
            len = env->GetArrayLength(a);
            ptr = env->GetByteArrayElements(a, nullptr);
        }
    }

    ~JniBytes() {
        if (ptr && arr) {
            env->ReleaseByteArrayElements(arr, ptr, JNI_ABORT);
        }
    }

    const uint8_t* data() const { return reinterpret_cast<const uint8_t*>(ptr); }
    size_t size() const { return static_cast<size_t>(len); }
};

static jbyteArray to_jbyteArray(JNIEnv* env, const uint8_t* data, size_t len) {
    jbyteArray result = env->NewByteArray(static_cast<jint>(len));
    if (result) {
        env->SetByteArrayRegion(result, 0, static_cast<jint>(len),
                                reinterpret_cast<const jbyte*>(data));
    }
    return result;
}

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_dark_system_1encryptor_SystemEncryptor_nativeEncrypt(
    JNIEnv* env, jobject, jbyteArray plaintext, jbyteArray key
) {
    JniBytes pt(env, plaintext);
    JniBytes k(env, key);

    if (!pt.ptr || !k.ptr || k.size() != tn::AES_KEY_SIZE) return nullptr;

    auto result = g_engine.encrypt_aes_gcm(pt.data(), pt.size(), k.data(), k.size(), nullptr, 0);
    if (!result.success) return nullptr;

    return to_jbyteArray(env, result.sealed_data.data(), result.sealed_data.size());
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_system_1encryptor_SystemEncryptor_nativeDecrypt(
    JNIEnv* env, jobject, jbyteArray sealed_data, jbyteArray key
) {
    JniBytes sd(env, sealed_data);
    JniBytes k(env, key);

    if (!sd.ptr || !k.ptr || k.size() != tn::AES_KEY_SIZE) return nullptr;

    auto result = g_engine.decrypt_aes_gcm(sd.data(), sd.size(), k.data(), k.size(), nullptr, 0);
    if (!result.success) return nullptr;

    return to_jbyteArray(env, result.plaintext.data(), result.plaintext.size());
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_system_1encryptor_SystemEncryptor_nativeDeriveKey(
    JNIEnv* env, jobject, jbyteArray master_key, jstring context
) {
    JniBytes mk(env, master_key);
    if (!mk.ptr || !context) return nullptr;

    const char* ctx_str = env->GetStringUTFChars(context, nullptr);
    if (!ctx_str) return nullptr;
    size_t ctx_len = std::strlen(ctx_str);

    uint8_t derived[tn::AES_KEY_SIZE];
    bool ok = g_engine.hkdf_sha256(
        mk.data(), mk.size(),
        nullptr, 0,
        reinterpret_cast<const uint8_t*>(ctx_str), ctx_len,
        derived, tn::AES_KEY_SIZE
    );

    env->ReleaseStringUTFChars(context, ctx_str);

    if (!ok) return nullptr;

    jbyteArray result = to_jbyteArray(env, derived, tn::AES_KEY_SIZE);
    tn::secure_zero(derived, tn::AES_KEY_SIZE);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_system_1encryptor_SystemEncryptor_nativeRandomBytes(
    JNIEnv* env, jobject, jint size
) {
    if (size <= 0) return nullptr;

    auto len = static_cast<size_t>(size);
    uint8_t* buf = new(std::nothrow) uint8_t[len];
    if (!buf) return nullptr;

    bool ok = g_engine.random_bytes(buf, len);
    jbyteArray result = ok ? to_jbyteArray(env, buf, len) : nullptr;

    tn::secure_zero(buf, len);
    delete[] buf;
    return result;
}

JNIEXPORT void JNICALL
Java_com_dark_system_1encryptor_SystemEncryptor_nativeSecureWipe(
    JNIEnv* env, jobject, jbyteArray data
) {
    if (!data) return;
    jsize len = env->GetArrayLength(data);
    jbyte* ptr = env->GetByteArrayElements(data, nullptr);
    if (ptr) {
        tn::secure_zero(ptr, static_cast<size_t>(len));
        env->ReleaseByteArrayElements(data, ptr, 0);
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_system_1encryptor_SystemEncryptor_nativePbkdf2(
    JNIEnv* env, jobject,
    jbyteArray password, jbyteArray salt, jint iterations, jint keyLength
) {
    JniBytes pw(env, password);
    JniBytes s(env, salt);

    uint8_t derived[64]; // max 64 bytes
    size_t len = std::min(static_cast<size_t>(keyLength), sizeof(derived));

    bool ok = g_engine.pbkdf2_sha256(
        pw.data(), pw.size(),
        s.data(), s.size(),
        static_cast<uint32_t>(iterations),
        derived, len
    );

    jbyteArray result = ok ? to_jbyteArray(env, derived, len) : nullptr;
    tn::secure_zero(derived, sizeof(derived));
    return result;
}

} // extern "C"

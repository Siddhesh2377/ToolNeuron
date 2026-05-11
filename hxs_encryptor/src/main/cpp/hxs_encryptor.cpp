#include <jni.h>
#include <cstring>
#include <algorithm>
#include <string>
#include <vector>

#include "crypto_engine.h"
#include "memory_guard.h"
#include "integrity.h"
#include "pq_kem.h"
#include "pq_sign.h"
#include "policy_engine.h"
#include "auth.h"
#include "boot_integrity.h"

static hxs::CryptoEngine g_engine;

// RAII wrapper for JNI byte arrays
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

// ── Encrypt (auto cipher selection) ──

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeEncrypt(
    JNIEnv* env, jobject, jbyteArray plaintext, jbyteArray key, jbyteArray aad
) {
    JniBytes pt(env, plaintext);
    JniBytes k(env, key);
    JniBytes a(env, aad);

    if (!pt.ptr || !k.ptr || k.size() != hxs::KEY_SIZE) return nullptr;

    auto result = g_engine.encrypt(
        pt.data(), pt.size(), k.data(), k.size(),
        a.ptr ? a.data() : nullptr, a.ptr ? a.size() : 0
    );
    if (!result.success) return nullptr;

    return to_jbyteArray(env, result.sealed_data.data(), result.sealed_data.size());
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeDecrypt(
    JNIEnv* env, jobject, jbyteArray sealed_data, jbyteArray key, jbyteArray aad
) {
    JniBytes sd(env, sealed_data);
    JniBytes k(env, key);
    JniBytes a(env, aad);

    if (!sd.ptr || !k.ptr || k.size() != hxs::KEY_SIZE) return nullptr;

    auto result = g_engine.decrypt(
        sd.data(), sd.size(), k.data(), k.size(),
        a.ptr ? a.data() : nullptr, a.ptr ? a.size() : 0
    );
    if (!result.success) return nullptr;

    return to_jbyteArray(env, result.plaintext.data(), result.plaintext.size());
}

// ── HKDF-SHA256 ──

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeDeriveKey(
    JNIEnv* env, jobject, jbyteArray ikm, jbyteArray salt, jstring info
) {
    JniBytes i(env, ikm);
    JniBytes s(env, salt);
    if (!i.ptr) return nullptr;

    const char* info_str = info ? env->GetStringUTFChars(info, nullptr) : nullptr;
    size_t info_len = info_str ? strlen(info_str) : 0;

    uint8_t derived[hxs::KEY_SIZE];
    bool ok = g_engine.hkdf_sha256(
        i.data(), i.size(),
        s.ptr ? s.data() : nullptr, s.ptr ? s.size() : 0,
        reinterpret_cast<const uint8_t*>(info_str), info_len,
        derived, hxs::KEY_SIZE
    );

    if (info_str) env->ReleaseStringUTFChars(info, info_str);
    if (!ok) return nullptr;

    jbyteArray result = to_jbyteArray(env, derived, hxs::KEY_SIZE);
    hxs::secure_zero(derived, hxs::KEY_SIZE);
    return result;
}

// ── Argon2id ──

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeArgon2id(
    JNIEnv* env, jobject,
    jbyteArray password, jbyteArray salt_arr,
    jint t_cost, jint m_cost, jint parallelism, jint hash_len
) {
    JniBytes pw(env, password);
    JniBytes s(env, salt_arr);

    if (!pw.ptr || !s.ptr) return nullptr;
    if (m_cost > 262144) m_cost = 262144;

    size_t out_len = std::min(static_cast<size_t>(hash_len), static_cast<size_t>(64));
    uint8_t output[64];

    bool ok = g_engine.argon2id(
        pw.data(), pw.size(),
        s.data(), s.size(),
        static_cast<uint32_t>(t_cost),
        static_cast<uint32_t>(m_cost),
        static_cast<uint32_t>(parallelism),
        output, out_len
    );

    jbyteArray result = ok ? to_jbyteArray(env, output, out_len) : nullptr;
    hxs::secure_zero(output, sizeof(output));
    return result;
}

// ── PBKDF2-SHA256 (fallback) ──

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativePbkdf2(
    JNIEnv* env, jobject,
    jbyteArray password, jbyteArray salt_arr, jint iterations, jint keyLength
) {
    JniBytes pw(env, password);
    JniBytes s(env, salt_arr);

    uint8_t derived[64];
    size_t len = std::min(static_cast<size_t>(keyLength), sizeof(derived));

    bool ok = g_engine.pbkdf2_sha256(
        pw.data(), pw.size(), s.data(), s.size(),
        static_cast<uint32_t>(iterations), derived, len
    );

    jbyteArray result = ok ? to_jbyteArray(env, derived, len) : nullptr;
    hxs::secure_zero(derived, sizeof(derived));
    return result;
}

// ── Random bytes ──

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeRandomBytes(
    JNIEnv* env, jobject, jint size
) {
    if (size <= 0) return nullptr;
    auto len = static_cast<size_t>(size);
    auto* buf = new(std::nothrow) uint8_t[len];
    if (!buf) return nullptr;

    bool ok = g_engine.random_bytes(buf, len);
    jbyteArray result = ok ? to_jbyteArray(env, buf, len) : nullptr;
    hxs::secure_zero(buf, len);
    delete[] buf;
    return result;
}

// ── SHA-256 ──

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeSha256(
    JNIEnv* env, jobject, jbyteArray data
) {
    JniBytes d(env, data);
    if (!d.ptr) return nullptr;

    uint8_t hash[32];
    g_engine.sha256(d.data(), d.size(), hash);
    return to_jbyteArray(env, hash, 32);
}

// ── Secure wipe ──

JNIEXPORT void JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeSecureWipe(
    JNIEnv* env, jobject, jbyteArray data
) {
    if (!data) return;
    jsize len = env->GetArrayLength(data);
    jbyte* ptr = env->GetByteArrayElements(data, nullptr);
    if (ptr) {
        hxs::secure_zero(ptr, static_cast<size_t>(len));
        env->ReleaseByteArrayElements(data, ptr, 0); // mode 0 = copy back
    }
}

// ── Cipher detection ──

JNIEXPORT jint JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeDetectCipher(
    JNIEnv*, jobject
) {
    return static_cast<jint>(g_engine.detect_optimal_cipher());
}

// ── Integrity checks ──

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeIsDebuggerAttached(
    JNIEnv*, jobject
) {
    return static_cast<jboolean>(hxs::IntegrityGuard::is_debugger_attached());
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeBlockDebugger(
    JNIEnv*, jobject
) {
    return static_cast<jboolean>(hxs::IntegrityGuard::block_debugger());
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeIsFridaPresent(
    JNIEnv*, jobject
) {
    return static_cast<jboolean>(hxs::IntegrityGuard::is_frida_present());
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeIsXposedPresent(
    JNIEnv*, jobject
) {
    return static_cast<jboolean>(hxs::IntegrityGuard::is_xposed_present());
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeVerifyApkSignature(
    JNIEnv* env, jobject, jbyteArray expected, jbyteArray actual
) {
    JniBytes e(env, expected);
    JniBytes a(env, actual);
    if (!e.ptr || !a.ptr) return JNI_FALSE;
    return static_cast<jboolean>(
        hxs::IntegrityGuard::verify_apk_signature(e.data(), e.size(), a.data(), a.size())
    );
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeHashFile(
    JNIEnv* env, jobject, jstring path
) {
    if (!path) return nullptr;
    const char* p = env->GetStringUTFChars(path, nullptr);
    if (!p) return nullptr;

    uint8_t hash[32];
    bool ok = hxs::IntegrityGuard::hash_file(p, hash);
    env->ReleaseStringUTFChars(path, p);

    return ok ? to_jbyteArray(env, hash, 32) : nullptr;
}

// ── Hybrid KEM (X25519 + ML-KEM-768) ──

JNIEXPORT jobjectArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeHybridKemKeygen(
    JNIEnv* env, jobject
) {
    auto kp = hxs::HybridKem::keygen();
    if (!kp.success) return nullptr;

    // Return [public_key, secret_key] as byte array pair
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray(2, byteArrayClass, nullptr);

    env->SetObjectArrayElement(result, 0,
        to_jbyteArray(env, kp.public_key.data(), kp.public_key.size()));
    env->SetObjectArrayElement(result, 1,
        to_jbyteArray(env, kp.secret_key.data(), kp.secret_key.size()));

    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeHybridKemEncaps(
    JNIEnv* env, jobject, jbyteArray public_key
) {
    JniBytes pk(env, public_key);
    if (!pk.ptr) return nullptr;

    auto r = hxs::HybridKem::encaps(pk.data(), pk.size());
    if (!r.success) return nullptr;

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray(2, byteArrayClass, nullptr);
    env->SetObjectArrayElement(result, 0,
        to_jbyteArray(env, r.ciphertext.data(), r.ciphertext.size()));
    env->SetObjectArrayElement(result, 1,
        to_jbyteArray(env, r.shared_secret.data(), r.shared_secret.size()));

    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeHybridKemDecaps(
    JNIEnv* env, jobject, jbyteArray ciphertext, jbyteArray secret_key
) {
    JniBytes ct(env, ciphertext);
    JniBytes sk(env, secret_key);
    if (!ct.ptr || !sk.ptr) return nullptr;

    auto r = hxs::HybridKem::decaps(ct.data(), ct.size(), sk.data(), sk.size());
    if (!r.success) return nullptr;

    return to_jbyteArray(env, r.shared_secret.data(), r.shared_secret.size());
}

// ── Hybrid Signatures (Ed25519 + ML-DSA-65) ──

JNIEXPORT jobjectArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeHybridSignKeygen(
    JNIEnv* env, jobject
) {
    auto kp = hxs::HybridSign::keygen();
    if (!kp.success) return nullptr;

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray(2, byteArrayClass, nullptr);
    env->SetObjectArrayElement(result, 0,
        to_jbyteArray(env, kp.public_key.data(), kp.public_key.size()));
    env->SetObjectArrayElement(result, 1,
        to_jbyteArray(env, kp.secret_key.data(), kp.secret_key.size()));

    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeHybridSign(
    JNIEnv* env, jobject, jbyteArray message, jbyteArray secret_key
) {
    JniBytes msg(env, message);
    JniBytes sk(env, secret_key);
    if (!msg.ptr || !sk.ptr) return nullptr;

    auto r = hxs::HybridSign::sign(msg.data(), msg.size(), sk.data(), sk.size());
    if (!r.success) return nullptr;

    return to_jbyteArray(env, r.signature.data(), r.signature.size());
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_HxsEncryptor_nativeHybridVerify(
    JNIEnv* env, jobject, jbyteArray message, jbyteArray signature, jbyteArray public_key
) {
    JniBytes msg(env, message);
    JniBytes sig(env, signature);
    JniBytes pk(env, public_key);
    if (!msg.ptr || !sig.ptr || !pk.ptr) return JNI_FALSE;

    return static_cast<jboolean>(
        hxs::HybridSign::verify(msg.data(), msg.size(), sig.data(), sig.size(), pk.data(), pk.size())
    );
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_PolicyEngine_nativeIsAllowed(
    JNIEnv* env, jclass, jint feature_id, jbyteArray token
) {
    JniBytes t(env, token);
    return static_cast<jboolean>(
        hxs::policy::is_allowed(
            static_cast<uint32_t>(feature_id),
            t.ptr ? t.data() : nullptr,
            t.ptr ? t.size() : 0
        )
    );
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_1encryptor_PolicyEngine_nativeSetPassthrough(
    JNIEnv*, jclass, jboolean passthrough
) {
    hxs::policy::set_passthrough(static_cast<bool>(passthrough));
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_1encryptor_PolicyEngine_nativeMarkTampered(
    JNIEnv*, jclass
) {
    hxs::policy::mark_tampered();
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_PolicyEngine_nativeIsTampered(
    JNIEnv*, jclass
) {
    return static_cast<jboolean>(hxs::policy::is_tampered());
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_PolicyEngine_nativeHasSession(
    JNIEnv*, jclass
) {
    return static_cast<jboolean>(hxs::policy::has_session());
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_1encryptor_PolicyEngine_nativeInvalidateSession(
    JNIEnv*, jclass
) {
    hxs::policy::invalidate_session();
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_1encryptor_PolicyEngine_nativeResetForTesting(
    JNIEnv*, jclass
) {
    hxs::policy::reset_for_testing();
}

JNIEXPORT jint JNICALL
Java_com_dark_hxs_1encryptor_BootIntegrity_nativeBootVerify(
    JNIEnv* env, jclass, jobjectArray lib_paths, jobjectArray lib_hashes
) {
    if (lib_paths == nullptr || lib_hashes == nullptr) {
        return static_cast<jint>(hxs::boot::FAIL_BAD_INPUT);
    }
    jsize count = env->GetArrayLength(lib_paths);
    if (env->GetArrayLength(lib_hashes) != count) {
        return static_cast<jint>(hxs::boot::FAIL_BAD_INPUT);
    }

    std::vector<std::string> path_storage;
    std::vector<std::vector<uint8_t>> hash_storage;
    std::vector<hxs::boot::LibCheck> checks;
    path_storage.reserve(static_cast<size_t>(count));
    hash_storage.reserve(static_cast<size_t>(count));
    checks.reserve(static_cast<size_t>(count));

    for (jsize i = 0; i < count; i++) {
        auto path_obj = reinterpret_cast<jstring>(env->GetObjectArrayElement(lib_paths, i));
        auto hash_obj = reinterpret_cast<jbyteArray>(env->GetObjectArrayElement(lib_hashes, i));
        if (path_obj == nullptr || hash_obj == nullptr) {
            return static_cast<jint>(hxs::boot::FAIL_BAD_INPUT);
        }
        const char* utf = env->GetStringUTFChars(path_obj, nullptr);
        if (!utf) return static_cast<jint>(hxs::boot::FAIL_BAD_INPUT);
        path_storage.emplace_back(utf);
        env->ReleaseStringUTFChars(path_obj, utf);

        jsize hlen = env->GetArrayLength(hash_obj);
        if (hlen != 32) {
            return static_cast<jint>(hxs::boot::FAIL_BAD_INPUT);
        }
        std::vector<uint8_t> h(32);
        env->GetByteArrayRegion(hash_obj, 0, 32, reinterpret_cast<jbyte*>(h.data()));
        hash_storage.emplace_back(std::move(h));
    }
    for (jsize i = 0; i < count; i++) {
        hxs::boot::LibCheck c{ path_storage[i].c_str(), hash_storage[i].data() };
        checks.emplace_back(c);
    }

    uint32_t reasons = hxs::boot::boot_verify(checks.data(), checks.size());
    return static_cast<jint>(reasons);
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_1encryptor_BootIntegrity_nativeHardFail(
    JNIEnv*, jclass, jint reason
) {
    hxs::boot::hard_fail(static_cast<uint32_t>(reason));
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_BootIntegrity_nativeSetRelaxedForTesting(
    JNIEnv*, jclass, jboolean relaxed
) {
    return static_cast<jboolean>(
        hxs::boot::set_relaxed_for_testing(static_cast<bool>(relaxed))
    );
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_1encryptor_BootIntegrity_nativeInstallPtrace(
    JNIEnv*, jclass
) {
    hxs::boot::install_ptrace_self_trace();
}

JNIEXPORT jint JNICALL
Java_com_dark_hxs_1encryptor_BootIntegrity_nativeScanEnvironment(
    JNIEnv*, jclass
) {
    return static_cast<jint>(hxs::boot::scan_process_environment());
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_BootIntegrity_nativeHashFile(
    JNIEnv* env, jclass, jstring path
) {
    if (!path) return nullptr;
    const char* p = env->GetStringUTFChars(path, nullptr);
    if (!p) return nullptr;
    uint8_t hash[32];
    bool ok = hxs::IntegrityGuard::hash_file(p, hash);
    env->ReleaseStringUTFChars(path, p);
    return ok ? to_jbyteArray(env, hash, 32) : nullptr;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_1encryptor_BootIntegrity_nativeVerifyHookBaselines(
    JNIEnv*, jclass
) {
    return static_cast<jboolean>(hxs::boot::verify_hook_baselines());
}

jint JNI_OnLoad(JavaVM*, void*) {
    hxs::boot::install_ptrace_self_trace();
    hxs::boot::capture_hook_baselines();
    return JNI_VERSION_1_6;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_hxs_1encryptor_AuthNative_nativeSetup(
    JNIEnv* env, jclass, jbyteArray pin
) {
    JniBytes p(env, pin);
    if (!p.ptr || p.size() == 0) return nullptr;

    auto r = hxs::auth::setup(p.data(), p.size());
    if (!r.success) return nullptr;

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray(2, byteArrayClass, nullptr);
    env->SetObjectArrayElement(result, 0,
        to_jbyteArray(env, r.salt.data(), r.salt.size()));
    env->SetObjectArrayElement(result, 1,
        to_jbyteArray(env, r.hash.data(), r.hash.size()));
    hxs::secure_zero(r.hash.data(), r.hash.size());
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_1encryptor_AuthNative_nativeVerify(
    JNIEnv* env, jclass, jbyteArray pin, jbyteArray salt, jbyteArray stored_hash
) {
    JniBytes p(env, pin);
    JniBytes s(env, salt);
    JniBytes h(env, stored_hash);
    if (!p.ptr || !s.ptr || !h.ptr) return nullptr;

    auto r = hxs::auth::verify(
        p.data(), p.size(),
        s.data(), s.size(),
        h.data(), h.size()
    );
    if (!r.success) return nullptr;

    jbyteArray out = to_jbyteArray(env, r.token.data(), r.token.size());
    hxs::secure_zero(r.token.data(), r.token.size());
    return out;
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_1encryptor_AuthNative_nativeInvalidate(
    JNIEnv*, jclass
) {
    hxs::auth::invalidate();
}

} // extern "C"

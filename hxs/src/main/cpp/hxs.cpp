#include <jni.h>
#include <cstring>
#include <string>
#include <mutex>
#include <unordered_map>
#include <memory>
#include <unistd.h>

#include "wire_format.h"
#include "collection.h"
#include "manifest.h"

#include <android/log.h>
#define TAG "HXS"

// ── Global state ──

static std::mutex g_mtx;
static std::unique_ptr<hxs::Manifest> g_manifest;
static std::unordered_map<std::string, std::unique_ptr<hxs::Collection>> g_collections;
static std::string g_base_dir;
static bool g_open = false;
static hxs::CryptoCallbacks g_crypto;

// ── JNI helpers ──

struct JniBytes {
    JNIEnv* env;
    jbyteArray arr;
    jbyte* ptr;
    jsize len;
    JniBytes(JNIEnv* e, jbyteArray a) : env(e), arr(a), ptr(nullptr), len(0) {
        if (a) { len = env->GetArrayLength(a); ptr = env->GetByteArrayElements(a, nullptr); }
    }
    ~JniBytes() { if (ptr && arr) env->ReleaseByteArrayElements(arr, ptr, JNI_ABORT); }
    const uint8_t* data() const { return reinterpret_cast<const uint8_t*>(ptr); }
    size_t size() const { return static_cast<size_t>(len); }
};

static jbyteArray to_jbyteArray(JNIEnv* env, const uint8_t* data, size_t len) {
    jbyteArray result = env->NewByteArray(static_cast<jint>(len));
    if (result) env->SetByteArrayRegion(result, 0, static_cast<jint>(len),
                                         reinterpret_cast<const jbyte*>(data));
    return result;
}

static std::string jstring_to_string(JNIEnv* env, jstring js) {
    if (!js) return "";
    const char* c = env->GetStringUTFChars(js, nullptr);
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    return s;
}

// ── Crypto callback bridge ──
// These are set from Kotlin via nativeSetCryptoCallbacks
// They call back into HxsEncryptor via JNI global refs

static JavaVM* g_jvm = nullptr;
static jobject g_encryptor_ref = nullptr;   // global ref to HxsEncryptor
static jmethodID g_encrypt_method = nullptr;
static jmethodID g_decrypt_method = nullptr;

static JNIEnv* get_env() {
    JNIEnv* env = nullptr;
    if (g_jvm) g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    return env;
}

static bool crypto_encrypt_cb(const uint8_t* in, size_t in_len,
                               std::vector<uint8_t>& out, void* ctx) {
    JNIEnv* env = get_env();
    if (!env || !g_encryptor_ref || !g_encrypt_method) return false;

    jbyteArray jin = to_jbyteArray(env, in, in_len);
    jbyteArray key = static_cast<jbyteArray>(ctx);

    auto result = static_cast<jbyteArray>(
        env->CallObjectMethod(g_encryptor_ref, g_encrypt_method, jin, key, nullptr));

    env->DeleteLocalRef(jin);
    if (!result) return false;

    jsize rlen = env->GetArrayLength(result);
    out.resize(static_cast<size_t>(rlen));
    env->GetByteArrayRegion(result, 0, rlen, reinterpret_cast<jbyte*>(out.data()));
    env->DeleteLocalRef(result);
    return true;
}

static bool crypto_decrypt_cb(const uint8_t* in, size_t in_len,
                               std::vector<uint8_t>& out, void* ctx) {
    JNIEnv* env = get_env();
    if (!env || !g_encryptor_ref || !g_decrypt_method) return false;

    jbyteArray jin = to_jbyteArray(env, in, in_len);
    jbyteArray key = static_cast<jbyteArray>(ctx);

    auto result = static_cast<jbyteArray>(
        env->CallObjectMethod(g_encryptor_ref, g_decrypt_method, jin, key, nullptr));

    env->DeleteLocalRef(jin);
    if (!result) return false;

    jsize rlen = env->GetArrayLength(result);
    out.resize(static_cast<size_t>(rlen));
    env->GetByteArrayRegion(result, 0, rlen, reinterpret_cast<jbyte*>(out.data()));
    env->DeleteLocalRef(result);
    return true;
}

// ── Ensure collection is loaded ──

static hxs::Collection* ensure_collection(const std::string& name) {
    auto it = g_collections.find(name);
    if (it != g_collections.end()) return it->second.get();

    if (!g_manifest->has_collection(name)) {
        // Register new collection
        hxs::CollectionMeta meta;
        meta.name = name;
        meta.filename = name + ".hxs";
        g_manifest->register_collection(meta);
        g_manifest->save();
    }

    auto coll = std::make_unique<hxs::Collection>(name, g_base_dir, g_crypto);
    coll->load();

    auto* ptr = coll.get();
    g_collections[name] = std::move(coll);
    return ptr;
}

// ── JNI exports ──

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// ── Vault lifecycle ──

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_HexStorage_nativeCreatePlaintext(
    JNIEnv* env, jobject, jstring basePath
) {
    std::lock_guard lock(g_mtx);
    g_base_dir = jstring_to_string(env, basePath);

    g_crypto = {}; // no encryption
    g_manifest = std::make_unique<hxs::Manifest>(g_base_dir + "/manifest.hxs");
    if (!g_manifest->create_plaintext()) return JNI_FALSE;

    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_HexStorage_nativeOpenPlaintext(
    JNIEnv* env, jobject, jstring basePath
) {
    std::lock_guard lock(g_mtx);
    g_base_dir = jstring_to_string(env, basePath);

    g_crypto = {};
    g_manifest = std::make_unique<hxs::Manifest>(g_base_dir + "/manifest.hxs");
    if (!g_manifest->open_plaintext()) return JNI_FALSE;

    // Load registered collections
    for (auto& meta : g_manifest->list_collections()) {
        auto coll = std::make_unique<hxs::Collection>(meta.name, g_base_dir, g_crypto);
        coll->load();
        g_collections[meta.name] = std::move(coll);
    }

    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_HexStorage_nativeCreateEncrypted(
    JNIEnv* env, jobject, jstring basePath,
    jbyteArray appKey, jbyteArray userKey, jobject encryptor
) {
    std::lock_guard lock(g_mtx);
    g_base_dir = jstring_to_string(env, basePath);

    // Set up crypto callbacks via HxsEncryptor
    if (g_encryptor_ref) { env->DeleteGlobalRef(g_encryptor_ref); g_encryptor_ref = nullptr; }
    if (g_crypto.ctx) { env->DeleteGlobalRef(static_cast<jobject>(g_crypto.ctx)); g_crypto.ctx = nullptr; }
    if (encryptor) {
        g_encryptor_ref = env->NewGlobalRef(encryptor);
        jclass cls = env->GetObjectClass(encryptor);
        g_encrypt_method = env->GetMethodID(cls, "nativeEncrypt", "([B[B[B)[B");
        g_decrypt_method = env->GetMethodID(cls, "nativeDecrypt", "([B[B[B)[B");
    }

    JniBytes ak(env, appKey);
    JniBytes uk(env, userKey);

    // The DEK key for crypto callbacks is the user key
    g_crypto.encrypt = crypto_encrypt_cb;
    g_crypto.decrypt = crypto_decrypt_cb;
    g_crypto.ctx = env->NewGlobalRef(userKey); // keep key alive

    g_manifest = std::make_unique<hxs::Manifest>(g_base_dir + "/manifest.hxs");
    if (!g_manifest->create(ak.data(), ak.size(), uk.data(), uk.size(), g_crypto)) {
        if (g_encryptor_ref) { env->DeleteGlobalRef(g_encryptor_ref); g_encryptor_ref = nullptr; }
        if (g_crypto.ctx) { env->DeleteGlobalRef(static_cast<jobject>(g_crypto.ctx)); g_crypto.ctx = nullptr; }
        return JNI_FALSE;
    }

    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_HexStorage_nativeOpenEncrypted(
    JNIEnv* env, jobject, jstring basePath,
    jbyteArray appKey, jbyteArray userKey, jobject encryptor
) {
    std::lock_guard lock(g_mtx);
    g_base_dir = jstring_to_string(env, basePath);

    if (g_encryptor_ref) { env->DeleteGlobalRef(g_encryptor_ref); g_encryptor_ref = nullptr; }
    if (g_crypto.ctx) { env->DeleteGlobalRef(static_cast<jobject>(g_crypto.ctx)); g_crypto.ctx = nullptr; }
    if (encryptor) {
        g_encryptor_ref = env->NewGlobalRef(encryptor);
        jclass cls = env->GetObjectClass(encryptor);
        g_encrypt_method = env->GetMethodID(cls, "nativeEncrypt", "([B[B[B)[B");
        g_decrypt_method = env->GetMethodID(cls, "nativeDecrypt", "([B[B[B)[B");
    }

    JniBytes ak(env, appKey);
    JniBytes uk(env, userKey);

    g_crypto.encrypt = crypto_encrypt_cb;
    g_crypto.decrypt = crypto_decrypt_cb;
    g_crypto.ctx = env->NewGlobalRef(userKey);

    g_manifest = std::make_unique<hxs::Manifest>(g_base_dir + "/manifest.hxs");
    if (!g_manifest->open(ak.data(), ak.size(), uk.data(), uk.size(), g_crypto)) {
        if (g_encryptor_ref) { env->DeleteGlobalRef(g_encryptor_ref); g_encryptor_ref = nullptr; }
        if (g_crypto.ctx) { env->DeleteGlobalRef(static_cast<jobject>(g_crypto.ctx)); g_crypto.ctx = nullptr; }
        return JNI_FALSE;
    }

    for (auto& meta : g_manifest->list_collections()) {
        auto coll = std::make_unique<hxs::Collection>(meta.name, g_base_dir, g_crypto);
        coll->load();
        g_collections[meta.name] = std::move(coll);
    }

    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_HexStorage_nativeClose(JNIEnv* env, jobject) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return;

    // Flush all collections
    for (auto& [name, coll] : g_collections) {
        coll->flush();
        g_manifest->update_collection(name, coll->count(), 0, coll->schema_version());
    }
    g_manifest->save();

    g_collections.clear();
    g_manifest.reset();

    if (g_encryptor_ref) { env->DeleteGlobalRef(g_encryptor_ref); g_encryptor_ref = nullptr; }
    if (g_crypto.ctx) { env->DeleteGlobalRef(static_cast<jobject>(g_crypto.ctx)); g_crypto.ctx = nullptr; }

    g_open = false;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_HexStorage_nativeExists(JNIEnv* env, jobject, jstring basePath) {
    std::string path = jstring_to_string(env, basePath) + "/manifest.hxs";
    return (access(path.c_str(), F_OK) == 0) ? JNI_TRUE : JNI_FALSE;
}

// ── Collection management ──

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_HexStorage_nativeEnsureCollection(JNIEnv* env, jobject, jstring name) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return JNI_FALSE;
    return ensure_collection(jstring_to_string(env, name)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_HexStorage_nativeDropCollection(JNIEnv* env, jobject, jstring name) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return;
    std::string n = jstring_to_string(env, name);
    auto it = g_collections.find(n);
    if (it != g_collections.end()) {
        it->second->drop();
        g_collections.erase(it);
    }
    g_manifest->unregister_collection(n);
    g_manifest->save();
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_hxs_HexStorage_nativeListCollections(JNIEnv* env, jobject) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return nullptr;

    auto colls = g_manifest->list_collections();
    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jint>(colls.size()), strClass, nullptr);
    for (size_t i = 0; i < colls.size(); i++) {
        env->SetObjectArrayElement(result, static_cast<jint>(i),
            env->NewStringUTF(colls[i].name.c_str()));
    }
    return result;
}

// ── CRUD ──

JNIEXPORT jint JNICALL
Java_com_dark_hxs_HexStorage_nativePut(
    JNIEnv* env, jobject, jstring collection, jbyteArray recordData
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return -1;

    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (!coll) return -1;

    JniBytes rd(env, recordData);
    hxs::Record rec = hxs::Record::decode(rd.data(), rd.size());
    return static_cast<jint>(coll->put(rec));
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_HexStorage_nativeUpdate(
    JNIEnv* env, jobject, jstring collection, jbyteArray recordData
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return JNI_FALSE;

    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (!coll) return JNI_FALSE;

    JniBytes rd(env, recordData);
    hxs::Record rec = hxs::Record::decode(rd.data(), rd.size());
    return coll->update(rec) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_hxs_HexStorage_nativeGet(
    JNIEnv* env, jobject, jstring collection, jint recordId
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return nullptr;

    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (!coll) return nullptr;

    hxs::Record rec = coll->get(static_cast<uint32_t>(recordId));
    if (rec.id() == 0) return nullptr;

    auto encoded = rec.encode();
    return to_jbyteArray(env, encoded.data(), encoded.size());
}

JNIEXPORT jboolean JNICALL
Java_com_dark_hxs_HexStorage_nativeDelete(
    JNIEnv* env, jobject, jstring collection, jint recordId
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return JNI_FALSE;

    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (!coll) return JNI_FALSE;

    return coll->remove(static_cast<uint32_t>(recordId)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_dark_hxs_HexStorage_nativeCount(
    JNIEnv* env, jobject, jstring collection
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return 0;

    auto* coll = ensure_collection(jstring_to_string(env, collection));
    return coll ? static_cast<jint>(coll->count()) : 0;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_hxs_HexStorage_nativeGetAll(
    JNIEnv* env, jobject, jstring collection
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return nullptr;

    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (!coll) return nullptr;

    auto records = coll->get_all();
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray(static_cast<jint>(records.size()), byteArrayClass, nullptr);

    for (size_t i = 0; i < records.size(); i++) {
        auto encoded = records[i].encode();
        jbyteArray arr = to_jbyteArray(env, encoded.data(), encoded.size());
        env->SetObjectArrayElement(result, static_cast<jint>(i), arr);
        env->DeleteLocalRef(arr);
    }
    return result;
}

// ── Indexing ──

JNIEXPORT void JNICALL
Java_com_dark_hxs_HexStorage_nativeAddIndex(
    JNIEnv* env, jobject, jstring collection, jint tag, jint wireType
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return;
    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (coll) coll->add_index(static_cast<uint16_t>(tag),
                               static_cast<hxs::WireType>(wireType));
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_HexStorage_nativeRemoveIndex(
    JNIEnv* env, jobject, jstring collection, jint tag
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return;
    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (coll) coll->remove_index(static_cast<uint16_t>(tag));
}

// ── Queries ──

JNIEXPORT jobjectArray JNICALL
Java_com_dark_hxs_HexStorage_nativeQueryString(
    JNIEnv* env, jobject, jstring collection, jint tag, jstring value
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return nullptr;

    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (!coll) return nullptr;

    auto results = coll->query_string(static_cast<uint16_t>(tag),
                                       jstring_to_string(env, value));

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray out = env->NewObjectArray(static_cast<jint>(results.size()), byteArrayClass, nullptr);
    for (size_t i = 0; i < results.size(); i++) {
        auto enc = results[i].encode();
        jbyteArray arr = to_jbyteArray(env, enc.data(), enc.size());
        env->SetObjectArrayElement(out, static_cast<jint>(i), arr);
        env->DeleteLocalRef(arr);
    }
    return out;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_hxs_HexStorage_nativeQueryInt(
    JNIEnv* env, jobject, jstring collection, jint tag, jlong value
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return nullptr;

    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (!coll) return nullptr;

    auto results = coll->query_int(static_cast<uint16_t>(tag),
                                    static_cast<int64_t>(value));

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray out = env->NewObjectArray(static_cast<jint>(results.size()), byteArrayClass, nullptr);
    for (size_t i = 0; i < results.size(); i++) {
        auto enc = results[i].encode();
        jbyteArray arr = to_jbyteArray(env, enc.data(), enc.size());
        env->SetObjectArrayElement(out, static_cast<jint>(i), arr);
        env->DeleteLocalRef(arr);
    }
    return out;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_hxs_HexStorage_nativeQueryRange(
    JNIEnv* env, jobject, jstring collection, jint tag, jlong minVal, jlong maxVal
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return nullptr;

    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (!coll) return nullptr;

    auto results = coll->query_range_u64(static_cast<uint16_t>(tag),
                                          static_cast<uint64_t>(minVal),
                                          static_cast<uint64_t>(maxVal));

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray out = env->NewObjectArray(static_cast<jint>(results.size()), byteArrayClass, nullptr);
    for (size_t i = 0; i < results.size(); i++) {
        auto enc = results[i].encode();
        jbyteArray arr = to_jbyteArray(env, enc.data(), enc.size());
        env->SetObjectArrayElement(out, static_cast<jint>(i), arr);
        env->DeleteLocalRef(arr);
    }
    return out;
}

// ── Flush ──

JNIEXPORT void JNICALL
Java_com_dark_hxs_HexStorage_nativeFlush(JNIEnv* env, jobject, jstring collection) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return;
    std::string name = jstring_to_string(env, collection);
    auto it = g_collections.find(name);
    if (it != g_collections.end()) it->second->flush();
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_HexStorage_nativeFlushAll(JNIEnv*, jobject) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return;
    for (auto& [_, coll] : g_collections) coll->flush();
    g_manifest->save();
}

// ── Schema version ──

JNIEXPORT jint JNICALL
Java_com_dark_hxs_HexStorage_nativeGetSchemaVersion(
    JNIEnv* env, jobject, jstring collection
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return 0;
    auto* coll = ensure_collection(jstring_to_string(env, collection));
    return coll ? static_cast<jint>(coll->schema_version()) : 0;
}

JNIEXPORT void JNICALL
Java_com_dark_hxs_HexStorage_nativeSetSchemaVersion(
    JNIEnv* env, jobject, jstring collection, jint version
) {
    std::lock_guard lock(g_mtx);
    if (!g_open) return;
    auto* coll = ensure_collection(jstring_to_string(env, collection));
    if (coll) coll->set_schema_version(static_cast<uint32_t>(version));
}

} // extern "C"

#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <cstring>
#include <android/log.h>

#include "path_guard.h"
#include "io_engine.h"
#include "crypto_engine.h"
#include "memory_guard.h"
#include "manifest.h"
#include "collection.h"
#include "wal.h"
#include "wire_format.h"

#define LOG_TAG "ums"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mtx;
static std::unique_ptr<fo::IOEngine> g_io;
static std::unique_ptr<tn::CryptoEngine> g_crypto;
static std::unique_ptr<ums::Manifest> g_manifest;
static std::unique_ptr<ums::WAL> g_wal;
static std::unordered_map<std::string, std::unique_ptr<ums::Collection>> g_collections;
static bool g_open = false;

static std::string jstr(JNIEnv* env, jstring js) {
    if (!js) return {};
    const char* r = env->GetStringUTFChars(js, nullptr);
    if (!r) return {};
    std::string s(r);
    env->ReleaseStringUTFChars(js, r);
    return s;
}

static ums::Collection* get_collection(const std::string& name) {
    auto it = g_collections.find(name);
    return it != g_collections.end() ? it->second.get() : nullptr;
}

// Helper to safely get key bytes, zero on release
struct KeyGuard {
    JNIEnv* env;
    jbyteArray arr;
    jbyte* ptr;
    jsize len;

    KeyGuard(JNIEnv* e, jbyteArray a) : env(e), arr(a), ptr(nullptr), len(0) {
        if (a) {
            len = env->GetArrayLength(a);
            ptr = env->GetByteArrayElements(a, nullptr);
        }
    }
    ~KeyGuard() {
        if (ptr) {
            std::memset(ptr, 0, static_cast<size_t>(len));
            env->ReleaseByteArrayElements(arr, ptr, 0); // mode 0: copy back zeros
        }
    }
    const uint8_t* data() const { return reinterpret_cast<const uint8_t*>(ptr); }
    bool valid() const { return ptr != nullptr; }
};

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeCreate(
    JNIEnv* env, jobject, jstring basePath,
    jbyteArray appKey, jbyteArray userKey
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (g_open) {
        LOGE("nativeCreate: already open");
        return JNI_FALSE;
    }

    auto base = jstr(env, basePath);
    fo::PathGuard guard(base);
    g_io = std::make_unique<fo::IOEngine>(std::move(guard));
    if (!g_io->init()) {
        LOGE("nativeCreate: IOEngine init failed");
        return JNI_FALSE;
    }
    g_io->make_dir("index");

    g_crypto = std::make_unique<tn::CryptoEngine>();
    g_manifest = std::make_unique<ums::Manifest>(*g_io, *g_crypto, "manifest.ums");

    KeyGuard ak(env, appKey);
    KeyGuard uk(env, userKey);
    if (!ak.valid() || !uk.valid()) {
        LOGE("nativeCreate: failed to get key bytes");
        return JNI_FALSE;
    }

    bool ok = g_manifest->create(ak.data(), uk.data());
    if (!ok) {
        LOGE("nativeCreate: manifest create failed");
        return JNI_FALSE;
    }

    g_wal = std::make_unique<ums::WAL>(*g_io, "wal.ums");
    g_wal->open();
    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeOpen(
    JNIEnv* env, jobject, jstring basePath,
    jbyteArray appKey, jbyteArray userKey
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (g_open) {
        LOGE("nativeOpen: already open");
        return JNI_FALSE;
    }

    auto base = jstr(env, basePath);
    fo::PathGuard guard(base);
    g_io = std::make_unique<fo::IOEngine>(std::move(guard));
    if (!g_io->init()) {
        LOGE("nativeOpen: IOEngine init failed");
        return JNI_FALSE;
    }

    g_crypto = std::make_unique<tn::CryptoEngine>();
    g_manifest = std::make_unique<ums::Manifest>(*g_io, *g_crypto, "manifest.ums");

    KeyGuard ak(env, appKey);
    KeyGuard uk(env, userKey);
    if (!ak.valid() || !uk.valid()) {
        LOGE("nativeOpen: failed to get key bytes");
        return JNI_FALSE;
    }

    bool ok = g_manifest->open(ak.data(), uk.data());
    if (!ok) {
        LOGE("nativeOpen: manifest open failed");
        return JNI_FALSE;
    }

    // Load registered collections
    for (auto& [name, meta] : g_manifest->collections()) {
        auto col = std::make_unique<ums::Collection>(
            name, meta.filename, *g_io, *g_crypto, g_manifest->dek()
        );
        col->load();
        g_collections[name] = std::move(col);
    }

    g_wal = std::make_unique<ums::WAL>(*g_io, "wal.ums");
    g_wal->open();

    // Replay any uncommitted WAL entries
    g_wal->replay([](const ums::WalEntry& entry) {
        auto* col = get_collection(entry.collection);
        if (!col) return;
        if (entry.op == ums::WalOp::PUT) {
            auto rec = ums::Record::decode(entry.data.data(), entry.data.size());
            col->put(rec);
        } else if (entry.op == ums::WalOp::DELETE) {
            col->remove(entry.record_id);
        }
    });
    g_wal->checkpoint();

    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeCreateWithPassphrase(
    JNIEnv* env, jobject, jstring basePath,
    jbyteArray appKey, jbyteArray passphrase
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (g_open) {
        LOGE("nativeCreateWithPassphrase: already open");
        return JNI_FALSE;
    }

    auto base = jstr(env, basePath);
    fo::PathGuard guard(base);
    g_io = std::make_unique<fo::IOEngine>(std::move(guard));
    if (!g_io->init()) {
        LOGE("nativeCreateWithPassphrase: IOEngine init failed");
        return JNI_FALSE;
    }
    g_io->make_dir("index");

    g_crypto = std::make_unique<tn::CryptoEngine>();
    g_manifest = std::make_unique<ums::Manifest>(*g_io, *g_crypto, "manifest.ums");

    KeyGuard ak(env, appKey);
    KeyGuard pp(env, passphrase);
    if (!ak.valid() || !pp.valid()) {
        LOGE("nativeCreateWithPassphrase: failed to get key/passphrase bytes");
        return JNI_FALSE;
    }

    bool ok = g_manifest->create_with_passphrase(
        ak.data(),
        reinterpret_cast<const uint8_t*>(pp.ptr), static_cast<size_t>(pp.len)
    );
    if (!ok) {
        LOGE("nativeCreateWithPassphrase: manifest create failed");
        return JNI_FALSE;
    }

    g_wal = std::make_unique<ums::WAL>(*g_io, "wal.ums");
    g_wal->open();
    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeOpenWithPassphrase(
    JNIEnv* env, jobject, jstring basePath,
    jbyteArray appKey, jbyteArray passphrase
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (g_open) {
        LOGE("nativeOpenWithPassphrase: already open");
        return JNI_FALSE;
    }

    auto base = jstr(env, basePath);
    fo::PathGuard guard(base);
    g_io = std::make_unique<fo::IOEngine>(std::move(guard));
    if (!g_io->init()) {
        LOGE("nativeOpenWithPassphrase: IOEngine init failed");
        return JNI_FALSE;
    }

    g_crypto = std::make_unique<tn::CryptoEngine>();
    g_manifest = std::make_unique<ums::Manifest>(*g_io, *g_crypto, "manifest.ums");

    KeyGuard ak(env, appKey);
    KeyGuard pp(env, passphrase);
    if (!ak.valid() || !pp.valid()) {
        LOGE("nativeOpenWithPassphrase: failed to get key/passphrase bytes");
        return JNI_FALSE;
    }

    bool ok = g_manifest->open_with_passphrase(
        ak.data(),
        reinterpret_cast<const uint8_t*>(pp.ptr), static_cast<size_t>(pp.len)
    );
    if (!ok) {
        LOGE("nativeOpenWithPassphrase: manifest open failed");
        return JNI_FALSE;
    }

    // Load registered collections
    for (auto& [name, meta] : g_manifest->collections()) {
        auto col = std::make_unique<ums::Collection>(
            name, meta.filename, *g_io, *g_crypto, g_manifest->dek()
        );
        col->load();
        g_collections[name] = std::move(col);
    }

    g_wal = std::make_unique<ums::WAL>(*g_io, "wal.ums");
    g_wal->open();

    // Replay any uncommitted WAL entries
    g_wal->replay([](const ums::WalEntry& entry) {
        auto* col = get_collection(entry.collection);
        if (!col) return;
        if (entry.op == ums::WalOp::PUT) {
            auto rec = ums::Record::decode(entry.data.data(), entry.data.size());
            col->put(rec);
        } else if (entry.op == ums::WalOp::DELETE) {
            col->remove(entry.record_id);
        }
    });
    g_wal->checkpoint();

    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeCreatePlaintext(
    JNIEnv* env, jobject, jstring basePath
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (g_open) {
        LOGE("nativeCreatePlaintext: already open");
        return JNI_FALSE;
    }

    auto base = jstr(env, basePath);
    fo::PathGuard guard(base);
    g_io = std::make_unique<fo::IOEngine>(std::move(guard));
    if (!g_io->init()) {
        LOGE("nativeCreatePlaintext: IOEngine init failed");
        return JNI_FALSE;
    }
    g_io->make_dir("index");

    g_crypto = std::make_unique<tn::CryptoEngine>();
    g_manifest = std::make_unique<ums::Manifest>(*g_io, *g_crypto, "manifest.ums");

    bool ok = g_manifest->create_plaintext();
    if (!ok) {
        LOGE("nativeCreatePlaintext: manifest create_plaintext failed");
        return JNI_FALSE;
    }

    g_wal = std::make_unique<ums::WAL>(*g_io, "wal.ums");
    g_wal->open();
    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeOpenPlaintext(
    JNIEnv* env, jobject, jstring basePath
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (g_open) {
        LOGE("nativeOpenPlaintext: already open");
        return JNI_FALSE;
    }

    auto base = jstr(env, basePath);
    fo::PathGuard guard(base);
    g_io = std::make_unique<fo::IOEngine>(std::move(guard));
    if (!g_io->init()) {
        LOGE("nativeOpenPlaintext: IOEngine init failed");
        return JNI_FALSE;
    }

    g_crypto = std::make_unique<tn::CryptoEngine>();
    g_manifest = std::make_unique<ums::Manifest>(*g_io, *g_crypto, "manifest.ums");

    bool ok = g_manifest->open_plaintext();
    if (!ok) {
        LOGE("nativeOpenPlaintext: manifest open_plaintext failed");
        return JNI_FALSE;
    }

    // Load registered collections with plaintext=true
    for (auto& [name, meta] : g_manifest->collections()) {
        auto col = std::make_unique<ums::Collection>(
            name, meta.filename, *g_io, *g_crypto, nullptr, true
        );
        col->load();
        g_collections[name] = std::move(col);
    }

    g_wal = std::make_unique<ums::WAL>(*g_io, "wal.ums");
    g_wal->open();

    // Replay any uncommitted WAL entries
    g_wal->replay([](const ums::WalEntry& entry) {
        auto* col = get_collection(entry.collection);
        if (!col) return;
        if (entry.op == ums::WalOp::PUT) {
            auto rec = ums::Record::decode(entry.data.data(), entry.data.size());
            col->put(rec);
        } else if (entry.op == ums::WalOp::DELETE) {
            col->remove(entry.record_id);
        }
    });
    g_wal->checkpoint();

    g_open = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeExists(
    JNIEnv* env, jobject, jstring basePath
) {
    auto base = jstr(env, basePath);
    fo::PathGuard guard(base);
    fo::IOEngine io(std::move(guard));
    return io.exists("manifest.ums") ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeClose(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return;
    // Flush all collections
    for (auto& [name, col] : g_collections) {
        col->flush();
        g_manifest->update_collection(name, col->count(), 0);
    }
    g_manifest->save();
    g_wal->checkpoint();

    g_collections.clear();
    g_wal.reset();
    g_manifest.reset();
    g_crypto.reset();
    g_io.reset();
    g_open = false;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeEnsureCollection(
    JNIEnv* env, jobject, jstring name
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return JNI_FALSE;
    auto n = jstr(env, name);
    if (g_collections.count(n)) return JNI_TRUE;

    std::string filename = n + ".ums";
    ums::CollectionMeta meta{n, filename, 0, 0};
    g_manifest->register_collection(meta);

    bool pt = g_manifest->is_plaintext();
    auto col = std::make_unique<ums::Collection>(
        n, filename, *g_io, *g_crypto,
        pt ? nullptr : g_manifest->dek(), pt
    );
    g_collections[n] = std::move(col);
    g_manifest->save();
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativePut(
    JNIEnv* env, jobject, jstring collection, jbyteArray recordData
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return -1;
    auto col = get_collection(jstr(env, collection));
    if (!col) {
        LOGE("nativePut: collection not found");
        return -1;
    }

    jsize len = env->GetArrayLength(recordData);
    auto* bytes = env->GetByteArrayElements(recordData, nullptr);
    if (!bytes) {
        LOGE("nativePut: failed to get record bytes");
        return -1;
    }
    auto rec = ums::Record::decode(reinterpret_cast<const uint8_t*>(bytes), len);
    env->ReleaseByteArrayElements(recordData, bytes, JNI_ABORT);

    // WAL first
    auto encoded = rec.encode();
    auto seq = g_wal->append(ums::WalOp::PUT, col->name(), rec.id(), encoded);

    uint32_t id = col->put(rec);
    col->flush();
    g_wal->mark_committed(seq);

    return static_cast<jint>(id);
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeGet(
    JNIEnv* env, jobject, jstring collection, jint recordId
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return nullptr;
    auto col = get_collection(jstr(env, collection));
    if (!col) return nullptr;

    auto rec = col->get(static_cast<uint32_t>(recordId));
    if (rec.id() == 0) return nullptr;

    auto encoded = rec.encode();
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(encoded.size()));
    if (!arr) return nullptr;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(encoded.size()),
        reinterpret_cast<const jbyte*>(encoded.data()));
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeDelete(
    JNIEnv* env, jobject, jstring collection, jint recordId
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return JNI_FALSE;
    auto col = get_collection(jstr(env, collection));
    if (!col) return JNI_FALSE;

    auto seq = g_wal->append(ums::WalOp::DELETE, col->name(),
                             static_cast<uint32_t>(recordId));
    bool ok = col->remove(static_cast<uint32_t>(recordId));
    if (ok) {
        col->flush();
        g_wal->mark_committed(seq);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeCount(
    JNIEnv* env, jobject, jstring collection
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return 0;
    auto col = get_collection(jstr(env, collection));
    return col ? static_cast<jint>(col->count()) : 0;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeGetAll(
    JNIEnv* env, jobject, jstring collection
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return nullptr;
    auto col = get_collection(jstr(env, collection));
    if (!col) return nullptr;

    std::vector<std::vector<uint8_t>> encoded_records;
    col->for_each([&](const ums::Record& rec) {
        encoded_records.push_back(rec.encode());
    });

    auto cls = env->FindClass("[B"); // byte[]
    auto arr = env->NewObjectArray(static_cast<jsize>(encoded_records.size()), cls, nullptr);
    if (!arr) {
        env->DeleteLocalRef(cls);
        return nullptr;
    }
    for (size_t i = 0; i < encoded_records.size(); i++) {
        auto& e = encoded_records[i];
        jbyteArray ba = env->NewByteArray(static_cast<jsize>(e.size()));
        if (!ba) break;
        env->SetByteArrayRegion(ba, 0, static_cast<jsize>(e.size()),
            reinterpret_cast<const jbyte*>(e.data()));
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), ba);
        env->DeleteLocalRef(ba);
    }
    env->DeleteLocalRef(cls);
    return arr;
}

// ---------------------------------------------------------------------------
// Index + Query API
// ---------------------------------------------------------------------------

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeAddIndex(
    JNIEnv* env, jobject, jstring collection, jint tag, jint wireType
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return JNI_FALSE;
    auto col = get_collection(jstr(env, collection));
    if (!col) return JNI_FALSE;
    col->add_index(static_cast<uint16_t>(tag), static_cast<uint8_t>(wireType));
    return JNI_TRUE;
}

// helper: encode a list of Records into a JNI Array<ByteArray>
static jobjectArray records_to_jni(JNIEnv* env, const std::vector<ums::Record>& records) {
    auto cls = env->FindClass("[B");
    auto arr = env->NewObjectArray(static_cast<jsize>(records.size()), cls, nullptr);
    if (!arr) {
        env->DeleteLocalRef(cls);
        return nullptr;
    }
    for (size_t i = 0; i < records.size(); i++) {
        auto encoded = records[i].encode();
        jbyteArray ba = env->NewByteArray(static_cast<jsize>(encoded.size()));
        if (!ba) break;
        env->SetByteArrayRegion(ba, 0, static_cast<jsize>(encoded.size()),
            reinterpret_cast<const jbyte*>(encoded.data()));
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), ba);
        env->DeleteLocalRef(ba);
    }
    env->DeleteLocalRef(cls);
    return arr;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeQueryString(
    JNIEnv* env, jobject, jstring collection, jint tag, jstring value
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return nullptr;
    auto col = get_collection(jstr(env, collection));
    if (!col) return nullptr;
    auto val = jstr(env, value);
    auto results = col->find_eq_string(static_cast<uint16_t>(tag), val);
    return records_to_jni(env, results);
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeQueryInt(
    JNIEnv* env, jobject, jstring collection, jint tag, jlong value
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return nullptr;
    auto col = get_collection(jstr(env, collection));
    if (!col) return nullptr;
    auto results = col->find_eq_int(static_cast<uint16_t>(tag), static_cast<int64_t>(value));
    return records_to_jni(env, results);
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeQueryRange(
    JNIEnv* env, jobject, jstring collection, jint tag, jlong minVal, jlong maxVal
) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open) return nullptr;
    auto col = get_collection(jstr(env, collection));
    if (!col) return nullptr;
    auto results = col->find_range_u64(
        static_cast<uint16_t>(tag),
        static_cast<uint64_t>(minVal),
        static_cast<uint64_t>(maxVal)
    );
    return records_to_jni(env, results);
}

// ---------------------------------------------------------------------------
// Flags
// ---------------------------------------------------------------------------

JNIEXPORT jint JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeGetFlags(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open || !g_manifest) return 0;
    return static_cast<jint>(g_manifest->flags());
}

JNIEXPORT jboolean JNICALL
Java_com_dark_ums_UnifiedMemorySystem_nativeSetFlags(JNIEnv*, jobject, jint flags) {
    std::lock_guard<std::mutex> lock(g_mtx);
    if (!g_open || !g_manifest) return JNI_FALSE;
    g_manifest->set_flags(static_cast<uint16_t>(flags));
    return g_manifest->save() ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

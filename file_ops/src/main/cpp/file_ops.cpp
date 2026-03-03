#include <jni.h>
#include "io_engine.h"
#include <memory>
#include <string>

static std::unique_ptr<fo::IOEngine> g_engine;

static std::string jstring_to_string(JNIEnv* env, jstring js) {
    const char* raw = env->GetStringUTFChars(js, nullptr);
    std::string s(raw);
    env->ReleaseStringUTFChars(js, raw);
    return s;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_dark_file_1ops_FileOps_nativeInit(JNIEnv* env, jobject, jstring basePath) {
    auto base = jstring_to_string(env, basePath);
    fo::PathGuard guard(base);
    g_engine = std::make_unique<fo::IOEngine>(std::move(guard));
    return g_engine->init() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_file_1ops_FileOps_nativeWrite(
    JNIEnv* env, jobject, jstring path, jbyteArray data, jlong offset
) {
    if (!g_engine) return JNI_FALSE;
    auto rel = jstring_to_string(env, path);
    jsize len = env->GetArrayLength(data);
    auto* bytes = env->GetByteArrayElements(data, nullptr);
    auto result = g_engine->write(rel, reinterpret_cast<const uint8_t*>(bytes), len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return result.success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_dark_file_1ops_FileOps_nativeRead(
    JNIEnv* env, jobject, jstring path, jlong offset, jlong length
) {
    if (!g_engine) return nullptr;
    auto rel = jstring_to_string(env, path);
    auto result = g_engine->read(rel, static_cast<size_t>(offset), static_cast<size_t>(length));
    if (!result.success) return nullptr;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(result.data.size()));
    if (arr && !result.data.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(result.data.size()),
            reinterpret_cast<const jbyte*>(result.data.data()));
    }
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_file_1ops_FileOps_nativeAppend(
    JNIEnv* env, jobject, jstring path, jbyteArray data
) {
    if (!g_engine) return JNI_FALSE;
    auto rel = jstring_to_string(env, path);
    jsize len = env->GetArrayLength(data);
    auto* bytes = env->GetByteArrayElements(data, nullptr);
    auto result = g_engine->append(rel, reinterpret_cast<const uint8_t*>(bytes), len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return result.success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_file_1ops_FileOps_nativeDelete(JNIEnv* env, jobject, jstring path) {
    if (!g_engine) return JNI_FALSE;
    return g_engine->remove(jstring_to_string(env, path)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_file_1ops_FileOps_nativeExists(JNIEnv* env, jobject, jstring path) {
    if (!g_engine) return JNI_FALSE;
    return g_engine->exists(jstring_to_string(env, path)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_dark_file_1ops_FileOps_nativeGetSize(JNIEnv* env, jobject, jstring path) {
    if (!g_engine) return -1;
    return g_engine->file_size(jstring_to_string(env, path));
}

JNIEXPORT jboolean JNICALL
Java_com_dark_file_1ops_FileOps_nativeMakeDir(JNIEnv* env, jobject, jstring path) {
    if (!g_engine) return JNI_FALSE;
    return g_engine->make_dir(jstring_to_string(env, path)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dark_file_1ops_FileOps_nativeListDir(JNIEnv* env, jobject, jstring path) {
    if (!g_engine) return nullptr;
    auto entries = g_engine->list_dir(jstring_to_string(env, path));
    auto cls = env->FindClass("java/lang/String");
    auto arr = env->NewObjectArray(static_cast<jsize>(entries.size()), cls, nullptr);
    for (size_t i = 0; i < entries.size(); i++) {
        env->SetObjectArrayElement(arr, static_cast<jsize>(i),
            env->NewStringUTF(entries[i].c_str()));
    }
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_file_1ops_FileOps_nativeRename(JNIEnv* env, jobject, jstring from, jstring to) {
    if (!g_engine) return JNI_FALSE;
    return g_engine->rename_file(
        jstring_to_string(env, from), jstring_to_string(env, to)
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_file_1ops_FileOps_nativeFsync(JNIEnv* env, jobject, jstring path) {
    if (!g_engine) return JNI_FALSE;
    return g_engine->fsync_file(jstring_to_string(env, path)) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

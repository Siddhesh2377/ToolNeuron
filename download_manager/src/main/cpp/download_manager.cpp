#include <jni.h>
#include <android/log.h>
#include "download_engine.h"
#include "persistence.h"
#include <string>
#include <vector>

#define LOG_TAG "HXD"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,   LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,   LOG_TAG, __VA_ARGS__)

using namespace hxd;

extern "C" {

// ── File I/O engine ───────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_dark_download_1manager_HxdNative_nativePrepare(
        JNIEnv* env, jobject, jint id, jstring dest_j) {
    const char* dest = env->GetStringUTFChars(dest_j, nullptr);
    int64_t offset = DownloadEngine::instance().prepare((int)id, dest);
    env->ReleaseStringUTFChars(dest_j, dest);
    if (offset < 0) LOGE("prepare failed for id=%d", (int)id);
    else LOGD("prepare id=%d resumeOffset=%lld", (int)id, (long long)offset);
    return (jlong)offset;
}

JNIEXPORT void JNICALL
Java_com_dark_download_1manager_HxdNative_nativeSetTotal(
        JNIEnv*, jobject, jint id, jlong total) {
    DownloadEngine::instance().set_total((int)id, (int64_t)total);
}

JNIEXPORT jboolean JNICALL
Java_com_dark_download_1manager_HxdNative_nativeWriteChunk(
        JNIEnv* env, jobject, jint id, jbyteArray data, jint offset, jint len) {
    jbyte* buf = env->GetByteArrayElements(data, nullptr);
    bool ok = DownloadEngine::instance().write_chunk(
            (int)id, reinterpret_cast<const uint8_t*>(buf), (int)offset, (int)len);
    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dark_download_1manager_HxdNative_nativeComplete(
        JNIEnv*, jobject, jint id) {
    bool ok = DownloadEngine::instance().complete((int)id);
    LOGD("complete id=%d success=%d", (int)id, ok);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dark_download_1manager_HxdNative_nativeFail(
        JNIEnv*, jobject, jint id) {
    DownloadEngine::instance().fail((int)id);
}

JNIEXPORT void JNICALL
Java_com_dark_download_1manager_HxdNative_nativeCancel(
        JNIEnv*, jobject, jint id) {
    DownloadEngine::instance().cancel((int)id);
}

JNIEXPORT void JNICALL
Java_com_dark_download_1manager_HxdNative_nativePause(
        JNIEnv*, jobject, jint id) {
    DownloadEngine::instance().pause((int)id);
}

// Returns [downloaded_bytes, total_bytes, speed_bps, state_ordinal]
JNIEXPORT jlongArray JNICALL
Java_com_dark_download_1manager_HxdNative_nativeGetProgress(
        JNIEnv* env, jobject, jint id) {
    ProgressInfo p = DownloadEngine::instance().get_progress((int)id);
    jlongArray arr = env->NewLongArray(4);
    jlong buf[4] = {p.downloaded_bytes, p.total_bytes, p.speed_bps, (jlong)p.state};
    env->SetLongArrayRegion(arr, 0, 4, buf);
    return arr;
}

JNIEXPORT void JNICALL
Java_com_dark_download_1manager_HxdNative_nativeCleanup(
        JNIEnv*, jobject, jint id) {
    DownloadEngine::instance().cleanup((int)id);
}

// ── Persistence ───────────────────────────────────────────────────────────────

// Each task is represented as 6 parallel arrays for efficient JNI transfer.
JNIEXPORT jboolean JNICALL
Java_com_dark_download_1manager_HxdNative_nativeSaveQueue(
        JNIEnv* env, jobject,
        jstring   path_j,
        jintArray ids_j,
        jbyteArray states_j,
        jlongArray downloaded_j,
        jlongArray total_j,
        jobjectArray urls_j,
        jobjectArray dest_paths_j) {

    const char* path  = env->GetStringUTFChars(path_j, nullptr);
    jsize       count = env->GetArrayLength(ids_j);

    jint*  ids        = env->GetIntArrayElements (ids_j,        nullptr);
    jbyte* states     = env->GetByteArrayElements(states_j,     nullptr);
    jlong* downloaded = env->GetLongArrayElements(downloaded_j, nullptr);
    jlong* totals     = env->GetLongArrayElements(total_j,      nullptr);

    std::vector<PersistedTask> tasks;
    tasks.reserve(count);
    for (jsize i = 0; i < count; ++i) {
        PersistedTask t;
        t.id              = (int)ids[i];
        t.state           = (TaskState)(uint8_t)states[i];
        t.downloaded_bytes = downloaded[i];
        t.total_bytes     = totals[i];

        auto url_j  = (jstring)env->GetObjectArrayElement(urls_j,       i);
        auto dest_j = (jstring)env->GetObjectArrayElement(dest_paths_j, i);
        const char* url  = env->GetStringUTFChars(url_j,  nullptr);
        const char* dest = env->GetStringUTFChars(dest_j, nullptr);
        t.url       = url;
        t.dest_path = dest;
        env->ReleaseStringUTFChars(url_j,  url);
        env->ReleaseStringUTFChars(dest_j, dest);
        env->DeleteLocalRef(url_j);
        env->DeleteLocalRef(dest_j);
        tasks.push_back(std::move(t));
    }

    env->ReleaseIntArrayElements (ids_j,        ids,        JNI_ABORT);
    env->ReleaseByteArrayElements(states_j,     states,     JNI_ABORT);
    env->ReleaseLongArrayElements(downloaded_j, downloaded, JNI_ABORT);
    env->ReleaseLongArrayElements(total_j,      totals,     JNI_ABORT);

    bool ok = save_queue(path, tasks);
    env->ReleaseStringUTFChars(path_j, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Returns Array<Array<String>> — each inner array: [id, state, downloaded, total, url, destPath]
JNIEXPORT jobjectArray JNICALL
Java_com_dark_download_1manager_HxdNative_nativeLoadQueue(
        JNIEnv* env, jobject, jstring path_j) {
    const char* path = env->GetStringUTFChars(path_j, nullptr);
    std::vector<PersistedTask> tasks = load_queue(path);
    env->ReleaseStringUTFChars(path_j, path);

    jclass strClass    = env->FindClass("java/lang/String");
    jclass strArrClass = env->FindClass("[Ljava/lang/String;");
    jobjectArray result = env->NewObjectArray((jsize)tasks.size(), strArrClass, nullptr);

    for (jsize i = 0; i < (jsize)tasks.size(); ++i) {
        const auto& t = tasks[i];
        jobjectArray row = env->NewObjectArray(6, strClass, nullptr);
        jstring s0 = env->NewStringUTF(std::to_string(t.id).c_str());
        jstring s1 = env->NewStringUTF(std::to_string((int)t.state).c_str());
        jstring s2 = env->NewStringUTF(std::to_string(t.downloaded_bytes).c_str());
        jstring s3 = env->NewStringUTF(std::to_string(t.total_bytes).c_str());
        jstring s4 = env->NewStringUTF(t.url.c_str());
        jstring s5 = env->NewStringUTF(t.dest_path.c_str());
        env->SetObjectArrayElement(row, 0, s0);
        env->SetObjectArrayElement(row, 1, s1);
        env->SetObjectArrayElement(row, 2, s2);
        env->SetObjectArrayElement(row, 3, s3);
        env->SetObjectArrayElement(row, 4, s4);
        env->SetObjectArrayElement(row, 5, s5);
        env->SetObjectArrayElement(result, i, row);
        env->DeleteLocalRef(s0);
        env->DeleteLocalRef(s1);
        env->DeleteLocalRef(s2);
        env->DeleteLocalRef(s3);
        env->DeleteLocalRef(s4);
        env->DeleteLocalRef(s5);
        env->DeleteLocalRef(row);
    }

    return result;
}

} // extern "C"

/*=============================================================
 *   utils/jni_utils.h
 *=============================================================
 *
 *  Thin wrappers that translate C++ data → Kotlin callbacks.
 *  The functions are intentionally very small – they just look up
 *  the method ID and call it, keeping the JNI boilerplate out of
 *  the main logic.
 *============================================================*/

#pragma once

#include <jni.h>
#include <string>

namespace jni {
    void on_token(JNIEnv* env, jobject cb, const std::string& txt);
    void on_toolcall(JNIEnv* env, jobject cb,
                     const std::string& name,
                     const std::string& payload);
    void on_error(JNIEnv* env, jobject cb, const char* msg);
    void on_done(JNIEnv* env, jobject cb);
}
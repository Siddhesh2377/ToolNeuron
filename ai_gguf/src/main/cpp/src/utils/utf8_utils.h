/*=============================================================
 *   utils/utf8_utils.h
 *=============================================================
 *
 *  UTF‑8 ⇔ UTF‑16 (Java) conversion helpers, plus a thread‑local
 *  carry buffer that preserves incomplete multi‑byte sequences
 *  for streaming output.  All stubs are pass‑through; implementations
 *  live in utf8_utils.cpp.
 *============================================================*/

#pragma once

#include <jni.h>
#include <string>

namespace utf8 {
    /* Convert Java string (UTF‑16) → UTF‑8 std::string. */
    std::string from_jstring(JNIEnv* env, jstring js);

    /* Convert UTF‑8 string → Java string, preserving carry for
       incomplete UTF‑8 bytes when streaming. */
    jstring to_jstring(JNIEnv* env,
                       const std::string& utf8,
                       std::string& carry);

    /* Flush any remaining carry as U+FFFD replacement char. */
    void flush_carry(JNIEnv* env, jobject cb);

    /* Thread‑local reference to the current carry buffer. */
    std::string& get_carry_buffer();
    void clear_carry_buffer();
}
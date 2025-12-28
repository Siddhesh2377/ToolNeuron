/*=============================================================
 *   utils/logger.h
 *=============================================================
 *
 *  Very small header‑only logger.
 *  - ANSI / Android log output depending on compilation target.
 *  - Thread‑safe on desktop, native Android log is already thread‑safe.
 *  - Runtime level control; compile‑time filtering via NDEBUG.
 *  - Exported macros:
 *        LOG_ERROR(...)
 *        LOG_WARN(...)
 *        LOG_INFO(...)
 *        LOG_DEBUG(...)
 *============================================================*/

#pragma once

#include <cstdio>
#include <cstdarg>
#include <atomic>

#if defined(__ANDROID__)
#include <android/log.h>
#define LOG_PLATFORM_ANDROID 1
#else
#define LOG_PLATFORM_ANDROID 0
#endif

namespace log {
    enum class Level : int {
        Error   = 1,
        Warning = 2,
        Info    = 3,
        Debug   = 4
    };

    inline Level get_level() {
        static std::atomic<Level> lvl{Level::Info};
        return lvl.load(std::memory_order_relaxed);
    }

    inline void set_level(Level l) {
        static std::atomic<Level> lvl{Level::Info};
        lvl.store(l, std::memory_order_relaxed);
    }

    inline void logf(Level level, const char* fmt, ...) {
        if (level > get_level()) return;

#if LOG_PLATFORM_ANDROID
        int android_lvl = ANDROID_LOG_INFO;
        switch (level) {
            case Level::Error:   android_lvl = ANDROID_LOG_ERROR;   break;
            case Level::Warning: android_lvl = ANDROID_LOG_WARN;    break;
            case Level::Info:    android_lvl = ANDROID_LOG_INFO;    break;
            case Level::Debug:   android_lvl = ANDROID_LOG_DEBUG;   break;
        }
        va_list ap;
        va_start(ap, fmt);
        __android_log_vprint(android_lvl, "ai_core", fmt, ap);
        va_end(ap);
#else
        static std::mutex mtx;
        va_list ap;
        va_start(ap, fmt);
        std::lock_guard<std::mutex> lock(mtx);
        if (level == Level::Error || level == Level::Warning)
            std::vfprintf(stderr, fmt, ap);
        else
            std::vfprintf(stdout, fmt, ap);
        std::fprintf(stdout, "\n");
        va_end(ap);
#endif
    }
} // namespace log

#define LOG_ERROR(...)  ::log::logf(::log::Level::Error,   __VA_ARGS__)
#define LOG_WARN(...)   ::log::logf(::log::Level::Warning, __VA_ARGS__)
#define LOG_INFO(...)  ::log::logf(::log::Level::Info,    __VA_ARGS__)
#define LOG_DEBUG(...)  ::log::logf(::log::Level::Debug,   __VA_ARGS__)

#ifndef NDEBUG
// DEBUG can still be compiled in.
#else
#undef LOG_DEBUG
#define LOG_DEBUG(...)
#endif
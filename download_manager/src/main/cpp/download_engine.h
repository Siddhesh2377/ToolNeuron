#pragma once
#include "download_task.h"
#include <unordered_map>
#include <mutex>
#include <string>

namespace hxd {

// DownloadEngine: owns file descriptors and per-task state.
// Thread-safe — called from multiple coroutine threads via JNI.
//
// Lifetime of a task:
//   prepare() → [set_total()] → write_chunk()... → complete() | fail() | cleanup()
//
// On pause: caller calls cleanup() (fd closed, temp file kept).
//           Next prepare() detects .hxd_tmp → returns its size as resume offset.
// On cancel: caller deletes the .hxd_tmp file from Kotlin.
// On error:  caller calls fail() (fd closed, temp file kept for retry).
class DownloadEngine {
public:
    static DownloadEngine& instance();

    // Opens / appends to <dest_path>.hxd_tmp.
    // Returns resume offset (size of existing partial file), 0 if fresh start, -1 on error.
    int64_t prepare(int id, const char* dest_path);

    // Sets total expected bytes (from Content-Length + resume offset).
    void set_total(int id, int64_t total_bytes);

    // Writes a chunk to the temp file. Returns false if cancel/pause was signalled or I/O failed.
    bool write_chunk(int id, const uint8_t* data, int offset, int len);

    // fsync + rename <dest>.hxd_tmp → <dest>. Returns true on success.
    bool complete(int id);

    // Closes fd, removes task context. Does NOT delete the temp file (caller decides).
    void fail(int id);

    // Signals cancel — next write_chunk() call returns false. Caller still must call fail()/cleanup().
    void cancel(int id);

    // Signals pause — next write_chunk() call returns false. Caller calls cleanup() to close fd.
    void pause(int id);

    // Snapshot of current progress for this task.
    ProgressInfo get_progress(int id);

    // Closes fd and removes task context. Temp file is preserved (used for pause).
    void cleanup(int id);

private:
    DownloadEngine() = default;
    DownloadEngine(const DownloadEngine&) = delete;
    DownloadEngine& operator=(const DownloadEngine&) = delete;

    struct TaskCtx {
        int         fd               = -1;
        std::string temp_path;
        std::string dest_path;
        int64_t     downloaded_bytes = 0;
        int64_t     total_bytes      = -1;
        bool        cancel_requested = false;
        bool        pause_requested  = false;
        int64_t     session_start_ms     = 0;
        int64_t     session_start_bytes  = 0;
    };

    std::unordered_map<int, TaskCtx> contexts_;
    std::mutex mutex_;

    static int64_t now_ms();
    static int64_t calc_speed(TaskCtx& ctx);
    void close_and_erase(int id);
};

} // namespace hxd

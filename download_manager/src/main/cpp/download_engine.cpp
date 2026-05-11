#include "download_engine.h"
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <chrono>

namespace hxd {

DownloadEngine& DownloadEngine::instance() {
    static DownloadEngine inst;
    return inst;
}

int64_t DownloadEngine::now_ms() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

int64_t DownloadEngine::calc_speed(TaskCtx& ctx) {
    int64_t dt_ms = now_ms() - ctx.session_start_ms;
    if (dt_ms <= 0) return 0;
    int64_t db = ctx.downloaded_bytes - ctx.session_start_bytes;
    if (db <= 0) return 0;
    return (db * 1000LL) / dt_ms;
}

void DownloadEngine::close_and_erase(int id) {
    auto it = contexts_.find(id);
    if (it == contexts_.end()) return;
    if (it->second.fd >= 0) {
        close(it->second.fd);
        it->second.fd = -1;
    }
    contexts_.erase(it);
}

// ── prepare ───────────────────────────────────────────────────────────────────

int64_t DownloadEngine::prepare(int id, const char* dest_path) {
    std::string temp = std::string(dest_path) + ".hxd_tmp";

    // Detect partial file for resume
    int64_t resume_offset = 0;
    struct stat st{};
    if (stat(temp.c_str(), &st) == 0 && st.st_size > 0) {
        resume_offset = st.st_size;
    }

    int flags = O_WRONLY | O_CREAT | (resume_offset > 0 ? O_APPEND : O_TRUNC);
    int fd = open(temp.c_str(), flags, 0600);
    if (fd < 0) return -1;

    std::lock_guard<std::mutex> lk(mutex_);
    TaskCtx ctx;
    ctx.fd                  = fd;
    ctx.temp_path           = temp;
    ctx.dest_path           = dest_path;
    ctx.downloaded_bytes    = resume_offset;
    ctx.session_start_ms    = now_ms();
    ctx.session_start_bytes = resume_offset;
    contexts_[id]           = std::move(ctx);

    return resume_offset;
}

// ── set_total ─────────────────────────────────────────────────────────────────

void DownloadEngine::set_total(int id, int64_t total_bytes) {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = contexts_.find(id);
    if (it != contexts_.end()) it->second.total_bytes = total_bytes;
}

// ── write_chunk ───────────────────────────────────────────────────────────────

bool DownloadEngine::write_chunk(int id, const uint8_t* data, int offset, int len) {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = contexts_.find(id);
    if (it == contexts_.end()) return false;

    TaskCtx& ctx = it->second;
    if (ctx.cancel_requested || ctx.pause_requested) return false;

    const uint8_t* ptr = data + offset;
    int remaining = len;
    while (remaining > 0) {
        ssize_t w = write(ctx.fd, ptr, (size_t)remaining);
        if (w <= 0) return false;  // I/O error or disk full
        ptr      += w;
        remaining -= (int)w;
    }
    ctx.downloaded_bytes += len;
    return true;
}

// ── complete ──────────────────────────────────────────────────────────────────

bool DownloadEngine::complete(int id) {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = contexts_.find(id);
    if (it == contexts_.end()) return false;

    TaskCtx& ctx = it->second;
    if (ctx.fd >= 0) {
        fsync(ctx.fd);
        close(ctx.fd);
        ctx.fd = -1;
    }

    // Atomic rename: temp → final destination
    int ret = rename(ctx.temp_path.c_str(), ctx.dest_path.c_str());
    contexts_.erase(it);
    return ret == 0;
}

// ── fail ──────────────────────────────────────────────────────────────────────

void DownloadEngine::fail(int id) {
    std::lock_guard<std::mutex> lk(mutex_);
    close_and_erase(id);
    // Temp file is intentionally preserved — allows retry / resume on next attempt.
    // Caller (Kotlin) deletes it explicitly on cancel.
}

// ── cancel / pause ────────────────────────────────────────────────────────────

void DownloadEngine::cancel(int id) {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = contexts_.find(id);
    if (it != contexts_.end()) it->second.cancel_requested = true;
}

void DownloadEngine::pause(int id) {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = contexts_.find(id);
    if (it != contexts_.end()) it->second.pause_requested = true;
}

// ── get_progress ──────────────────────────────────────────────────────────────

ProgressInfo DownloadEngine::get_progress(int id) {
    std::lock_guard<std::mutex> lk(mutex_);
    auto it = contexts_.find(id);
    if (it == contexts_.end()) {
        return {id, 0, -1, 0, TaskState::FAILED};
    }
    TaskCtx& ctx = it->second;
    TaskState state = ctx.cancel_requested ? TaskState::CANCELLED :
                      ctx.pause_requested  ? TaskState::PAUSED    :
                                             TaskState::DOWNLOADING;
    return {id, ctx.downloaded_bytes, ctx.total_bytes, calc_speed(ctx), state};
}

// ── cleanup ───────────────────────────────────────────────────────────────────

void DownloadEngine::cleanup(int id) {
    std::lock_guard<std::mutex> lk(mutex_);
    close_and_erase(id);
    // Temp file preserved — use for resume.
}

} // namespace hxd

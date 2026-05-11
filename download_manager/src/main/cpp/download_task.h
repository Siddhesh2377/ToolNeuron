#pragma once
#include <cstdint>

namespace hxd {

// Must stay in sync with HxdStatus enum ordinals in Kotlin
enum class TaskState : uint8_t {
    QUEUED      = 0,
    CONNECTING  = 1,
    DOWNLOADING = 2,
    PAUSED      = 3,
    COMPLETED   = 4,
    FAILED      = 5,
    CANCELLED   = 6
};

struct ProgressInfo {
    int       task_id;
    int64_t   downloaded_bytes;
    int64_t   total_bytes;   // -1 = unknown (no Content-Length)
    int64_t   speed_bps;     // rolling average bytes/sec
    TaskState state;
};

} // namespace hxd

#pragma once
#include "download_task.h"
#include <string>
#include <vector>

namespace hxd {

// Flat binary format for persisting the download queue across process restarts.
//
// File layout:
//   [4]  magic   = 0x48584451  ("HXDQ")
//   [4]  version = 1
//   [4]  count
//   per task:
//     [4]  id
//     [1]  state  (TaskState ordinal)
//     [8]  downloaded_bytes
//     [8]  total_bytes  (-1 = unknown)
//     [2]  url_len
//     [N]  url
//     [2]  dest_path_len
//     [N]  dest_path
//
// All multi-byte integers are native-endian (device-local, never shared off-device).

struct PersistedTask {
    int         id;
    TaskState   state;
    int64_t     downloaded_bytes;
    int64_t     total_bytes;
    std::string url;
    std::string dest_path;
};

bool                      save_queue(const char* path, const std::vector<PersistedTask>& tasks);
std::vector<PersistedTask> load_queue(const char* path);

} // namespace hxd

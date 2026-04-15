#pragma once

#include "path_guard.h"
#include <cstdint>
#include <string>
#include <vector>

namespace fo {

struct ReadResult {
    std::vector<uint8_t> data;
    bool success;
};

struct WriteResult {
    bool success;
};

class IOEngine {
public:
    explicit IOEngine(PathGuard guard);

    // Initialize: create base dir with 0700 permissions
    bool init();

    // Atomic write: data -> .tmp -> fsync -> rename -> fsync parent
    WriteResult write(std::string_view rel_path, const uint8_t* data, size_t len);

    // Append to file (for WAL)
    WriteResult append(std::string_view rel_path, const uint8_t* data, size_t len);

    // Read file (or portion)
    ReadResult read(std::string_view rel_path, size_t offset = 0, size_t length = 0);

    // Secure delete: zero-fill then unlink
    bool remove(std::string_view rel_path);

    bool exists(std::string_view rel_path) const;
    int64_t file_size(std::string_view rel_path) const;
    bool make_dir(std::string_view rel_path);
    std::vector<std::string> list_dir(std::string_view rel_path) const;
    bool rename_file(std::string_view from, std::string_view to);
    bool fsync_file(std::string_view rel_path);

private:
    PathGuard guard_;

    bool set_permissions(const std::string& path, mode_t mode);
    bool fsync_path(const std::string& abs_path);
    bool fsync_parent(const std::string& abs_path);
};

} // namespace fo

#include "path_guard.h"
#include <sys/stat.h>

namespace fo {

PathGuard::PathGuard(std::string base_path) : base_path_(std::move(base_path)) {
    // Ensure base ends with /
    if (!base_path_.empty() && base_path_.back() != '/') {
        base_path_ += '/';
    }
}

std::string PathGuard::resolve(std::string_view relative_path) const {
    if (!is_safe(relative_path)) return {};
    return base_path_ + std::string(relative_path);
}

bool PathGuard::is_safe(std::string_view path) const {
    if (path.empty()) return false;

    // Reject absolute paths
    if (path[0] == '/') return false;

    // Reject traversal
    if (has_traversal(path)) return false;

    // Reject overly long paths
    if (base_path_.size() + path.size() > 256) return false;

    // Build full path and check for symlinks
    std::string full = base_path_ + std::string(path);
    if (is_symlink(full)) return false;

    return true;
}

bool PathGuard::has_traversal(std::string_view path) const {
    // Check for ".." component
    if (path == "..") return true;
    if (path.find("../") != std::string_view::npos) return true;
    if (path.find("/..") != std::string_view::npos) return true;
    // Check for null bytes
    if (path.find('\0') != std::string_view::npos) return true;
    return false;
}

bool PathGuard::is_symlink(const std::string& path) const {
    struct stat st;
    if (lstat(path.c_str(), &st) == 0) {
        return S_ISLNK(st.st_mode);
    }
    return false; // File doesn't exist yet — not a symlink
}

} // namespace fo

#pragma once

#include <string>
#include <string_view>

namespace fo {

class PathGuard {
public:
    explicit PathGuard(std::string base_path);

    // Returns resolved absolute path, or empty string if invalid
    std::string resolve(std::string_view relative_path) const;

    // Check if path is safe (no traversal, no symlinks, within jail)
    bool is_safe(std::string_view relative_path) const;

    const std::string& base() const noexcept { return base_path_; }

private:
    std::string base_path_;

    bool has_traversal(std::string_view path) const;
    bool is_symlink(const std::string& path) const;
};

} // namespace fo

#include "io_engine.h"

#include <android/log.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstring>
#include <algorithm>

#define LOG_TAG "file_ops"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace fo {

IOEngine::IOEngine(PathGuard guard) : guard_(std::move(guard)) {}

bool IOEngine::init() {
    const auto& base = guard_.base();
    if (mkdir(base.c_str(), 0700) != 0 && errno != EEXIST) {
        LOGE("Failed to create base dir: %s", strerror(errno));
        return false;
    }
    return set_permissions(base, 0700);
}

WriteResult IOEngine::write(std::string_view rel_path, const uint8_t* data, size_t len) {
    auto abs = guard_.resolve(rel_path);
    if (abs.empty()) return {false};

    std::string tmp = abs + ".tmp";

    int fd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) {
        LOGE("write open failed: %s", strerror(errno));
        return {false};
    }

    size_t written = 0;
    while (written < len) {
        ssize_t n = ::write(fd, data + written, len - written);
        if (n < 0) {
            if (errno == EINTR) continue;
            LOGE("write failed: %s", strerror(errno));
            close(fd);
            unlink(tmp.c_str());
            return {false};
        }
        written += n;
    }

    if (::fsync(fd) != 0) {
        LOGE("fsync failed: %s", strerror(errno));
        close(fd);
        unlink(tmp.c_str());
        return {false};
    }
    close(fd);

    if (::rename(tmp.c_str(), abs.c_str()) != 0) {
        LOGE("rename failed: %s", strerror(errno));
        unlink(tmp.c_str());
        return {false};
    }

    set_permissions(abs, 0600);
    fsync_parent(abs);
    return {true};
}

WriteResult IOEngine::append(std::string_view rel_path, const uint8_t* data, size_t len) {
    auto abs = guard_.resolve(rel_path);
    if (abs.empty()) return {false};

    int fd = open(abs.c_str(), O_WRONLY | O_CREAT | O_APPEND, 0600);
    if (fd < 0) {
        LOGE("append open failed: %s", strerror(errno));
        return {false};
    }

    size_t written = 0;
    while (written < len) {
        ssize_t n = ::write(fd, data + written, len - written);
        if (n < 0) {
            if (errno == EINTR) continue;
            close(fd);
            return {false};
        }
        written += n;
    }

    ::fsync(fd);
    close(fd);
    set_permissions(abs, 0600);
    return {true};
}

ReadResult IOEngine::read(std::string_view rel_path, size_t offset, size_t length) {
    auto abs = guard_.resolve(rel_path);
    if (abs.empty()) return {{}, false};

    int fd = open(abs.c_str(), O_RDONLY);
    if (fd < 0) return {{}, false};

    struct stat st;
    if (fstat(fd, &st) != 0) {
        close(fd);
        return {{}, false};
    }

    size_t total = static_cast<size_t>(st.st_size);
    if (offset >= total) {
        close(fd);
        return {{}, true}; // empty but success
    }

    size_t to_read = (length == 0) ? (total - offset) : std::min(length, total - offset);

    std::vector<uint8_t> buf(to_read);
    if (offset > 0) lseek(fd, static_cast<off_t>(offset), SEEK_SET);

    size_t got = 0;
    while (got < to_read) {
        ssize_t n = ::read(fd, buf.data() + got, to_read - got);
        if (n < 0) {
            if (errno == EINTR) continue;
            close(fd);
            return {{}, false};
        }
        if (n == 0) break;
        got += n;
    }

    close(fd);
    buf.resize(got);
    return {std::move(buf), true};
}

bool IOEngine::remove(std::string_view rel_path) {
    auto abs = guard_.resolve(rel_path);
    if (abs.empty()) return false;

    // Zero-fill before unlinking
    struct stat st;
    if (stat(abs.c_str(), &st) == 0 && S_ISREG(st.st_mode)) {
        int fd = open(abs.c_str(), O_WRONLY);
        if (fd >= 0) {
            std::vector<uint8_t> zeros(static_cast<size_t>(st.st_size), 0);
            ::write(fd, zeros.data(), zeros.size());
            ::fsync(fd);
            close(fd);
        }
    }

    return unlink(abs.c_str()) == 0;
}

bool IOEngine::exists(std::string_view rel_path) const {
    auto abs = guard_.resolve(rel_path);
    if (abs.empty()) return false;
    struct stat st;
    return stat(abs.c_str(), &st) == 0;
}

int64_t IOEngine::file_size(std::string_view rel_path) const {
    auto abs = guard_.resolve(rel_path);
    if (abs.empty()) return -1;
    struct stat st;
    if (stat(abs.c_str(), &st) != 0) return -1;
    return static_cast<int64_t>(st.st_size);
}

bool IOEngine::make_dir(std::string_view rel_path) {
    auto abs = guard_.resolve(rel_path);
    if (abs.empty()) return false;
    if (mkdir(abs.c_str(), 0700) != 0 && errno != EEXIST) return false;
    return set_permissions(abs, 0700);
}

std::vector<std::string> IOEngine::list_dir(std::string_view rel_path) const {
    auto abs = guard_.resolve(rel_path);
    if (abs.empty()) return {};

    DIR* dir = opendir(abs.c_str());
    if (!dir) return {};

    std::vector<std::string> entries;
    struct dirent* ent;
    while ((ent = readdir(dir)) != nullptr) {
        if (ent->d_name[0] == '.') continue; // skip . and ..
        entries.emplace_back(ent->d_name);
    }
    closedir(dir);
    return entries;
}

bool IOEngine::rename_file(std::string_view from, std::string_view to) {
    auto abs_from = guard_.resolve(from);
    auto abs_to = guard_.resolve(to);
    if (abs_from.empty() || abs_to.empty()) return false;
    return ::rename(abs_from.c_str(), abs_to.c_str()) == 0;
}

bool IOEngine::fsync_file(std::string_view rel_path) {
    auto abs = guard_.resolve(rel_path);
    if (abs.empty()) return false;
    return fsync_path(abs);
}

bool IOEngine::set_permissions(const std::string& path, mode_t mode) {
    return chmod(path.c_str(), mode) == 0;
}

bool IOEngine::fsync_path(const std::string& abs_path) {
    int fd = open(abs_path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    int r = ::fsync(fd);
    close(fd);
    return r == 0;
}

bool IOEngine::fsync_parent(const std::string& abs_path) {
    auto pos = abs_path.rfind('/');
    if (pos == std::string::npos) return false;
    std::string parent = abs_path.substr(0, pos);
    return fsync_path(parent);
}

} // namespace fo

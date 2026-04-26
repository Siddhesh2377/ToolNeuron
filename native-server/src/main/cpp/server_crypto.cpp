#include "server_crypto.h"

#include <cerrno>
#include <cstddef>
#include <cstring>
#include <fcntl.h>
#include <sys/random.h>
#include <sys/syscall.h>
#include <unistd.h>

namespace tn::server::crypto {

    bool random_bytes(uint8_t* out, size_t len) {
        size_t filled = 0;
        while (filled < len) {
            ssize_t n = ::getrandom(out + filled, len - filled, 0);
            if (n > 0) {
                filled += static_cast<size_t>(n);
                continue;
            }
            if (n < 0 && errno == EINTR) continue;
            int fd = ::open("/dev/urandom", O_RDONLY | O_CLOEXEC);
            if (fd < 0) return false;
            while (filled < len) {
                ssize_t r = ::read(fd, out + filled, len - filled);
                if (r > 0) {
                    filled += static_cast<size_t>(r);
                } else if (r < 0 && errno == EINTR) {
                    continue;
                } else {
                    ::close(fd);
                    return false;
                }
            }
            ::close(fd);
            return true;
        }
        return true;
    }

    std::vector<uint8_t> random_bytes(size_t len) {
        std::vector<uint8_t> buf(len);
        if (!random_bytes(buf.data(), len)) buf.clear();
        return buf;
    }

    bool const_time_eq(const uint8_t* a, const uint8_t* b, size_t len) {
        volatile uint8_t diff = 0;
        for (size_t i = 0; i < len; ++i) {
            diff = static_cast<uint8_t>(diff | (a[i] ^ b[i]));
        }
        return diff == 0;
    }

    bool const_time_eq(const std::vector<uint8_t>& a, const std::vector<uint8_t>& b) {
        if (a.size() != b.size()) return false;
        return const_time_eq(a.data(), b.data(), a.size());
    }

    static constexpr char kUrlAlpha[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    std::string to_base64url(const uint8_t* data, size_t len) {
        std::string out;
        out.reserve(((len + 2) / 3) * 4);
        size_t i = 0;
        while (i + 3 <= len) {
            uint32_t v = (static_cast<uint32_t>(data[i]) << 16) |
                         (static_cast<uint32_t>(data[i + 1]) << 8) |
                         static_cast<uint32_t>(data[i + 2]);
            out.push_back(kUrlAlpha[(v >> 18) & 0x3F]);
            out.push_back(kUrlAlpha[(v >> 12) & 0x3F]);
            out.push_back(kUrlAlpha[(v >> 6) & 0x3F]);
            out.push_back(kUrlAlpha[v & 0x3F]);
            i += 3;
        }
        if (i < len) {
            uint32_t v = static_cast<uint32_t>(data[i]) << 16;
            if (i + 1 < len) v |= static_cast<uint32_t>(data[i + 1]) << 8;
            out.push_back(kUrlAlpha[(v >> 18) & 0x3F]);
            out.push_back(kUrlAlpha[(v >> 12) & 0x3F]);
            if (i + 1 < len) out.push_back(kUrlAlpha[(v >> 6) & 0x3F]);
        }
        return out;
    }

    std::string to_base64url(const std::vector<uint8_t>& data) {
        return to_base64url(data.data(), data.size());
    }

    void secure_zero(void* ptr, size_t len) {
        volatile uint8_t* p = static_cast<volatile uint8_t*>(ptr);
        while (len--) *p++ = 0;
    }

}

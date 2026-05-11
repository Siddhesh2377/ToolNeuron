#include "server_staging.h"

#include "server_crypto.h"

#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstdio>
#include <fstream>
#include <mutex>

namespace tn::server::staging {

    namespace {
        std::mutex  g_mu;
        std::string g_dir;
    }

    void set_dir(const std::string& path) {
        std::lock_guard<std::mutex> lock(g_mu);
        g_dir = path;
        if (!g_dir.empty()) {
            ::mkdir(g_dir.c_str(), 0700);
        }
    }

    std::string dir() {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_dir;
    }

    std::string make_path(const std::string& suffix) {
        std::string base;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            if (g_dir.empty()) return {};
            base = g_dir;
        }
        auto raw = tn::server::crypto::random_bytes(12);
        std::string token = raw.empty() ? "x" : tn::server::crypto::to_base64url(raw);
        std::string sanitized;
        sanitized.reserve(suffix.size());
        for (char ch : suffix) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
                (ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '_') {
                sanitized.push_back(ch);
            }
        }
        if (sanitized.empty()) sanitized = "bin";
        return base + "/tn_" + token + "_" + sanitized;
    }

    bool write_bytes(const std::string& path, const uint8_t* data, size_t len) {
        std::ofstream f(path, std::ios::binary | std::ios::trunc);
        if (!f) return false;
        f.write(reinterpret_cast<const char*>(data), static_cast<std::streamsize>(len));
        f.flush();
        return f.good();
    }

    bool read_bytes(const std::string& path, std::vector<uint8_t>& out, size_t max_len) {
        std::ifstream f(path, std::ios::binary | std::ios::ate);
        if (!f) return false;
        std::streamsize size = f.tellg();
        if (size < 0) return false;
        size_t sz = static_cast<size_t>(size);
        if (max_len > 0 && sz > max_len) return false;
        f.seekg(0, std::ios::beg);
        out.assign(sz, 0);
        if (sz > 0) {
            f.read(reinterpret_cast<char*>(out.data()), static_cast<std::streamsize>(sz));
        }
        return f.good() || f.eof();
    }

    void unlink_safe(const std::string& path) {
        if (path.empty()) return;
        ::unlink(path.c_str());
    }

    void purge_all() {
        std::string base;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            base = g_dir;
        }
        if (base.empty()) return;
        DIR* d = ::opendir(base.c_str());
        if (!d) return;
        struct dirent* e;
        while ((e = ::readdir(d)) != nullptr) {
            std::string name = e->d_name;
            if (name == "." || name == "..") continue;
            if (name.rfind("tn_", 0) != 0) continue;
            std::string full = base + "/" + name;
            ::unlink(full.c_str());
        }
        ::closedir(d);
    }

}

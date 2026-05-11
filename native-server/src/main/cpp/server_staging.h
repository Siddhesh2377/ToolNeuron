#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace tn::server::staging {

    void set_dir(const std::string& path);
    std::string dir();

    std::string make_path(const std::string& suffix);

    bool write_bytes(const std::string& path, const uint8_t* data, size_t len);
    bool read_bytes(const std::string& path, std::vector<uint8_t>& out, size_t max_len);

    void unlink_safe(const std::string& path);

    void purge_all();

}

#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace tn::server::crypto {

    bool random_bytes(uint8_t* out, size_t len);
    std::vector<uint8_t> random_bytes(size_t len);

    bool const_time_eq(const uint8_t* a, const uint8_t* b, size_t len);
    bool const_time_eq(const std::vector<uint8_t>& a, const std::vector<uint8_t>& b);

    std::string to_base64url(const uint8_t* data, size_t len);
    std::string to_base64url(const std::vector<uint8_t>& data);

    void secure_zero(void* ptr, size_t len);

}

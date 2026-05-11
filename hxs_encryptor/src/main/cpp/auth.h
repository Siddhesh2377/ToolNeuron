#pragma once

#include "policy_engine.h"

#include <array>
#include <cstdint>
#include <cstddef>

namespace hxs {
namespace auth {

constexpr uint32_t ARGON2_T_COST   = 4;
constexpr uint32_t ARGON2_M_COST   = 131072;
constexpr uint32_t ARGON2_P_COST   = 1;
constexpr size_t   HASH_LEN        = 32;
constexpr size_t   SALT_LEN        = 16;
constexpr size_t   TOKEN_LEN       = hxs::policy::SESSION_TOKEN_LEN;

struct SetupResult {
    bool success = false;
    std::array<uint8_t, SALT_LEN> salt{};
    std::array<uint8_t, HASH_LEN> hash{};
};

struct VerifyResult {
    bool success = false;
    std::array<uint8_t, TOKEN_LEN> token{};
};

SetupResult setup(const uint8_t* pin, size_t pin_len);

VerifyResult verify(
    const uint8_t* pin, size_t pin_len,
    const uint8_t* salt, size_t salt_len,
    const uint8_t* stored_hash, size_t stored_hash_len
);

void invalidate();

}
}

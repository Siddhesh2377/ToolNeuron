#pragma once

#include <cstddef>
#include <cstdint>

namespace hxs {
namespace obf {

constexpr uint8_t XOR_KEY = 0x5A;

template<size_t N>
struct XorLit {
    uint8_t enc[N];

    constexpr XorLit(const char (&s)[N]) : enc{} {
        for (size_t i = 0; i < N; i++) {
            enc[i] = static_cast<uint8_t>(s[i]) ^ XOR_KEY;
        }
    }

    void decode(char* out) const {
        for (size_t i = 0; i < N; i++) {
            out[i] = static_cast<char>(enc[i] ^ XOR_KEY);
        }
    }

    static constexpr size_t size() { return N; }
};

template<size_t N>
constexpr XorLit<N> make_xor(const char (&s)[N]) {
    return XorLit<N>(s);
}

}
}

#define HXS_OBF(var_name, literal)                                         \
    static constexpr auto HXS_OBF_ENC_##var_name = ::hxs::obf::make_xor(literal); \
    char var_name[HXS_OBF_ENC_##var_name.size()];                          \
    HXS_OBF_ENC_##var_name.decode(var_name)

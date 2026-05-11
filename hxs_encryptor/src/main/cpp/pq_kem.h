#pragma once

#include "memory_guard.h"
#include <cstdint>
#include <cstddef>
#include <vector>

namespace hxs {

constexpr size_t PQ_X25519_KEY_SIZE = 32;

// ML-KEM-768 (Kyber) key sizes
constexpr size_t MLKEM768_PUBLIC_KEY_SIZE  = 1184;
constexpr size_t MLKEM768_SECRET_KEY_SIZE  = 2400;
constexpr size_t MLKEM768_CIPHERTEXT_SIZE  = 1088;
constexpr size_t MLKEM768_SHARED_SECRET_SIZE = 32;

// Hybrid KEM: X25519 + ML-KEM-768
constexpr size_t HYBRID_KEM_PUBLIC_KEY_SIZE = PQ_X25519_KEY_SIZE + MLKEM768_PUBLIC_KEY_SIZE;   // 1216
constexpr size_t HYBRID_KEM_SECRET_KEY_SIZE = PQ_X25519_KEY_SIZE + MLKEM768_SECRET_KEY_SIZE;   // 2432
constexpr size_t HYBRID_KEM_CIPHERTEXT_SIZE = PQ_X25519_KEY_SIZE + MLKEM768_CIPHERTEXT_SIZE;   // 1120
constexpr size_t HYBRID_KEM_SHARED_SECRET_SIZE = 32; // HKDF output

struct HybridKemKeyPair {
    std::vector<uint8_t> public_key;   // [x25519_pub(32) || mlkem_pub(1184)]
    SecureBuffer secret_key;           // [x25519_priv(32) || mlkem_sk(2400)]
    bool success;
};

struct HybridKemEncapsResult {
    std::vector<uint8_t> ciphertext;   // [x25519_eph_pub(32) || mlkem_ct(1088)]
    SecureBuffer shared_secret;        // 32 bytes from HKDF
    bool success;
};

struct HybridKemDecapsResult {
    SecureBuffer shared_secret;        // 32 bytes from HKDF
    bool success;
};

class HybridKem {
public:
    // Generate a hybrid keypair (X25519 + ML-KEM-768)
    static HybridKemKeyPair keygen();

    // Encapsulate: produce ciphertext + shared secret from recipient's public key
    static HybridKemEncapsResult encaps(const uint8_t* public_key, size_t pk_len);

    // Decapsulate: recover shared secret from ciphertext + own secret key
    static HybridKemDecapsResult decaps(
        const uint8_t* ciphertext, size_t ct_len,
        const uint8_t* secret_key, size_t sk_len
    );
};

} // namespace hxs

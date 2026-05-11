#pragma once

#include "memory_guard.h"
#include <cstdint>
#include <cstddef>
#include <vector>

namespace hxs {

// ML-DSA-65 (Dilithium) sizes
constexpr size_t MLDSA65_PUBLIC_KEY_SIZE = 1952;
constexpr size_t MLDSA65_SECRET_KEY_SIZE = 4032;
constexpr size_t MLDSA65_SIGNATURE_SIZE  = 3309;

// Hybrid signature: Ed25519 + ML-DSA-65
constexpr size_t HYBRID_SIG_PUBLIC_KEY_SIZE = 32 + MLDSA65_PUBLIC_KEY_SIZE;   // 1984
constexpr size_t HYBRID_SIG_SECRET_KEY_SIZE = 64 + MLDSA65_SECRET_KEY_SIZE;   // 4096
constexpr size_t HYBRID_SIG_SIGNATURE_SIZE  = 64 + MLDSA65_SIGNATURE_SIZE;    // 3373

struct HybridSigKeyPair {
    std::vector<uint8_t> public_key;  // [ed25519_pub(32) || mldsa_pub(1952)]
    SecureBuffer secret_key;          // [ed25519_priv(64) || mldsa_sk(4032)]
    bool success;
};

struct HybridSignResult {
    std::vector<uint8_t> signature;   // [ed25519_sig(64) || mldsa_sig(<=3309)]
    bool success;
};

class HybridSign {
public:
    // Generate hybrid keypair (Ed25519 + ML-DSA-65)
    static HybridSigKeyPair keygen();

    // Sign message with both algorithms
    static HybridSignResult sign(
        const uint8_t* message, size_t message_len,
        const uint8_t* secret_key, size_t sk_len
    );

    // Verify: both signatures must be valid
    static bool verify(
        const uint8_t* message, size_t message_len,
        const uint8_t* signature, size_t sig_len,
        const uint8_t* public_key, size_t pk_len
    );
};

} // namespace hxs

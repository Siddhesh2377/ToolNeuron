#pragma once

#include "memory_guard.h"
#include <cstdint>
#include <vector>

namespace tn {

constexpr size_t AES_KEY_SIZE = 32;
constexpr size_t GCM_NONCE_SIZE = 12;
constexpr size_t GCM_TAG_SIZE = 16;
constexpr size_t ED25519_PUBLIC_KEY_SIZE = 32;
constexpr size_t ED25519_PRIVATE_KEY_SIZE = 64;
constexpr size_t ED25519_SIGNATURE_SIZE = 64;
constexpr size_t X25519_KEY_SIZE = 32;

struct EncryptResult {
    std::vector<uint8_t> sealed_data;
    bool success;
};

struct DecryptResult {
    SecureBuffer plaintext;
    bool success;
};

class CryptoEngine {
public:
    CryptoEngine();
    ~CryptoEngine();

    EncryptResult encrypt_aes_gcm(
        const uint8_t* plaintext, size_t plaintext_len,
        const uint8_t* key, size_t key_len,
        const uint8_t* aad, size_t aad_len
    );

    DecryptResult decrypt_aes_gcm(
        const uint8_t* sealed_data, size_t sealed_len,
        const uint8_t* key, size_t key_len,
        const uint8_t* aad, size_t aad_len
    );

    bool sign_ed25519(
        const uint8_t* message, size_t message_len,
        const uint8_t* private_key,
        uint8_t* signature_out
    );

    bool verify_ed25519(
        const uint8_t* message, size_t message_len,
        const uint8_t* signature,
        const uint8_t* public_key
    );

    bool x25519_shared_secret(
        const uint8_t* private_key,
        const uint8_t* peer_public,
        uint8_t* shared_out
    );

    bool hkdf_sha256(
        const uint8_t* ikm, size_t ikm_len,
        const uint8_t* salt, size_t salt_len,
        const uint8_t* info, size_t info_len,
        uint8_t* output, size_t output_len
    );

    bool pbkdf2_sha256(
        const uint8_t* password, size_t password_len,
        const uint8_t* salt, size_t salt_len,
        uint32_t iterations,
        uint8_t* output, size_t output_len
    );

    bool random_bytes(uint8_t* buf, size_t len);
};

} // namespace tn

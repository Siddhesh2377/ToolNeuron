#pragma once

#include "memory_guard.h"
#include <cstdint>
#include <vector>

namespace hxs {

constexpr size_t KEY_SIZE          = 32;
constexpr size_t GCM_NONCE_SIZE    = 12;
constexpr size_t GCM_TAG_SIZE      = 16;
constexpr size_t CHACHA_NONCE_SIZE = 12;
constexpr size_t CHACHA_TAG_SIZE   = 16;

constexpr size_t ED25519_PUBLIC_KEY_SIZE  = 32;
constexpr size_t ED25519_PRIVATE_KEY_SIZE = 64;
constexpr size_t ED25519_SIGNATURE_SIZE   = 64;
constexpr size_t X25519_KEY_SIZE          = 32;

constexpr size_t ARGON2_SALT_SIZE   = 16;
constexpr size_t ARGON2_HASH_SIZE   = 32;
constexpr uint32_t ARGON2_T_COST    = 3;       // iterations
constexpr uint32_t ARGON2_M_COST    = 65536;   // 64 MB
constexpr uint32_t ARGON2_P_COST    = 4;       // parallelism

enum class CipherSuite : uint8_t {
    AES_256_GCM        = 0,
    CHACHA20_POLY1305  = 1,
    AUTO               = 255,
};

struct EncryptResult {
    std::vector<uint8_t> sealed_data; // [1-byte cipher_id][nonce][ciphertext+tag]
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

    // Returns the optimal cipher for this device (cached on first call)
    CipherSuite detect_optimal_cipher();

    // Encrypt/decrypt with explicit or auto cipher selection
    EncryptResult encrypt(
        const uint8_t* plaintext, size_t plaintext_len,
        const uint8_t* key, size_t key_len,
        const uint8_t* aad, size_t aad_len,
        CipherSuite suite = CipherSuite::AUTO
    );

    DecryptResult decrypt(
        const uint8_t* sealed_data, size_t sealed_len,
        const uint8_t* key, size_t key_len,
        const uint8_t* aad, size_t aad_len
    );

    // AES-256-GCM (direct)
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

    // ChaCha20-Poly1305 (direct)
    EncryptResult encrypt_chacha20(
        const uint8_t* plaintext, size_t plaintext_len,
        const uint8_t* key, size_t key_len,
        const uint8_t* aad, size_t aad_len
    );

    DecryptResult decrypt_chacha20(
        const uint8_t* sealed_data, size_t sealed_len,
        const uint8_t* key, size_t key_len,
        const uint8_t* aad, size_t aad_len
    );

    // Ed25519 signatures
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

    // X25519 key exchange
    bool x25519_shared_secret(
        const uint8_t* private_key,
        const uint8_t* peer_public,
        uint8_t* shared_out
    );

    // HKDF-SHA256
    bool hkdf_sha256(
        const uint8_t* ikm, size_t ikm_len,
        const uint8_t* salt, size_t salt_len,
        const uint8_t* info, size_t info_len,
        uint8_t* output, size_t output_len
    );

    // PBKDF2-SHA256 (fallback KDF)
    bool pbkdf2_sha256(
        const uint8_t* password, size_t password_len,
        const uint8_t* salt, size_t salt_len,
        uint32_t iterations,
        uint8_t* output, size_t output_len
    );

    // Argon2id (primary KDF)
    bool argon2id(
        const uint8_t* password, size_t password_len,
        const uint8_t* salt, size_t salt_len,
        uint32_t t_cost, uint32_t m_cost, uint32_t parallelism,
        uint8_t* output, size_t output_len
    );

    // CSPRNG
    bool random_bytes(uint8_t* buf, size_t len);

    // SHA-256 hash
    bool sha256(const uint8_t* data, size_t len, uint8_t* out_32);

private:
    CipherSuite cached_suite_ = CipherSuite::AUTO;
    bool suite_detected_ = false;

    bool has_aes_hardware();

    EncryptResult encrypt_aead(
        const uint8_t* aead_func_tag, size_t nonce_size,
        const uint8_t* plaintext, size_t plaintext_len,
        const uint8_t* key, size_t key_len,
        const uint8_t* aad, size_t aad_len
    );

    DecryptResult decrypt_aead(
        const uint8_t* aead_func_tag, size_t nonce_size,
        const uint8_t* sealed_data, size_t sealed_len,
        const uint8_t* key, size_t key_len,
        const uint8_t* aad, size_t aad_len
    );
};

} // namespace hxs

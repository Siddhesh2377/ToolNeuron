#include "crypto_engine.h"
#include "memory_guard.h"
#include "argon2.h"

#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/curve25519.h>
#include <openssl/hkdf.h>
#include <openssl/sha.h>
#include <openssl/mem.h>

#include <cstring>

#ifdef __aarch64__
#include <sys/auxv.h>
#include <asm/hwcap.h>
#endif

namespace hxs {

CryptoEngine::CryptoEngine() = default;
CryptoEngine::~CryptoEngine() = default;

bool CryptoEngine::has_aes_hardware() {
#ifdef __aarch64__
    unsigned long hwcaps = getauxval(AT_HWCAP);
    return (hwcaps & HWCAP_AES) != 0;
#elif defined(__x86_64__)
    // x86_64 emulator — always has AES-NI in practice
    return true;
#else
    return false;
#endif
}

CipherSuite CryptoEngine::detect_optimal_cipher() {
    if (suite_detected_) return cached_suite_;
    cached_suite_ = has_aes_hardware() ? CipherSuite::AES_256_GCM
                                       : CipherSuite::CHACHA20_POLY1305;
    suite_detected_ = true;
    return cached_suite_;
}

// Unified AEAD encrypt helper
static EncryptResult do_encrypt_aead(
    const EVP_AEAD* aead, size_t nonce_size, size_t tag_size,
    uint8_t cipher_id,
    const uint8_t* plaintext, size_t plaintext_len,
    const uint8_t* key, size_t key_len,
    const uint8_t* aad, size_t aad_len,
    CryptoEngine& engine
) {
    EncryptResult result{};
    result.success = false;

    if (key_len != KEY_SIZE) return result;

    uint8_t nonce[12];
    if (!engine.random_bytes(nonce, nonce_size)) return result;

    // Format: [cipher_id:1][nonce:12][ciphertext+tag]
    size_t sealed_size = 1 + nonce_size + plaintext_len + tag_size;
    result.sealed_data.resize(sealed_size);
    result.sealed_data[0] = cipher_id;
    std::memcpy(result.sealed_data.data() + 1, nonce, nonce_size);

    EVP_AEAD_CTX ctx;
    if (!EVP_AEAD_CTX_init(&ctx, aead, key, key_len, tag_size, nullptr)) {
        hxs::secure_zero(nonce, sizeof(nonce));
        return result;
    }

    size_t out_len = 0;
    int ok = EVP_AEAD_CTX_seal(
        &ctx,
        result.sealed_data.data() + 1 + nonce_size, &out_len,
        plaintext_len + tag_size,
        nonce, nonce_size,
        plaintext, plaintext_len,
        aad, aad_len
    );

    EVP_AEAD_CTX_cleanup(&ctx);
    hxs::secure_zero(nonce, sizeof(nonce));

    if (!ok) {
        result.sealed_data.clear();
        return result;
    }

    result.sealed_data.resize(1 + nonce_size + out_len);
    result.success = true;
    return result;
}

static DecryptResult do_decrypt_aead(
    const EVP_AEAD* aead, size_t nonce_size, size_t tag_size,
    const uint8_t* ciphertext, size_t ciphertext_len,
    const uint8_t* key, size_t key_len,
    const uint8_t* aad, size_t aad_len
) {
    DecryptResult result{SecureBuffer(0), false};

    if (key_len != KEY_SIZE) return result;
    if (ciphertext_len < nonce_size + tag_size) return result;

    const uint8_t* nonce = ciphertext;
    const uint8_t* ct = ciphertext + nonce_size;
    size_t ct_len = ciphertext_len - nonce_size;
    size_t max_pt = ct_len - tag_size;

    result.plaintext = SecureBuffer(max_pt);

    EVP_AEAD_CTX ctx;
    if (!EVP_AEAD_CTX_init(&ctx, aead, key, key_len, tag_size, nullptr)) {
        return result;
    }

    size_t out_len = 0;
    int ok = EVP_AEAD_CTX_open(
        &ctx,
        result.plaintext.data(), &out_len, max_pt,
        nonce, nonce_size,
        ct, ct_len,
        aad, aad_len
    );

    EVP_AEAD_CTX_cleanup(&ctx);

    if (!ok) {
        result.plaintext.wipe();
        result.plaintext = SecureBuffer(0);
        return result;
    }

    result.success = true;
    return result;
}

// Auto-selecting encrypt: prepends 1-byte cipher ID
EncryptResult CryptoEngine::encrypt(
    const uint8_t* plaintext, size_t plaintext_len,
    const uint8_t* key, size_t key_len,
    const uint8_t* aad, size_t aad_len,
    CipherSuite suite
) {
    if (suite == CipherSuite::AUTO) suite = detect_optimal_cipher();

    if (suite == CipherSuite::AES_256_GCM) {
        return do_encrypt_aead(
            EVP_aead_aes_256_gcm(), GCM_NONCE_SIZE, GCM_TAG_SIZE,
            static_cast<uint8_t>(CipherSuite::AES_256_GCM),
            plaintext, plaintext_len, key, key_len, aad, aad_len, *this);
    } else {
        return do_encrypt_aead(
            EVP_aead_chacha20_poly1305(), CHACHA_NONCE_SIZE, CHACHA_TAG_SIZE,
            static_cast<uint8_t>(CipherSuite::CHACHA20_POLY1305),
            plaintext, plaintext_len, key, key_len, aad, aad_len, *this);
    }
}

// Auto-detecting decrypt: reads cipher ID from first byte
DecryptResult CryptoEngine::decrypt(
    const uint8_t* sealed_data, size_t sealed_len,
    const uint8_t* key, size_t key_len,
    const uint8_t* aad, size_t aad_len
) {
    if (sealed_len < 2) return {SecureBuffer(0), false};

    uint8_t cipher_id = sealed_data[0];
    const uint8_t* inner = sealed_data + 1;
    size_t inner_len = sealed_len - 1;

    if (cipher_id == static_cast<uint8_t>(CipherSuite::AES_256_GCM)) {
        return do_decrypt_aead(
            EVP_aead_aes_256_gcm(), GCM_NONCE_SIZE, GCM_TAG_SIZE,
            inner, inner_len, key, key_len, aad, aad_len);
    } else if (cipher_id == static_cast<uint8_t>(CipherSuite::CHACHA20_POLY1305)) {
        return do_decrypt_aead(
            EVP_aead_chacha20_poly1305(), CHACHA_NONCE_SIZE, CHACHA_TAG_SIZE,
            inner, inner_len, key, key_len, aad, aad_len);
    }

    return {SecureBuffer(0), false};
}

// Direct AES-256-GCM (no cipher ID prefix — raw format)
EncryptResult CryptoEngine::encrypt_aes_gcm(
    const uint8_t* plaintext, size_t plaintext_len,
    const uint8_t* key, size_t key_len,
    const uint8_t* aad, size_t aad_len
) {
    EncryptResult result{};
    result.success = false;
    if (key_len != KEY_SIZE) return result;

    uint8_t nonce[GCM_NONCE_SIZE];
    if (!RAND_bytes(nonce, GCM_NONCE_SIZE)) return result;

    result.sealed_data.resize(GCM_NONCE_SIZE + plaintext_len + GCM_TAG_SIZE);
    std::memcpy(result.sealed_data.data(), nonce, GCM_NONCE_SIZE);

    EVP_AEAD_CTX ctx;
    if (!EVP_AEAD_CTX_init(&ctx, EVP_aead_aes_256_gcm(), key, key_len,
                           GCM_TAG_SIZE, nullptr)) {
        secure_zero(nonce, sizeof(nonce));
        return result;
    }

    size_t out_len = 0;
    int ok = EVP_AEAD_CTX_seal(
        &ctx,
        result.sealed_data.data() + GCM_NONCE_SIZE, &out_len,
        plaintext_len + GCM_TAG_SIZE,
        nonce, GCM_NONCE_SIZE,
        plaintext, plaintext_len,
        aad, aad_len
    );

    EVP_AEAD_CTX_cleanup(&ctx);
    secure_zero(nonce, sizeof(nonce));

    if (!ok) {
        result.sealed_data.clear();
        return result;
    }

    result.sealed_data.resize(GCM_NONCE_SIZE + out_len);
    result.success = true;
    return result;
}

DecryptResult CryptoEngine::decrypt_aes_gcm(
    const uint8_t* sealed_data, size_t sealed_len,
    const uint8_t* key, size_t key_len,
    const uint8_t* aad, size_t aad_len
) {
    return do_decrypt_aead(
        EVP_aead_aes_256_gcm(), GCM_NONCE_SIZE, GCM_TAG_SIZE,
        sealed_data, sealed_len, key, key_len, aad, aad_len);
}

// Direct ChaCha20-Poly1305 (no cipher ID prefix — raw format)
EncryptResult CryptoEngine::encrypt_chacha20(
    const uint8_t* plaintext, size_t plaintext_len,
    const uint8_t* key, size_t key_len,
    const uint8_t* aad, size_t aad_len
) {
    EncryptResult result{};
    result.success = false;
    if (key_len != KEY_SIZE) return result;

    uint8_t nonce[CHACHA_NONCE_SIZE];
    if (!RAND_bytes(nonce, CHACHA_NONCE_SIZE)) return result;

    result.sealed_data.resize(CHACHA_NONCE_SIZE + plaintext_len + CHACHA_TAG_SIZE);
    std::memcpy(result.sealed_data.data(), nonce, CHACHA_NONCE_SIZE);

    EVP_AEAD_CTX ctx;
    if (!EVP_AEAD_CTX_init(&ctx, EVP_aead_chacha20_poly1305(), key, key_len,
                           CHACHA_TAG_SIZE, nullptr)) {
        secure_zero(nonce, sizeof(nonce));
        return result;
    }

    size_t out_len = 0;
    int ok = EVP_AEAD_CTX_seal(
        &ctx,
        result.sealed_data.data() + CHACHA_NONCE_SIZE, &out_len,
        plaintext_len + CHACHA_TAG_SIZE,
        nonce, CHACHA_NONCE_SIZE,
        plaintext, plaintext_len,
        aad, aad_len
    );

    EVP_AEAD_CTX_cleanup(&ctx);
    secure_zero(nonce, sizeof(nonce));

    if (!ok) {
        result.sealed_data.clear();
        return result;
    }

    result.sealed_data.resize(CHACHA_NONCE_SIZE + out_len);
    result.success = true;
    return result;
}

DecryptResult CryptoEngine::decrypt_chacha20(
    const uint8_t* sealed_data, size_t sealed_len,
    const uint8_t* key, size_t key_len,
    const uint8_t* aad, size_t aad_len
) {
    return do_decrypt_aead(
        EVP_aead_chacha20_poly1305(), CHACHA_NONCE_SIZE, CHACHA_TAG_SIZE,
        sealed_data, sealed_len, key, key_len, aad, aad_len);
}

// Ed25519
bool CryptoEngine::sign_ed25519(
    const uint8_t* message, size_t message_len,
    const uint8_t* private_key,
    uint8_t* signature_out
) {
    return ED25519_sign(signature_out, message, message_len, private_key) == 1;
}

bool CryptoEngine::verify_ed25519(
    const uint8_t* message, size_t message_len,
    const uint8_t* signature,
    const uint8_t* public_key
) {
    return ED25519_verify(message, message_len, signature, public_key) == 1;
}

// X25519
bool CryptoEngine::x25519_shared_secret(
    const uint8_t* private_key,
    const uint8_t* peer_public,
    uint8_t* shared_out
) {
    return X25519(shared_out, private_key, peer_public) == 1;
}

// HKDF-SHA256
bool CryptoEngine::hkdf_sha256(
    const uint8_t* ikm, size_t ikm_len,
    const uint8_t* salt, size_t salt_len,
    const uint8_t* info, size_t info_len,
    uint8_t* output, size_t output_len
) {
    return HKDF(output, output_len, EVP_sha256(),
                ikm, ikm_len,
                salt, salt_len,
                info, info_len) == 1;
}

// PBKDF2-SHA256 (fallback)
bool CryptoEngine::pbkdf2_sha256(
    const uint8_t* password, size_t password_len,
    const uint8_t* salt, size_t salt_len,
    uint32_t iterations,
    uint8_t* output, size_t output_len
) {
    return PKCS5_PBKDF2_HMAC(
        reinterpret_cast<const char*>(password), static_cast<int>(password_len),
        salt, static_cast<int>(salt_len),
        static_cast<int>(iterations),
        EVP_sha256(),
        static_cast<int>(output_len),
        output
    ) == 1;
}

// Argon2id (primary KDF)
bool CryptoEngine::argon2id(
    const uint8_t* password, size_t password_len,
    const uint8_t* salt, size_t salt_len,
    uint32_t t_cost, uint32_t m_cost, uint32_t parallelism,
    uint8_t* output, size_t output_len
) {
    int rc = argon2id_hash_raw(
        t_cost, m_cost, parallelism,
        password, password_len,
        salt, salt_len,
        output, output_len
    );
    return rc == ARGON2_OK;
}

// CSPRNG
bool CryptoEngine::random_bytes(uint8_t* buf, size_t len) {
    return RAND_bytes(buf, static_cast<int>(len)) == 1;
}

// SHA-256
bool CryptoEngine::sha256(const uint8_t* data, size_t len, uint8_t* out_32) {
    SHA256(data, len, out_32);
    return true;
}

} // namespace hxs

#include "crypto_engine.h"

#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/curve25519.h>
#include <openssl/hkdf.h>
#include <openssl/mem.h>

#include <cstring>

namespace tn {

CryptoEngine::CryptoEngine() = default;
CryptoEngine::~CryptoEngine() = default;

EncryptResult CryptoEngine::encrypt_aes_gcm(
    const uint8_t* plaintext, size_t plaintext_len,
    const uint8_t* key, size_t key_len,
    const uint8_t* aad, size_t aad_len
) {
    EncryptResult result{};
    result.success = false;

    if (key_len != AES_KEY_SIZE) return result;

    uint8_t nonce[GCM_NONCE_SIZE];
    if (!RAND_bytes(nonce, GCM_NONCE_SIZE)) return result;

    result.sealed_data.resize(GCM_NONCE_SIZE + plaintext_len + GCM_TAG_SIZE);
    std::memcpy(result.sealed_data.data(), nonce, GCM_NONCE_SIZE);

    EVP_AEAD_CTX ctx;
    if (!EVP_AEAD_CTX_init(&ctx, EVP_aead_aes_256_gcm(), key, key_len,
                           GCM_TAG_SIZE, nullptr)) {
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
    DecryptResult result{SecureBuffer(0), false};

    if (key_len != AES_KEY_SIZE) return result;
    if (sealed_len < GCM_NONCE_SIZE + GCM_TAG_SIZE) return result;

    const uint8_t* nonce = sealed_data;
    const uint8_t* ciphertext = sealed_data + GCM_NONCE_SIZE;
    size_t ciphertext_len = sealed_len - GCM_NONCE_SIZE;

    size_t max_plaintext = ciphertext_len - GCM_TAG_SIZE;
    result.plaintext = SecureBuffer(max_plaintext);

    EVP_AEAD_CTX ctx;
    if (!EVP_AEAD_CTX_init(&ctx, EVP_aead_aes_256_gcm(), key, key_len,
                           GCM_TAG_SIZE, nullptr)) {
        return result;
    }

    size_t out_len = 0;
    int ok = EVP_AEAD_CTX_open(
        &ctx,
        result.plaintext.data(), &out_len, max_plaintext,
        nonce, GCM_NONCE_SIZE,
        ciphertext, ciphertext_len,
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

bool CryptoEngine::x25519_shared_secret(
    const uint8_t* private_key,
    const uint8_t* peer_public,
    uint8_t* shared_out
) {
    return X25519(shared_out, private_key, peer_public) == 1;
}

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

bool CryptoEngine::random_bytes(uint8_t* buf, size_t len) {
    return RAND_bytes(buf, static_cast<int>(len)) == 1;
}

} // namespace tn

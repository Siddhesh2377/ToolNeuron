#include "pq_kem.h"
#include "crypto_engine.h"

#include <openssl/curve25519.h>
#include <openssl/rand.h>
#include <oqs/oqs.h>

#include <cstring>

namespace hxs {

HybridKemKeyPair HybridKem::keygen() {
    HybridKemKeyPair result{
        std::vector<uint8_t>(HYBRID_KEM_PUBLIC_KEY_SIZE),
        SecureBuffer(HYBRID_KEM_SECRET_KEY_SIZE),
        false
    };

    // X25519 keypair
    uint8_t x_pub[X25519_KEY_SIZE];
    uint8_t x_priv[X25519_KEY_SIZE];
    RAND_bytes(x_priv, X25519_KEY_SIZE);
    X25519_public_from_private(x_pub, x_priv);

    // ML-KEM-768 keypair
    OQS_KEM* kem = OQS_KEM_new(OQS_KEM_alg_ml_kem_768);
    if (!kem) return result;

    uint8_t* mlkem_pub = result.public_key.data() + X25519_KEY_SIZE;
    uint8_t* mlkem_sk = result.secret_key.data() + X25519_KEY_SIZE;

    if (OQS_KEM_keypair(kem, mlkem_pub, mlkem_sk) != OQS_SUCCESS) {
        OQS_KEM_free(kem);
        return result;
    }
    OQS_KEM_free(kem);

    // Pack public key: [x25519_pub || mlkem_pub]
    memcpy(result.public_key.data(), x_pub, X25519_KEY_SIZE);

    // Pack secret key: [x25519_priv || mlkem_sk]
    memcpy(result.secret_key.data(), x_priv, X25519_KEY_SIZE);

    // Wipe temporaries
    secure_zero(x_priv, X25519_KEY_SIZE);

    result.success = true;
    return result;
}

HybridKemEncapsResult HybridKem::encaps(const uint8_t* public_key, size_t pk_len) {
    HybridKemEncapsResult result{
        std::vector<uint8_t>(HYBRID_KEM_CIPHERTEXT_SIZE),
        SecureBuffer(HYBRID_KEM_SHARED_SECRET_SIZE),
        false
    };

    if (pk_len != HYBRID_KEM_PUBLIC_KEY_SIZE) return result;

    const uint8_t* x_peer_pub = public_key;
    const uint8_t* mlkem_pub = public_key + X25519_KEY_SIZE;

    // X25519: generate ephemeral keypair, compute shared secret
    uint8_t x_eph_priv[X25519_KEY_SIZE];
    uint8_t x_eph_pub[X25519_KEY_SIZE];
    uint8_t x_shared[X25519_KEY_SIZE];

    RAND_bytes(x_eph_priv, X25519_KEY_SIZE);
    X25519_public_from_private(x_eph_pub, x_eph_priv);

    if (!X25519(x_shared, x_eph_priv, x_peer_pub)) {
        secure_zero(x_eph_priv, X25519_KEY_SIZE);
        return result;
    }

    // ML-KEM-768: encapsulate
    OQS_KEM* kem = OQS_KEM_new(OQS_KEM_alg_ml_kem_768);
    if (!kem) {
        secure_zero(x_eph_priv, X25519_KEY_SIZE);
        secure_zero(x_shared, X25519_KEY_SIZE);
        return result;
    }

    uint8_t* mlkem_ct = result.ciphertext.data() + X25519_KEY_SIZE;
    uint8_t mlkem_shared[MLKEM768_SHARED_SECRET_SIZE];

    if (OQS_KEM_encaps(kem, mlkem_ct, mlkem_shared, mlkem_pub) != OQS_SUCCESS) {
        OQS_KEM_free(kem);
        secure_zero(x_eph_priv, X25519_KEY_SIZE);
        secure_zero(x_shared, X25519_KEY_SIZE);
        return result;
    }
    OQS_KEM_free(kem);

    // Pack ciphertext: [x25519_eph_pub || mlkem_ct]
    memcpy(result.ciphertext.data(), x_eph_pub, X25519_KEY_SIZE);

    // Combine shared secrets: HKDF(x_shared || mlkem_shared)
    uint8_t combined[X25519_KEY_SIZE + MLKEM768_SHARED_SECRET_SIZE];
    memcpy(combined, x_shared, X25519_KEY_SIZE);
    memcpy(combined + X25519_KEY_SIZE, mlkem_shared, MLKEM768_SHARED_SECRET_SIZE);

    CryptoEngine engine;
    const char* info = "hxs-hybrid-kem-v1";
    engine.hkdf_sha256(
        combined, sizeof(combined),
        nullptr, 0,
        reinterpret_cast<const uint8_t*>(info), strlen(info),
        result.shared_secret.data(), HYBRID_KEM_SHARED_SECRET_SIZE
    );

    // Wipe all temporaries
    secure_zero(x_eph_priv, X25519_KEY_SIZE);
    secure_zero(x_shared, X25519_KEY_SIZE);
    secure_zero(mlkem_shared, MLKEM768_SHARED_SECRET_SIZE);
    secure_zero(combined, sizeof(combined));

    result.success = true;
    return result;
}

HybridKemDecapsResult HybridKem::decaps(
    const uint8_t* ciphertext, size_t ct_len,
    const uint8_t* secret_key, size_t sk_len
) {
    HybridKemDecapsResult result{
        SecureBuffer(HYBRID_KEM_SHARED_SECRET_SIZE),
        false
    };

    if (ct_len != HYBRID_KEM_CIPHERTEXT_SIZE) return result;
    if (sk_len != HYBRID_KEM_SECRET_KEY_SIZE) return result;

    const uint8_t* x_eph_pub = ciphertext;
    const uint8_t* mlkem_ct = ciphertext + X25519_KEY_SIZE;
    const uint8_t* x_priv = secret_key;
    const uint8_t* mlkem_sk = secret_key + X25519_KEY_SIZE;

    // X25519: compute shared secret
    uint8_t x_shared[X25519_KEY_SIZE];
    if (!X25519(x_shared, x_priv, x_eph_pub)) {
        return result;
    }

    // ML-KEM-768: decapsulate
    OQS_KEM* kem = OQS_KEM_new(OQS_KEM_alg_ml_kem_768);
    if (!kem) {
        secure_zero(x_shared, X25519_KEY_SIZE);
        return result;
    }

    uint8_t mlkem_shared[MLKEM768_SHARED_SECRET_SIZE];
    if (OQS_KEM_decaps(kem, mlkem_shared, mlkem_ct, mlkem_sk) != OQS_SUCCESS) {
        OQS_KEM_free(kem);
        secure_zero(x_shared, X25519_KEY_SIZE);
        return result;
    }
    OQS_KEM_free(kem);

    // Combine: HKDF(x_shared || mlkem_shared)
    uint8_t combined[X25519_KEY_SIZE + MLKEM768_SHARED_SECRET_SIZE];
    memcpy(combined, x_shared, X25519_KEY_SIZE);
    memcpy(combined + X25519_KEY_SIZE, mlkem_shared, MLKEM768_SHARED_SECRET_SIZE);

    CryptoEngine engine;
    const char* info = "hxs-hybrid-kem-v1";
    engine.hkdf_sha256(
        combined, sizeof(combined),
        nullptr, 0,
        reinterpret_cast<const uint8_t*>(info), strlen(info),
        result.shared_secret.data(), HYBRID_KEM_SHARED_SECRET_SIZE
    );

    secure_zero(x_shared, X25519_KEY_SIZE);
    secure_zero(mlkem_shared, MLKEM768_SHARED_SECRET_SIZE);
    secure_zero(combined, sizeof(combined));

    result.success = true;
    return result;
}

} // namespace hxs

#include "pq_sign.h"
#include "memory_guard.h"

#include <openssl/curve25519.h>
#include <openssl/rand.h>
#include <oqs/oqs.h>

#include <cstring>

namespace hxs {

HybridSigKeyPair HybridSign::keygen() {
    HybridSigKeyPair result{
        std::vector<uint8_t>(HYBRID_SIG_PUBLIC_KEY_SIZE),
        SecureBuffer(HYBRID_SIG_SECRET_KEY_SIZE),
        false
    };

    // Ed25519 keypair
    uint8_t ed_pub[32];
    uint8_t ed_seed[32];
    RAND_bytes(ed_seed, 32);

    uint8_t ed_priv[64];
    ED25519_keypair_from_seed(ed_pub, ed_priv, ed_seed);
    secure_zero(ed_seed, 32);

    // ML-DSA-65 keypair
    OQS_SIG* sig = OQS_SIG_new(OQS_SIG_alg_ml_dsa_65);
    if (!sig) return result;

    uint8_t* mldsa_pub = result.public_key.data() + 32;
    uint8_t* mldsa_sk = result.secret_key.data() + 64;

    if (OQS_SIG_keypair(sig, mldsa_pub, mldsa_sk) != OQS_SUCCESS) {
        OQS_SIG_free(sig);
        return result;
    }
    OQS_SIG_free(sig);

    // Pack
    memcpy(result.public_key.data(), ed_pub, 32);
    memcpy(result.secret_key.data(), ed_priv, 64);
    secure_zero(ed_priv, 64);

    result.success = true;
    return result;
}

HybridSignResult HybridSign::sign(
    const uint8_t* message, size_t message_len,
    const uint8_t* secret_key, size_t sk_len
) {
    HybridSignResult result{
        std::vector<uint8_t>(HYBRID_SIG_SIGNATURE_SIZE),
        false
    };

    if (sk_len != HYBRID_SIG_SECRET_KEY_SIZE) return result;

    const uint8_t* ed_priv = secret_key;
    const uint8_t* mldsa_sk = secret_key + 64;

    // Ed25519 sign
    if (!ED25519_sign(result.signature.data(), message, message_len, ed_priv)) {
        return result;
    }

    // ML-DSA-65 sign
    OQS_SIG* sig = OQS_SIG_new(OQS_SIG_alg_ml_dsa_65);
    if (!sig) return result;

    size_t mldsa_sig_len = 0;
    if (OQS_SIG_sign(sig, result.signature.data() + 64, &mldsa_sig_len,
                      message, message_len, mldsa_sk) != OQS_SUCCESS) {
        OQS_SIG_free(sig);
        return result;
    }
    OQS_SIG_free(sig);

    // Trim to actual signature size
    result.signature.resize(64 + mldsa_sig_len);
    result.success = true;
    return result;
}

bool HybridSign::verify(
    const uint8_t* message, size_t message_len,
    const uint8_t* signature, size_t sig_len,
    const uint8_t* public_key, size_t pk_len
) {
    if (pk_len != HYBRID_SIG_PUBLIC_KEY_SIZE) return false;
    if (sig_len < 64 + 1) return false; // minimum: ed25519(64) + at least 1 byte mldsa

    const uint8_t* ed_pub = public_key;
    const uint8_t* mldsa_pub = public_key + 32;
    const uint8_t* ed_sig = signature;
    const uint8_t* mldsa_sig = signature + 64;
    size_t mldsa_sig_len = sig_len - 64;

    // Both must pass
    if (!ED25519_verify(message, message_len, ed_sig, ed_pub)) {
        return false;
    }

    OQS_SIG* sig_ctx = OQS_SIG_new(OQS_SIG_alg_ml_dsa_65);
    if (!sig_ctx) return false;

    OQS_STATUS rc = OQS_SIG_verify(sig_ctx, message, message_len,
                                    mldsa_sig, mldsa_sig_len, mldsa_pub);
    OQS_SIG_free(sig_ctx);

    return rc == OQS_SUCCESS;
}

} // namespace hxs

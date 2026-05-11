#include "auth.h"
#include "crypto_engine.h"
#include "memory_guard.h"
#include "policy_engine.h"

#include <cstring>
#include <openssl/mem.h>

namespace hxs {
namespace auth {

namespace {

hxs::CryptoEngine& engine() {
    static hxs::CryptoEngine e;
    return e;
}

}

SetupResult setup(const uint8_t* pin, size_t pin_len) {
    SetupResult r{};
    if (pin == nullptr || pin_len == 0) return r;

    if (!engine().random_bytes(r.salt.data(), r.salt.size())) return r;

    bool ok = engine().argon2id(
        pin, pin_len,
        r.salt.data(), r.salt.size(),
        ARGON2_T_COST, ARGON2_M_COST, ARGON2_P_COST,
        r.hash.data(), r.hash.size()
    );
    if (!ok) {
        hxs::secure_zero(r.salt.data(), r.salt.size());
        hxs::secure_zero(r.hash.data(), r.hash.size());
        return r;
    }

    r.success = true;
    return r;
}

VerifyResult verify(
    const uint8_t* pin, size_t pin_len,
    const uint8_t* salt, size_t salt_len,
    const uint8_t* stored_hash, size_t stored_hash_len
) {
    VerifyResult r{};
    if (pin == nullptr || pin_len == 0) return r;
    if (salt == nullptr || salt_len == 0) return r;
    if (stored_hash == nullptr || stored_hash_len != HASH_LEN) return r;

    uint8_t derived[HASH_LEN];
    bool ok = engine().argon2id(
        pin, pin_len,
        salt, salt_len,
        ARGON2_T_COST, ARGON2_M_COST, ARGON2_P_COST,
        derived, HASH_LEN
    );
    if (!ok) {
        hxs::secure_zero(derived, HASH_LEN);
        return r;
    }

    int diff = CRYPTO_memcmp(derived, stored_hash, HASH_LEN);
    hxs::secure_zero(derived, HASH_LEN);
    if (diff != 0) return r;

    if (!engine().random_bytes(r.token.data(), r.token.size())) return r;

    hxs::policy::register_session(r.token.data());

    r.success = true;
    return r;
}

void invalidate() {
    hxs::policy::invalidate_session();
}

}
}

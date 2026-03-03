#pragma once

#include "crypto_engine.h"
#include "memory_guard.h"
#include "io_engine.h"

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace ums {

constexpr uint32_t MANIFEST_MAGIC = 0x554D534D; // "UMSM"
constexpr uint16_t FORMAT_VERSION = 1;
constexpr uint32_t DEFAULT_PBKDF2_ITERATIONS = 600000;
constexpr size_t DEK_SIZE = 32;
constexpr size_t PBKDF2_SALT_SIZE = 16;
static constexpr uint16_t FLAG_PLAINTEXT_MODE = 0x0002;

struct CollectionMeta {
    std::string name, filename;
    uint32_t record_count;
    uint64_t last_modified;
};

class Manifest {
public:
    Manifest(fo::IOEngine& io, tn::CryptoEngine& crypto, std::string path);

    bool create(const uint8_t* app_key, const uint8_t* user_key);
    bool open(const uint8_t* app_key, const uint8_t* user_key);

    // Plaintext mode: no encryption, no DEK, no key derivation
    bool create_plaintext();
    bool open_plaintext();
    bool is_plaintext() const;

    // Passphrase-based: PBKDF2 done internally, salt stored in manifest
    bool create_with_passphrase(const uint8_t* app_key,
                                const uint8_t* passphrase, size_t pass_len);
    bool open_with_passphrase(const uint8_t* app_key,
                              const uint8_t* passphrase, size_t pass_len);
    bool exists() const;
    const uint8_t* dek() const;  // only valid after open/create

    void register_collection(const CollectionMeta& meta);
    void update_collection(const std::string& name, uint32_t count, uint64_t modified);
    const std::unordered_map<std::string, CollectionMeta>& collections() const;

    bool save();
    bool change_passphrase(const uint8_t* app_key, const uint8_t* old_user_key,
                           const uint8_t* new_user_key);

    uint16_t flags() const;
    void set_flags(uint16_t f);

private:
    fo::IOEngine& io_;
    tn::CryptoEngine& crypto_;
    std::string path_;
    tn::SecureBuffer dek_{DEK_SIZE};
    std::vector<uint8_t> pbkdf2_salt_;
    uint32_t pbkdf2_iterations_ = DEFAULT_PBKDF2_ITERATIONS;
    std::vector<uint8_t> wrapped_dek_;
    std::vector<uint8_t> key_check_;
    uint16_t flags_ = 0;
    std::unordered_map<std::string, CollectionMeta> collections_;

    // Double-wrap: AES-GCM(AppKey, AES-GCM(UserKey, DEK))
    std::vector<uint8_t> wrap_dek(const uint8_t* app_key, const uint8_t* user_key);
    bool unwrap_dek(const uint8_t* app_key, const uint8_t* user_key,
                    const std::vector<uint8_t>& wrapped);

    // Key check: encrypt known plaintext with app_key, verify on open
    std::vector<uint8_t> make_key_check(const uint8_t* app_key);
    bool verify_key_check(const uint8_t* app_key, const std::vector<uint8_t>& check);

    std::vector<uint8_t> serialize();
    bool deserialize(const std::vector<uint8_t>& data);
};

} // namespace ums

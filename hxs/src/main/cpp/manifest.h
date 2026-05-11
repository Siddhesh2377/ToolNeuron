#pragma once

#include "collection.h"
#include <cstdint>
#include <string>
#include <vector>
#include <unordered_map>
#include <mutex>

namespace hxs {

constexpr uint32_t MANIFEST_MAGIC   = 0x4858534D; // "HXSM"
constexpr uint16_t MANIFEST_VERSION = 1;

constexpr uint16_t FLAG_PLAINTEXT = 0x0002;

/*
 * CollectionMeta — per-collection metadata stored in manifest.
 */
struct CollectionMeta {
    std::string name;
    std::string filename;      // e.g. "chats.hxs"
    uint32_t record_count = 0;
    uint64_t last_modified = 0;
    uint32_t schema_version = 0;
};

/*
 * Manifest — vault-level metadata.
 *
 * On-disk format (manifest.hxs):
 *   [0-3]    magic             0x4858534D
 *   [4-5]    format_version    uint16 LE (=1)
 *   [6-7]    flags             uint16 LE
 *   [8-23]   argon2_salt       16 bytes
 *   [24-27]  argon2_t_cost     uint32 LE
 *   [28-31]  argon2_m_cost     uint32 LE
 *   [32-35]  argon2_p_cost     uint32 LE
 *   [36-39]  wrapped_dek_len   uint32 LE
 *   [40..]   wrapped_dek       double-encrypted DEK
 *   [+0-3]   key_check_len     uint32 LE
 *   [+4..]   key_check         encrypted known plaintext
 *   [+0-1]   collection_count  uint16 LE
 *   Per collection:
 *     [+0-1]   name_len          uint16 LE
 *     [+2..]   name              UTF-8
 *     [+0-1]   filename_len      uint16 LE
 *     [+2..]   filename          UTF-8
 *     [+0-3]   record_count      uint32 LE
 *     [+4-11]  last_modified     uint64 LE
 *     [+12-15] schema_version    uint32 LE
 */
class Manifest {
public:
    explicit Manifest(const std::string& path);

    // Encrypted mode (app_key + user_key derived externally)
    bool create(const uint8_t* app_key, size_t ak_len,
                const uint8_t* user_key, size_t uk_len,
                const CryptoCallbacks& crypto);

    bool open(const uint8_t* app_key, size_t ak_len,
              const uint8_t* user_key, size_t uk_len,
              const CryptoCallbacks& crypto);

    // Plaintext mode (no encryption)
    bool create_plaintext();
    bool open_plaintext();

    bool is_plaintext() const { return (flags_ & FLAG_PLAINTEXT) != 0; }

    // DEK access (32 bytes)
    const uint8_t* dek() const { return dek_; }

    // Collection registry
    void register_collection(const CollectionMeta& meta);
    void unregister_collection(const std::string& name);
    void update_collection(const std::string& name, uint32_t count,
                           uint64_t modified, uint32_t schema_ver);
    const CollectionMeta* get_collection(const std::string& name) const;
    std::vector<CollectionMeta> list_collections() const;
    bool has_collection(const std::string& name) const;

    // Flags
    uint16_t flags() const { return flags_; }
    void set_flags(uint16_t f) { flags_ = f; }

    // Persistence
    bool save();

    // Argon2 params (stored for re-derivation on open)
    void get_argon2_params(uint32_t& t, uint32_t& m, uint32_t& p) const {
        t = argon2_t_cost_; m = argon2_m_cost_; p = argon2_p_cost_;
    }

private:
    std::string path_;
    uint16_t flags_ = 0;

    // Key material
    uint8_t dek_[32] = {};
    std::vector<uint8_t> argon2_salt_;
    uint32_t argon2_t_cost_ = 3;
    uint32_t argon2_m_cost_ = 65536;
    uint32_t argon2_p_cost_ = 4;
    std::vector<uint8_t> wrapped_dek_;
    std::vector<uint8_t> key_check_;

    // Collection registry
    std::unordered_map<std::string, CollectionMeta> collections_;
    mutable std::mutex mtx_;

    // Crypto callbacks for DEK wrapping
    CryptoCallbacks crypto_;

    // DEK wrapping: AES-GCM(user_key, AES-GCM(app_key, DEK))
    std::vector<uint8_t> wrap_dek(const uint8_t* app_key, size_t ak_len,
                                   const uint8_t* user_key, size_t uk_len);
    bool unwrap_dek(const uint8_t* app_key, size_t ak_len,
                    const uint8_t* user_key, size_t uk_len,
                    const std::vector<uint8_t>& wrapped);
    std::vector<uint8_t> make_key_check(const uint8_t* app_key, size_t ak_len);
    bool verify_key_check(const uint8_t* app_key, size_t ak_len,
                          const std::vector<uint8_t>& check);

    std::vector<uint8_t> serialize() const;
    bool deserialize(const std::vector<uint8_t>& data);
};

} // namespace hxs

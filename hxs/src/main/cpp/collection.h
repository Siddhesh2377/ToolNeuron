#pragma once

#include "wire_format.h"
#include "index.h"
#include "wal.h"

#include <cstdint>
#include <string>
#include <vector>
#include <unordered_map>
#include <functional>
#include <shared_mutex>
#include <memory>

namespace hxs {

/*
 * EncryptFn / DecryptFn — function pointers injected from the JNI layer.
 * This lets HXS use hxs_encryptor without a compile-time dependency.
 *
 * encrypt(plaintext, len, out_sealed, out_sealed_len, ctx) → bool
 * decrypt(sealed, len, out_plain, out_plain_len, ctx) → bool
 */
using EncryptFn = bool(*)(const uint8_t* in, size_t in_len,
                          std::vector<uint8_t>& out, void* ctx);
using DecryptFn = bool(*)(const uint8_t* in, size_t in_len,
                          std::vector<uint8_t>& out, void* ctx);

struct CryptoCallbacks {
    EncryptFn encrypt = nullptr;
    DecryptFn decrypt = nullptr;
    void* ctx = nullptr; // opaque pointer (DEK, engine, etc.)
};

/*
 * Collection — per-collection storage engine.
 *
 * On-disk format (collection.hxs):
 *   Sequence of blocks:
 *     [0-3]   block_len    uint32 LE (size of following data)
 *     [4..]   data         encrypted(compressed(Record.encode()))
 *
 * Records are held in memory (unordered_map<id, Record>).
 * Indexes are maintained live and serialized to collection.idx on flush.
 * WAL is per-collection at collection.wal.
 */
class Collection {
public:
    Collection(const std::string& name,
               const std::string& base_dir,
               const CryptoCallbacks& crypto);
    ~Collection();

    const std::string& name() const { return name_; }

    // Lifecycle
    bool load();
    bool flush();

    // CRUD
    uint32_t put(Record& record);   // auto-assigns ID if 0
    bool update(const Record& record);
    Record get(uint32_t record_id) const;
    bool remove(uint32_t record_id);
    uint32_t count() const;

    // Iteration
    void for_each(const std::function<void(const Record&)>& fn) const;

    // Get all records
    std::vector<Record> get_all() const;

    // Indexing (dynamic add/remove)
    void add_index(uint16_t tag, WireType wire_type);
    void remove_index(uint16_t tag);

    // Indexed queries
    std::vector<Record> query_string(uint16_t tag, std::string_view value) const;
    std::vector<Record> query_int(uint16_t tag, int64_t value) const;
    std::vector<Record> query_range_u64(uint16_t tag, uint64_t min_val, uint64_t max_val) const;

    // Fallback linear scan for unindexed fields
    std::vector<Record> scan(const std::function<bool(const Record&)>& predicate) const;

    // Drop this collection (delete all files)
    void drop();

    // Schema version (per-collection, for migrations)
    uint32_t schema_version() const { return schema_version_; }
    void set_schema_version(uint32_t v) { schema_version_ = v; }

private:
    std::string name_;
    std::string data_path_;    // collection.hxs
    std::string index_path_;   // collection.idx
    std::string wal_path_;     // collection.wal

    std::unordered_map<uint32_t, Record> records_;
    IndexSet indexes_;
    std::unique_ptr<WAL> wal_;
    CryptoCallbacks crypto_;

    uint32_t next_id_ = 1;
    uint32_t schema_version_ = 0;
    bool dirty_ = false;

    mutable std::shared_mutex mtx_; // readers-writer lock

    // Compression (LZ4)
    std::vector<uint8_t> compress(const std::vector<uint8_t>& input) const;
    std::vector<uint8_t> decompress(const uint8_t* data, size_t len) const;

    // Encrypt/decrypt wrappers
    std::vector<uint8_t> seal_record(const std::vector<uint8_t>& plain) const;
    std::vector<uint8_t> open_record(const uint8_t* data, size_t len) const;

    // WAL recovery
    void recover_from_wal();

    // Resolve record IDs from index results
    std::vector<Record> resolve_ids(const std::vector<uint32_t>& ids) const;
};

} // namespace hxs

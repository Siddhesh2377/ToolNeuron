#pragma once

#include "wire_format.h"
#include "index.h"
#include "crypto_engine.h"
#include "io_engine.h"

#include <functional>
#include <mutex>
#include <string>
#include <string_view>
#include <unordered_map>

namespace ums {

class Collection {
public:
    Collection(std::string name, std::string filename,
               fo::IOEngine& io, tn::CryptoEngine& crypto,
               const uint8_t* dek, bool plaintext = false);

    uint32_t put(const Record& record);  // returns record_id
    Record get(uint32_t record_id);
    bool remove(uint32_t record_id);
    uint32_t count() const;
    void for_each(const std::function<void(const Record&)>& fn);

    bool load();   // load all records from disk
    bool flush();  // write pending changes

    const std::string& name() const;
    const std::string& filename() const;

    // index management
    void add_index(uint16_t tag, uint8_t wire_type);

    // indexed queries (fall back to linear scan if no index)
    std::vector<Record> find_eq_string(uint16_t tag, std::string_view value);
    std::vector<Record> find_eq_int(uint16_t tag, int64_t value);
    std::vector<Record> find_range_u64(uint16_t tag, uint64_t min_val, uint64_t max_val);

private:
    std::string name_, filename_;
    fo::IOEngine& io_;
    tn::CryptoEngine& crypto_;
    const uint8_t* dek_;  // 32-byte DEK (nullptr when plaintext)
    bool plaintext_ = false;
    uint32_t next_id_ = 1;
    std::unordered_map<uint32_t, Record> records_;
    std::unordered_map<uint16_t, FieldIndex> indexes_;
    bool dirty_ = false;
    mutable std::mutex mtx_;

    std::vector<uint8_t> encrypt_record(const std::vector<uint8_t>& encoded);
    std::vector<uint8_t> decrypt_record(const uint8_t* data, size_t len);
    std::vector<uint8_t> compress(const std::vector<uint8_t>& data);
    std::vector<uint8_t> decompress(const uint8_t* data, size_t len);
    bool write_all();

    // collect records by ID list from index results
    std::vector<Record> collect_by_ids(const std::vector<uint32_t>& ids);
    // update all indexes for a record insert/remove
    void index_insert(const Record& rec);
    void index_remove(const Record& rec);
};

} // namespace ums

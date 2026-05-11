#pragma once

#include "wire_format.h"
#include <cstdint>
#include <map>
#include <string>
#include <vector>
#include <unordered_map>
#include <functional>

namespace hxs {

/*
 * FieldIndex — in-memory red-black tree index on a single field.
 *
 * Supports:
 *   - String equality (WIRE_BYTES)
 *   - Int64 equality (WIRE_VARINT)
 *   - Uint64 equality + range (WIRE_FIXED64)
 *   - Float/uint32 equality (WIRE_FIXED32)
 *
 * Serialized to disk on close, deserialized on open (no full rebuild).
 *
 * Serialization format:
 *   [0-1]   tag           uint16 LE
 *   [2]     wire_type     uint8
 *   [3-6]   entry_count   uint32 LE
 *   Per entry:
 *     [+0-3]  key_len     uint32 LE (for string index; 0 for numeric)
 *     [+4..]  key_data    (string bytes, or 8/4 bytes for numeric)
 *     [+N]    id_count    uint32 LE
 *     [+N+4.] record_ids  uint32 LE each
 */
class FieldIndex {
public:
    FieldIndex(uint16_t tag, WireType wire_type);

    uint16_t tag() const { return tag_; }
    WireType wire_type() const { return wire_type_; }

    // Insert/remove record from index
    void insert(const Record& rec);
    void remove(const Record& rec);
    void clear();

    // Rebuild from a full record set
    void rebuild(const std::unordered_map<uint32_t, Record>& records);

    // Queries
    std::vector<uint32_t> find_eq_str(std::string_view value) const;
    std::vector<uint32_t> find_eq_int(int64_t value) const;
    std::vector<uint32_t> find_eq_u64(uint64_t value) const;
    std::vector<uint32_t> find_range_u64(uint64_t min_val, uint64_t max_val) const;
    std::vector<uint32_t> find_eq_u32(uint32_t value) const;

    // Serialization
    std::vector<uint8_t> serialize() const;
    static FieldIndex deserialize(const uint8_t* data, size_t len, size_t& consumed);

private:
    uint16_t tag_;
    WireType wire_type_;

    std::map<std::string, std::vector<uint32_t>> str_index_;
    std::map<int64_t, std::vector<uint32_t>>     int_index_;
    std::map<uint64_t, std::vector<uint32_t>>    u64_index_;
    std::map<uint32_t, std::vector<uint32_t>>    u32_index_;

    void remove_id_from_vec(std::vector<uint32_t>& vec, uint32_t id);
};

/*
 * IndexSet — manages all indexes for a single collection.
 */
class IndexSet {
public:
    void add_index(uint16_t tag, WireType wire_type);
    void remove_index(uint16_t tag);
    bool has_index(uint16_t tag) const;

    void on_insert(const Record& rec);
    void on_remove(const Record& rec);
    void clear_all();
    void rebuild_all(const std::unordered_map<uint32_t, Record>& records);

    // Query (returns nullptr if no index for this tag)
    const FieldIndex* get_index(uint16_t tag) const;

    // Serialization of all indexes
    std::vector<uint8_t> serialize_all() const;
    void deserialize_all(const uint8_t* data, size_t len);

    // List all indexed tags
    std::vector<std::pair<uint16_t, WireType>> list_indexes() const;

private:
    std::unordered_map<uint16_t, FieldIndex> indexes_;
};

} // namespace hxs

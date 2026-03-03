#pragma once

#include "wire_format.h"

#include <cstdint>
#include <map>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace ums {

// in-memory field index using std::map (red-black tree, O(log n) lookups)
// one instance per indexed (collection, tag) pair
class FieldIndex {
public:
    FieldIndex(uint16_t tag, uint8_t wire_type);

    uint16_t tag() const { return tag_; }
    uint8_t wire_type() const { return wire_type_; }

    // extract tag value from record and add to index
    void insert(const Record& rec);
    // extract tag value from record and remove from index
    void remove(const Record& rec);
    void clear();
    // bulk rebuild from all records
    void rebuild(const std::unordered_map<uint32_t, Record>& records);

    // equality queries — return matching record IDs
    std::vector<uint32_t> find_eq_str(std::string_view value) const;
    std::vector<uint32_t> find_eq_int(int64_t value) const;
    std::vector<uint32_t> find_eq_u64(uint64_t value) const;

    // range query on fixed64 fields (timestamps), inclusive both ends
    std::vector<uint32_t> find_range_u64(uint64_t min_val, uint64_t max_val) const;

private:
    uint16_t tag_;
    uint8_t wire_type_;

    // only one of these is used, based on wire_type_
    std::map<std::string, std::vector<uint32_t>> str_index_;   // WIRE_BYTES
    std::map<int64_t, std::vector<uint32_t>> int_index_;       // WIRE_VARINT
    std::map<uint64_t, std::vector<uint32_t>> u64_index_;      // WIRE_FIXED64

    void remove_id(std::vector<uint32_t>& ids, uint32_t id);
};

} // namespace ums

#include "index.h"

#include <algorithm>

namespace ums {

FieldIndex::FieldIndex(uint16_t tag, uint8_t wire_type)
    : tag_(tag), wire_type_(wire_type) {}

void FieldIndex::insert(const Record& rec) {
    uint32_t id = rec.id();
    if (id == 0) return;

    switch (wire_type_) {
        case WIRE_BYTES: {
            auto val = rec.get_string(tag_);
            str_index_[val].push_back(id);
            break;
        }
        case WIRE_VARINT: {
            auto val = rec.get_varint(tag_);
            int_index_[val].push_back(id);
            break;
        }
        case WIRE_FIXED64: {
            auto val = rec.get_fixed64(tag_);
            u64_index_[val].push_back(id);
            break;
        }
        default:
            break;
    }
}

void FieldIndex::remove(const Record& rec) {
    uint32_t id = rec.id();
    if (id == 0) return;

    switch (wire_type_) {
        case WIRE_BYTES: {
            auto val = rec.get_string(tag_);
            auto it = str_index_.find(val);
            if (it != str_index_.end()) {
                remove_id(it->second, id);
                if (it->second.empty()) str_index_.erase(it);
            }
            break;
        }
        case WIRE_VARINT: {
            auto val = rec.get_varint(tag_);
            auto it = int_index_.find(val);
            if (it != int_index_.end()) {
                remove_id(it->second, id);
                if (it->second.empty()) int_index_.erase(it);
            }
            break;
        }
        case WIRE_FIXED64: {
            auto val = rec.get_fixed64(tag_);
            auto it = u64_index_.find(val);
            if (it != u64_index_.end()) {
                remove_id(it->second, id);
                if (it->second.empty()) u64_index_.erase(it);
            }
            break;
        }
        default:
            break;
    }
}

void FieldIndex::clear() {
    str_index_.clear();
    int_index_.clear();
    u64_index_.clear();
}

void FieldIndex::rebuild(const std::unordered_map<uint32_t, Record>& records) {
    clear();
    for (const auto& [id, rec] : records) {
        insert(rec);
    }
}

std::vector<uint32_t> FieldIndex::find_eq_str(std::string_view value) const {
    auto it = str_index_.find(std::string(value));
    if (it == str_index_.end()) return {};
    return it->second;
}

std::vector<uint32_t> FieldIndex::find_eq_int(int64_t value) const {
    auto it = int_index_.find(value);
    if (it == int_index_.end()) return {};
    return it->second;
}

std::vector<uint32_t> FieldIndex::find_eq_u64(uint64_t value) const {
    auto it = u64_index_.find(value);
    if (it == u64_index_.end()) return {};
    return it->second;
}

std::vector<uint32_t> FieldIndex::find_range_u64(uint64_t min_val, uint64_t max_val) const {
    std::vector<uint32_t> result;
    auto lo = u64_index_.lower_bound(min_val);
    auto hi = u64_index_.upper_bound(max_val);
    for (auto it = lo; it != hi; ++it) {
        result.insert(result.end(), it->second.begin(), it->second.end());
    }
    return result;
}

void FieldIndex::remove_id(std::vector<uint32_t>& ids, uint32_t id) {
    ids.erase(std::remove(ids.begin(), ids.end(), id), ids.end());
}

} // namespace ums

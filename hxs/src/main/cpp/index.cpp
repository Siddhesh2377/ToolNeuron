#include "index.h"
#include <cstring>
#include <algorithm>

namespace hxs {

// ── FieldIndex ──

FieldIndex::FieldIndex(uint16_t tag, WireType wire_type)
    : tag_(tag), wire_type_(wire_type) {}

void FieldIndex::remove_id_from_vec(std::vector<uint32_t>& vec, uint32_t id) {
    vec.erase(std::remove(vec.begin(), vec.end(), id), vec.end());
}

void FieldIndex::insert(const Record& rec) {
    uint32_t id = rec.id();
    switch (wire_type_) {
        case WIRE_BYTES: {
            auto str = rec.get_string(tag_);
            if (rec.has_field(tag_)) str_index_[str].push_back(id);
            break;
        }
        case WIRE_VARINT: {
            if (rec.has_field(tag_)) {
                int64_t val = rec.get_varint(tag_);
                int_index_[val].push_back(id);
            }
            break;
        }
        case WIRE_FIXED64: {
            if (rec.has_field(tag_)) {
                uint64_t val = rec.get_fixed64(tag_);
                u64_index_[val].push_back(id);
            }
            break;
        }
        case WIRE_FIXED32: {
            if (rec.has_field(tag_)) {
                uint32_t val = rec.get_fixed32(tag_);
                u32_index_[val].push_back(id);
            }
            break;
        }
    }
}

void FieldIndex::remove(const Record& rec) {
    uint32_t id = rec.id();
    switch (wire_type_) {
        case WIRE_BYTES: {
            auto str = rec.get_string(tag_);
            auto it = str_index_.find(str);
            if (it != str_index_.end()) {
                remove_id_from_vec(it->second, id);
                if (it->second.empty()) str_index_.erase(it);
            }
            break;
        }
        case WIRE_VARINT: {
            int64_t val = rec.get_varint(tag_);
            auto it = int_index_.find(val);
            if (it != int_index_.end()) {
                remove_id_from_vec(it->second, id);
                if (it->second.empty()) int_index_.erase(it);
            }
            break;
        }
        case WIRE_FIXED64: {
            uint64_t val = rec.get_fixed64(tag_);
            auto it = u64_index_.find(val);
            if (it != u64_index_.end()) {
                remove_id_from_vec(it->second, id);
                if (it->second.empty()) u64_index_.erase(it);
            }
            break;
        }
        case WIRE_FIXED32: {
            uint32_t val = rec.get_fixed32(tag_);
            auto it = u32_index_.find(val);
            if (it != u32_index_.end()) {
                remove_id_from_vec(it->second, id);
                if (it->second.empty()) u32_index_.erase(it);
            }
            break;
        }
    }
}

void FieldIndex::clear() {
    str_index_.clear();
    int_index_.clear();
    u64_index_.clear();
    u32_index_.clear();
}

void FieldIndex::rebuild(const std::unordered_map<uint32_t, Record>& records) {
    clear();
    for (auto& [_, rec] : records) {
        if (!rec.is_deleted()) insert(rec);
    }
}

std::vector<uint32_t> FieldIndex::find_eq_str(std::string_view value) const {
    auto it = str_index_.find(std::string(value));
    return (it != str_index_.end()) ? it->second : std::vector<uint32_t>{};
}

std::vector<uint32_t> FieldIndex::find_eq_int(int64_t value) const {
    auto it = int_index_.find(value);
    return (it != int_index_.end()) ? it->second : std::vector<uint32_t>{};
}

std::vector<uint32_t> FieldIndex::find_eq_u64(uint64_t value) const {
    auto it = u64_index_.find(value);
    return (it != u64_index_.end()) ? it->second : std::vector<uint32_t>{};
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

std::vector<uint32_t> FieldIndex::find_eq_u32(uint32_t value) const {
    auto it = u32_index_.find(value);
    return (it != u32_index_.end()) ? it->second : std::vector<uint32_t>{};
}

// ── FieldIndex serialization ──

std::vector<uint8_t> FieldIndex::serialize() const {
    std::vector<uint8_t> out;

    // Header: tag(2) + wire_type(1) + entry_count(4)
    out.resize(7);
    memcpy(out.data(), &tag_, 2);
    out[2] = static_cast<uint8_t>(wire_type_);

    auto write_u32 = [&](uint32_t v) {
        uint8_t buf[4];
        memcpy(buf, &v, 4);
        out.insert(out.end(), buf, buf + 4);
    };

    auto write_ids = [&](const std::vector<uint32_t>& ids) {
        write_u32(static_cast<uint32_t>(ids.size()));
        for (uint32_t id : ids) write_u32(id);
    };

    uint32_t entry_count = 0;

    switch (wire_type_) {
        case WIRE_BYTES: {
            entry_count = static_cast<uint32_t>(str_index_.size());
            memcpy(out.data() + 3, &entry_count, 4);
            for (auto& [key, ids] : str_index_) {
                write_u32(static_cast<uint32_t>(key.size()));
                out.insert(out.end(), key.begin(), key.end());
                write_ids(ids);
            }
            break;
        }
        case WIRE_VARINT: {
            entry_count = static_cast<uint32_t>(int_index_.size());
            memcpy(out.data() + 3, &entry_count, 4);
            for (auto& [key, ids] : int_index_) {
                uint8_t buf[8];
                int64_t k = key;
                memcpy(buf, &k, 8);
                out.insert(out.end(), buf, buf + 8);
                write_ids(ids);
            }
            break;
        }
        case WIRE_FIXED64: {
            entry_count = static_cast<uint32_t>(u64_index_.size());
            memcpy(out.data() + 3, &entry_count, 4);
            for (auto& [key, ids] : u64_index_) {
                uint8_t buf[8];
                memcpy(buf, &key, 8);
                out.insert(out.end(), buf, buf + 8);
                write_ids(ids);
            }
            break;
        }
        case WIRE_FIXED32: {
            entry_count = static_cast<uint32_t>(u32_index_.size());
            memcpy(out.data() + 3, &entry_count, 4);
            for (auto& [key, ids] : u32_index_) {
                uint8_t buf[4];
                memcpy(buf, &key, 4);
                out.insert(out.end(), buf, buf + 4);
                write_ids(ids);
            }
            break;
        }
    }

    return out;
}

FieldIndex FieldIndex::deserialize(const uint8_t* data, size_t len, size_t& consumed) {
    consumed = 0;
    if (len < 7) return FieldIndex(0, WIRE_VARINT);

    uint16_t tag;
    memcpy(&tag, data, 2);
    auto wt = static_cast<WireType>(data[2]);
    uint32_t entry_count;
    memcpy(&entry_count, data + 3, 4);

    FieldIndex idx(tag, wt);
    const uint8_t* p = data + 7;
    const uint8_t* end = data + len;

    auto read_u32 = [&]() -> uint32_t {
        if (p + 4 > end) return 0;
        uint32_t v;
        memcpy(&v, p, 4);
        p += 4;
        return v;
    };

    auto read_ids = [&]() -> std::vector<uint32_t> {
        uint32_t count = read_u32();
        std::vector<uint32_t> ids(count);
        for (uint32_t i = 0; i < count && p + 4 <= end; i++) {
            ids[i] = read_u32();
        }
        return ids;
    };

    for (uint32_t i = 0; i < entry_count; i++) {
        switch (wt) {
            case WIRE_BYTES: {
                uint32_t klen = read_u32();
                if (p + klen > end) goto done;
                std::string key(reinterpret_cast<const char*>(p), klen);
                p += klen;
                idx.str_index_[std::move(key)] = read_ids();
                break;
            }
            case WIRE_VARINT: {
                if (p + 8 > end) goto done;
                int64_t key;
                memcpy(&key, p, 8);
                p += 8;
                idx.int_index_[key] = read_ids();
                break;
            }
            case WIRE_FIXED64: {
                if (p + 8 > end) goto done;
                uint64_t key;
                memcpy(&key, p, 8);
                p += 8;
                idx.u64_index_[key] = read_ids();
                break;
            }
            case WIRE_FIXED32: {
                if (p + 4 > end) goto done;
                uint32_t key;
                memcpy(&key, p, 4);
                p += 4;
                idx.u32_index_[key] = read_ids();
                break;
            }
        }
    }

done:
    consumed = static_cast<size_t>(p - data);
    return idx;
}

// ── IndexSet ──

void IndexSet::add_index(uint16_t tag, WireType wire_type) {
    indexes_.emplace(tag, FieldIndex(tag, wire_type));
}

void IndexSet::remove_index(uint16_t tag) {
    indexes_.erase(tag);
}

bool IndexSet::has_index(uint16_t tag) const {
    return indexes_.count(tag) > 0;
}

void IndexSet::on_insert(const Record& rec) {
    for (auto& [_, idx] : indexes_) idx.insert(rec);
}

void IndexSet::on_remove(const Record& rec) {
    for (auto& [_, idx] : indexes_) idx.remove(rec);
}

void IndexSet::clear_all() {
    for (auto& [_, idx] : indexes_) idx.clear();
}

void IndexSet::rebuild_all(const std::unordered_map<uint32_t, Record>& records) {
    for (auto& [_, idx] : indexes_) idx.rebuild(records);
}

const FieldIndex* IndexSet::get_index(uint16_t tag) const {
    auto it = indexes_.find(tag);
    return (it != indexes_.end()) ? &it->second : nullptr;
}

std::vector<std::pair<uint16_t, WireType>> IndexSet::list_indexes() const {
    std::vector<std::pair<uint16_t, WireType>> result;
    for (auto& [tag, idx] : indexes_) {
        result.emplace_back(tag, idx.wire_type());
    }
    return result;
}

std::vector<uint8_t> IndexSet::serialize_all() const {
    std::vector<uint8_t> out;
    // Header: index_count(4)
    uint32_t count = static_cast<uint32_t>(indexes_.size());
    out.resize(4);
    memcpy(out.data(), &count, 4);

    for (auto& [_, idx] : indexes_) {
        auto chunk = idx.serialize();
        // Write chunk size + chunk
        uint32_t csize = static_cast<uint32_t>(chunk.size());
        uint8_t buf[4];
        memcpy(buf, &csize, 4);
        out.insert(out.end(), buf, buf + 4);
        out.insert(out.end(), chunk.begin(), chunk.end());
    }
    return out;
}

void IndexSet::deserialize_all(const uint8_t* data, size_t len) {
    indexes_.clear();
    if (len < 4) return;

    const uint8_t* p = data;
    const uint8_t* end = data + len;

    uint32_t count;
    memcpy(&count, p, 4);
    p += 4;

    for (uint32_t i = 0; i < count && p + 4 <= end; i++) {
        uint32_t csize;
        memcpy(&csize, p, 4);
        p += 4;

        if (p + csize > end) break;
        size_t consumed = 0;
        auto idx = FieldIndex::deserialize(p, csize, consumed);
        indexes_.emplace(idx.tag(), std::move(idx));
        p += csize;
    }
}

} // namespace hxs

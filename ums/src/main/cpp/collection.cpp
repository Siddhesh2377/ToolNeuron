#include "collection.h"

#include <android/log.h>
#include <cstring>
#include <lz4.h>

#define LOG_TAG "ums_collection"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace ums {

// ---------------------------------------------------------------------------
// Little-endian helpers (local to this TU)
// ---------------------------------------------------------------------------

static void write_le32(uint8_t* out, uint32_t v) {
    out[0] = static_cast<uint8_t>(v);
    out[1] = static_cast<uint8_t>(v >> 8);
    out[2] = static_cast<uint8_t>(v >> 16);
    out[3] = static_cast<uint8_t>(v >> 24);
}

static uint32_t read_le32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) |
           (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) |
           (static_cast<uint32_t>(p[3]) << 24);
}

// LZ4 compression header magic
static constexpr uint32_t LZ4_MAGIC = 0x4C5A3400; // "LZ4\0"
// only compress records larger than this threshold
static constexpr size_t COMPRESS_THRESHOLD = 128;

// ---------------------------------------------------------------------------
// Construction
// ---------------------------------------------------------------------------

Collection::Collection(std::string name, std::string filename,
                       fo::IOEngine& io, tn::CryptoEngine& crypto,
                       const uint8_t* dek, bool plaintext)
    : name_(std::move(name)),
      filename_(std::move(filename)),
      io_(io),
      crypto_(crypto),
      dek_(dek),
      plaintext_(plaintext) {}

const std::string& Collection::name() const { return name_; }
const std::string& Collection::filename() const { return filename_; }

// ---------------------------------------------------------------------------
// Encrypt / Decrypt a single record
// ---------------------------------------------------------------------------

std::vector<uint8_t> Collection::encrypt_record(const std::vector<uint8_t>& encoded) {
    if (plaintext_) {
        return encoded; // skip AES-GCM, return data as-is
    }
    auto result = crypto_.encrypt_aes_gcm(
        encoded.data(), encoded.size(),
        dek_, tn::AES_KEY_SIZE,
        nullptr, 0
    );
    if (!result.success) {
        LOGE("encrypt_record: AES-GCM encrypt failed");
        return {};
    }
    return std::move(result.sealed_data);
}

std::vector<uint8_t> Collection::decrypt_record(const uint8_t* data, size_t len) {
    if (plaintext_) {
        return std::vector<uint8_t>(data, data + len); // skip AES-GCM, return copy
    }
    auto result = crypto_.decrypt_aes_gcm(
        data, len,
        dek_, tn::AES_KEY_SIZE,
        nullptr, 0
    );
    if (!result.success) {
        LOGE("decrypt_record: AES-GCM decrypt failed");
        return {};
    }
    // copy from SecureBuffer into a vector
    std::vector<uint8_t> out(result.plaintext.data(),
                             result.plaintext.data() + result.plaintext.size());
    return out;
}

// ---------------------------------------------------------------------------
// LZ4 compression / decompression
// ---------------------------------------------------------------------------

std::vector<uint8_t> Collection::compress(const std::vector<uint8_t>& data) {
    // skip compression for small records
    if (data.size() < COMPRESS_THRESHOLD) return data;

    int src_size = static_cast<int>(data.size());
    int max_dst = LZ4_compressBound(src_size);
    // header: 4 bytes magic + 4 bytes uncompressed size
    std::vector<uint8_t> out(8 + max_dst);
    write_le32(out.data(), LZ4_MAGIC);
    write_le32(out.data() + 4, static_cast<uint32_t>(data.size()));

    int compressed_size = LZ4_compress_default(
        reinterpret_cast<const char*>(data.data()),
        reinterpret_cast<char*>(out.data() + 8),
        src_size, max_dst
    );

    if (compressed_size <= 0) {
        // compression failed, return original
        return data;
    }

    // if compressed is not smaller, skip
    if (static_cast<size_t>(compressed_size + 8) >= data.size()) {
        return data;
    }

    out.resize(8 + compressed_size);
    return out;
}

std::vector<uint8_t> Collection::decompress(const uint8_t* data, size_t len) {
    // check for LZ4 magic header
    if (len >= 8 && read_le32(data) == LZ4_MAGIC) {
        uint32_t orig_size = read_le32(data + 4);
        if (orig_size > 64 * 1024 * 1024) { // 64MB sanity cap
            LOGE("decompress: original size too large (%u)", orig_size);
            return {};
        }
        std::vector<uint8_t> out(orig_size);
        int result = LZ4_decompress_safe(
            reinterpret_cast<const char*>(data + 8),
            reinterpret_cast<char*>(out.data()),
            static_cast<int>(len - 8),
            static_cast<int>(orig_size)
        );
        if (result < 0) {
            LOGE("decompress: LZ4_decompress_safe failed");
            return {};
        }
        return out;
    }
    // not compressed, return as-is
    return std::vector<uint8_t>(data, data + len);
}

// ---------------------------------------------------------------------------
// Index helpers
// ---------------------------------------------------------------------------

void Collection::add_index(uint16_t tag, uint8_t wire_type) {
    std::lock_guard<std::mutex> lock(mtx_);
    if (indexes_.count(tag)) return; // already exists
    auto [it, _] = indexes_.emplace(tag, FieldIndex(tag, wire_type));
    it->second.rebuild(records_);
}

void Collection::index_insert(const Record& rec) {
    for (auto& [tag, idx] : indexes_) {
        idx.insert(rec);
    }
}

void Collection::index_remove(const Record& rec) {
    for (auto& [tag, idx] : indexes_) {
        idx.remove(rec);
    }
}

std::vector<Record> Collection::collect_by_ids(const std::vector<uint32_t>& ids) {
    std::vector<Record> result;
    result.reserve(ids.size());
    for (uint32_t id : ids) {
        auto it = records_.find(id);
        if (it != records_.end()) result.push_back(it->second);
    }
    return result;
}

// ---------------------------------------------------------------------------
// Query methods (indexed with linear-scan fallback)
// ---------------------------------------------------------------------------

std::vector<Record> Collection::find_eq_string(uint16_t tag, std::string_view value) {
    std::lock_guard<std::mutex> lock(mtx_);
    auto idx_it = indexes_.find(tag);
    if (idx_it != indexes_.end()) {
        return collect_by_ids(idx_it->second.find_eq_str(value));
    }
    // fallback: linear scan
    std::vector<Record> result;
    for (const auto& [id, rec] : records_) {
        if (rec.get_string(tag) == value) result.push_back(rec);
    }
    return result;
}

std::vector<Record> Collection::find_eq_int(uint16_t tag, int64_t value) {
    std::lock_guard<std::mutex> lock(mtx_);
    auto idx_it = indexes_.find(tag);
    if (idx_it != indexes_.end()) {
        return collect_by_ids(idx_it->second.find_eq_int(value));
    }
    // fallback: linear scan
    std::vector<Record> result;
    for (const auto& [id, rec] : records_) {
        if (rec.get_varint(tag) == value) result.push_back(rec);
    }
    return result;
}

std::vector<Record> Collection::find_range_u64(uint16_t tag, uint64_t min_val, uint64_t max_val) {
    std::lock_guard<std::mutex> lock(mtx_);
    auto idx_it = indexes_.find(tag);
    if (idx_it != indexes_.end()) {
        return collect_by_ids(idx_it->second.find_range_u64(min_val, max_val));
    }
    // fallback: linear scan
    std::vector<Record> result;
    for (const auto& [id, rec] : records_) {
        uint64_t v = rec.get_fixed64(tag);
        if (v >= min_val && v <= max_val) result.push_back(rec);
    }
    return result;
}

// ---------------------------------------------------------------------------
// CRUD operations
// ---------------------------------------------------------------------------

uint32_t Collection::put(const Record& record) {
    std::lock_guard<std::mutex> lock(mtx_);

    Record r = record;  // copy so we can modify
    if (r.id() == 0) {
        r.set_id(next_id_++);
    } else {
        if (r.id() >= next_id_) {
            next_id_ = r.id() + 1;
        }
        // remove old record from indexes before overwrite
        auto old_it = records_.find(r.id());
        if (old_it != records_.end()) {
            index_remove(old_it->second);
        }
    }

    uint32_t rid = r.id();
    index_insert(r);
    records_[rid] = std::move(r);
    dirty_ = true;
    return rid;
}

Record Collection::get(uint32_t record_id) {
    std::lock_guard<std::mutex> lock(mtx_);
    auto it = records_.find(record_id);
    if (it == records_.end()) return Record();
    return it->second;
}

bool Collection::remove(uint32_t record_id) {
    std::lock_guard<std::mutex> lock(mtx_);
    auto it = records_.find(record_id);
    if (it == records_.end()) return false;
    index_remove(it->second);
    records_.erase(it);
    dirty_ = true;
    return true;
}

uint32_t Collection::count() const {
    std::lock_guard<std::mutex> lock(mtx_);
    return static_cast<uint32_t>(records_.size());
}

void Collection::for_each(const std::function<void(const Record&)>& fn) {
    std::lock_guard<std::mutex> lock(mtx_);
    for (const auto& [id, rec] : records_) {
        fn(rec);
    }
}

// ---------------------------------------------------------------------------
// Load from disk
//
// File format: sequence of [4-byte sealed_len LE][sealed_data] blocks
// After decryption, data is either raw record (UMSR magic) or LZ4 compressed
// ---------------------------------------------------------------------------

bool Collection::load() {
    std::lock_guard<std::mutex> lock(mtx_);

    if (!io_.exists(filename_)) {
        return true;
    }

    auto result = io_.read(filename_);
    if (!result.success) {
        LOGE("load: failed to read file %s", filename_.c_str());
        return false;
    }

    const uint8_t* p = result.data.data();
    size_t remaining = result.data.size();

    records_.clear();
    next_id_ = 1;

    while (remaining >= 4) {
        uint32_t sealed_len = read_le32(p);
        p += 4;
        remaining -= 4;

        if (sealed_len > remaining) {
            LOGE("load: truncated sealed block (need %u, have %zu)", sealed_len, remaining);
            break;
        }

        auto decrypted = decrypt_record(p, sealed_len);
        p += sealed_len;
        remaining -= sealed_len;

        if (decrypted.empty()) {
            LOGE("load: failed to decrypt a record block");
            continue;
        }

        // decompress if LZ4, otherwise pass through
        auto decoded_bytes = decompress(decrypted.data(), decrypted.size());
        if (decoded_bytes.empty()) {
            LOGE("load: decompression failed");
            continue;
        }

        Record rec = Record::decode(decoded_bytes.data(), decoded_bytes.size());
        if (rec.id() == 0) {
            LOGE("load: decoded record has id=0, skipping");
            continue;
        }

        if (rec.id() >= next_id_) {
            next_id_ = rec.id() + 1;
        }
        records_[rec.id()] = std::move(rec);
    }

    // rebuild all indexes from loaded records
    for (auto& [tag, idx] : indexes_) {
        idx.rebuild(records_);
    }

    dirty_ = false;
    return true;
}

// ---------------------------------------------------------------------------
// Flush to disk
// ---------------------------------------------------------------------------

bool Collection::flush() {
    std::lock_guard<std::mutex> lock(mtx_);
    if (!dirty_) return true;
    return write_all();
}

bool Collection::write_all() {
    std::vector<uint8_t> file_data;

    for (const auto& [id, rec] : records_) {
        auto encoded = rec.encode();
        auto compressed = compress(encoded);
        auto sealed = encrypt_record(compressed);
        if (sealed.empty()) {
            LOGE("write_all: failed to encrypt record %u", id);
            return false;
        }

        size_t offset = file_data.size();
        file_data.resize(offset + 4 + sealed.size());
        write_le32(file_data.data() + offset, static_cast<uint32_t>(sealed.size()));
        std::memcpy(file_data.data() + offset + 4, sealed.data(), sealed.size());
    }

    auto wr = io_.write(filename_, file_data.data(), file_data.size());
    if (!wr.success) {
        LOGE("write_all: io_.write failed for %s", filename_.c_str());
        return false;
    }

    dirty_ = false;
    return true;
}

} // namespace ums

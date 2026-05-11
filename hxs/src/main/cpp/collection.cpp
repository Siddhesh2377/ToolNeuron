#include "collection.h"

#include <lz4.h>

#include <cstring>
#include <cstdio>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#include <android/log.h>
#define TAG "HXS"

namespace hxs {

constexpr uint32_t LZ4_MAGIC = 0x4C5A3400; // "LZ4\0"
constexpr size_t COMPRESS_THRESHOLD = 128;

Collection::Collection(const std::string& name,
                       const std::string& base_dir,
                       const CryptoCallbacks& crypto)
    : name_(name), crypto_(crypto)
{
    data_path_  = base_dir + "/" + name + ".hxs";
    index_path_ = base_dir + "/" + name + ".idx";
    wal_path_   = base_dir + "/" + name + ".wal";
    wal_ = std::make_unique<WAL>(wal_path_);
}

Collection::~Collection() {
    if (dirty_) flush();
}

// ── LZ4 compression ──

std::vector<uint8_t> Collection::compress(const std::vector<uint8_t>& input) const {
    if (input.size() < COMPRESS_THRESHOLD) return input;

    int max_dst = LZ4_compressBound(static_cast<int>(input.size()));
    std::vector<uint8_t> out(8 + max_dst); // 8-byte header

    uint32_t magic = LZ4_MAGIC;
    uint32_t orig_size = static_cast<uint32_t>(input.size());
    memcpy(out.data(), &magic, 4);
    memcpy(out.data() + 4, &orig_size, 4);

    int compressed_size = LZ4_compress_default(
        reinterpret_cast<const char*>(input.data()),
        reinterpret_cast<char*>(out.data() + 8),
        static_cast<int>(input.size()),
        max_dst
    );

    if (compressed_size <= 0 || static_cast<size_t>(compressed_size + 8) >= input.size()) {
        return input; // compression didn't help
    }

    out.resize(8 + compressed_size);
    return out;
}

std::vector<uint8_t> Collection::decompress(const uint8_t* data, size_t len) const {
    if (len < 8) return std::vector<uint8_t>(data, data + len);

    uint32_t magic;
    memcpy(&magic, data, 4);
    if (magic != LZ4_MAGIC) {
        return std::vector<uint8_t>(data, data + len); // not compressed
    }

    uint32_t orig_size;
    memcpy(&orig_size, data + 4, 4);
    if (orig_size > 64 * 1024 * 1024) return {}; // sanity: 64 MB max

    std::vector<uint8_t> out(orig_size);
    int decompressed = LZ4_decompress_safe(
        reinterpret_cast<const char*>(data + 8),
        reinterpret_cast<char*>(out.data()),
        static_cast<int>(len - 8),
        static_cast<int>(orig_size)
    );

    if (decompressed < 0) return {};
    out.resize(static_cast<size_t>(decompressed));
    return out;
}

// ── Encrypt/decrypt wrappers ──

std::vector<uint8_t> Collection::seal_record(const std::vector<uint8_t>& plain) const {
    if (!crypto_.encrypt) return plain; // plaintext mode

    std::vector<uint8_t> sealed;
    if (crypto_.encrypt(plain.data(), plain.size(), sealed, crypto_.ctx)) {
        return sealed;
    }
    return {}; // encryption failed
}

std::vector<uint8_t> Collection::open_record(const uint8_t* data, size_t len) const {
    if (!crypto_.decrypt) return std::vector<uint8_t>(data, data + len);

    std::vector<uint8_t> plain;
    if (crypto_.decrypt(data, len, plain, crypto_.ctx)) {
        return plain;
    }
    return {};
}

// ── Load from disk ──

bool Collection::load() {
    std::unique_lock lock(mtx_);

    records_.clear();
    next_id_ = 1;

    // Load WAL first
    wal_->open();

    // Load data file
    int fd = ::open(data_path_.c_str(), O_RDONLY);
    if (fd >= 0) {
        struct stat st;
        if (fstat(fd, &st) == 0 && st.st_size > 0) {
            std::vector<uint8_t> buf(static_cast<size_t>(st.st_size));
            ssize_t n = ::read(fd, buf.data(), buf.size());
            if (n > 0) {
                const uint8_t* p = buf.data();
                const uint8_t* end = buf.data() + n;

                while (p + 4 <= end) {
                    uint32_t block_len;
                    memcpy(&block_len, p, 4);
                    p += 4;

                    if (p + block_len > end) break;

                    // Decrypt → decompress → decode
                    auto decrypted = open_record(p, block_len);
                    if (decrypted.empty()) { p += block_len; continue; }

                    auto decompressed = decompress(decrypted.data(), decrypted.size());
                    if (decompressed.empty()) { p += block_len; continue; }

                    Record rec = Record::decode(decompressed.data(), decompressed.size());
                    if (rec.id() > 0 && !rec.is_deleted()) {
                        if (rec.id() >= next_id_) next_id_ = rec.id() + 1;
                        records_[rec.id()] = std::move(rec);
                    }
                    p += block_len;
                }
            }
        }
        ::close(fd);
    }

    // Load serialized indexes
    int idx_fd = ::open(index_path_.c_str(), O_RDONLY);
    if (idx_fd >= 0) {
        struct stat st;
        if (fstat(idx_fd, &st) == 0 && st.st_size > 0) {
            std::vector<uint8_t> buf(static_cast<size_t>(st.st_size));
            ssize_t n = ::read(idx_fd, buf.data(), buf.size());
            if (n > 0) {
                indexes_.deserialize_all(buf.data(), static_cast<size_t>(n));
            }
        }
        ::close(idx_fd);
    }

    // Recover uncommitted WAL entries
    recover_from_wal();

    dirty_ = false;
    return true;
}

// ── Flush to disk ──

bool Collection::flush() {
    std::shared_lock lock(mtx_);

    int fd = ::open(data_path_.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) return false;

    for (auto& [id, rec] : records_) {
        if (rec.is_deleted()) continue;

        auto encoded = rec.encode();
        auto compressed = compress(encoded);
        auto sealed = seal_record(compressed);
        if (sealed.empty() && crypto_.encrypt) continue; // encryption failed, skip

        uint32_t block_len = static_cast<uint32_t>(sealed.size());
        ::write(fd, &block_len, 4);
        ::write(fd, sealed.data(), sealed.size());
    }

    ::fsync(fd);
    ::close(fd);

    // Save indexes
    auto idx_data = indexes_.serialize_all();
    int idx_fd = ::open(index_path_.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (idx_fd >= 0) {
        ::write(idx_fd, idx_data.data(), idx_data.size());
        ::fsync(idx_fd);
        ::close(idx_fd);
    }

    // Checkpoint WAL
    wal_->checkpoint();

    dirty_ = false;
    return true;
}

// ── CRUD ──

uint32_t Collection::put(Record& record) {
    std::unique_lock lock(mtx_);

    if (record.id() == 0) {
        record.set_id(next_id_++);
    } else if (record.id() >= next_id_) {
        next_id_ = record.id() + 1;
    }

    // WAL first
    auto encoded = record.encode();
    uint32_t seq = wal_->append(WalOp::PUT, record.id(), encoded);

    // Apply to memory
    indexes_.on_insert(record);
    records_[record.id()] = record;

    // Mark WAL committed
    wal_->mark_committed(seq);
    dirty_ = true;
    return record.id();
}

bool Collection::update(const Record& record) {
    std::unique_lock lock(mtx_);

    auto it = records_.find(record.id());
    if (it == records_.end()) return false;

    // WAL
    auto encoded = record.encode();
    uint32_t seq = wal_->append(WalOp::PUT, record.id(), encoded);

    // Update indexes: remove old, insert new
    indexes_.on_remove(it->second);
    indexes_.on_insert(record);
    it->second = record;

    wal_->mark_committed(seq);
    dirty_ = true;
    return true;
}

Record Collection::get(uint32_t record_id) const {
    std::shared_lock lock(mtx_);
    auto it = records_.find(record_id);
    return (it != records_.end()) ? it->second : Record();
}

bool Collection::remove(uint32_t record_id) {
    std::unique_lock lock(mtx_);

    auto it = records_.find(record_id);
    if (it == records_.end()) return false;

    uint32_t seq = wal_->append(WalOp::DELETE, record_id);

    indexes_.on_remove(it->second);
    records_.erase(it);

    wal_->mark_committed(seq);
    dirty_ = true;
    return true;
}

uint32_t Collection::count() const {
    std::shared_lock lock(mtx_);
    return static_cast<uint32_t>(records_.size());
}

// ── Iteration ──

void Collection::for_each(const std::function<void(const Record&)>& fn) const {
    std::shared_lock lock(mtx_);
    for (auto& [_, rec] : records_) {
        if (!rec.is_deleted()) fn(rec);
    }
}

std::vector<Record> Collection::get_all() const {
    std::shared_lock lock(mtx_);
    std::vector<Record> result;
    result.reserve(records_.size());
    for (auto& [_, rec] : records_) {
        if (!rec.is_deleted()) result.push_back(rec);
    }
    return result;
}

// ── Indexing ──

void Collection::add_index(uint16_t tag, WireType wire_type) {
    std::unique_lock lock(mtx_);
    indexes_.add_index(tag, wire_type);
    // Rebuild this specific index from existing records
    auto* idx = const_cast<FieldIndex*>(indexes_.get_index(tag));
    if (idx) idx->rebuild(records_);
    dirty_ = true;
}

void Collection::remove_index(uint16_t tag) {
    std::unique_lock lock(mtx_);
    indexes_.remove_index(tag);
    dirty_ = true;
}

// ── Queries ──

std::vector<Record> Collection::resolve_ids(const std::vector<uint32_t>& ids) const {
    std::vector<Record> result;
    result.reserve(ids.size());
    for (uint32_t id : ids) {
        auto it = records_.find(id);
        if (it != records_.end() && !it->second.is_deleted()) {
            result.push_back(it->second);
        }
    }
    return result;
}

std::vector<Record> Collection::query_string(uint16_t tag, std::string_view value) const {
    std::shared_lock lock(mtx_);
    const FieldIndex* idx = indexes_.get_index(tag);
    if (idx) return resolve_ids(idx->find_eq_str(value));

    // Fallback: linear scan
    std::vector<Record> result;
    for (auto& [_, rec] : records_) {
        if (!rec.is_deleted() && rec.get_string(tag) == value) {
            result.push_back(rec);
        }
    }
    return result;
}

std::vector<Record> Collection::query_int(uint16_t tag, int64_t value) const {
    std::shared_lock lock(mtx_);
    const FieldIndex* idx = indexes_.get_index(tag);
    if (idx) return resolve_ids(idx->find_eq_int(value));

    std::vector<Record> result;
    for (auto& [_, rec] : records_) {
        if (!rec.is_deleted() && rec.get_varint(tag) == value) {
            result.push_back(rec);
        }
    }
    return result;
}

std::vector<Record> Collection::query_range_u64(uint16_t tag, uint64_t min_val, uint64_t max_val) const {
    std::shared_lock lock(mtx_);
    const FieldIndex* idx = indexes_.get_index(tag);
    if (idx) return resolve_ids(idx->find_range_u64(min_val, max_val));

    std::vector<Record> result;
    for (auto& [_, rec] : records_) {
        if (!rec.is_deleted()) {
            uint64_t v = rec.get_fixed64(tag);
            if (v >= min_val && v <= max_val) result.push_back(rec);
        }
    }
    return result;
}

std::vector<Record> Collection::scan(const std::function<bool(const Record&)>& predicate) const {
    std::shared_lock lock(mtx_);
    std::vector<Record> result;
    for (auto& [_, rec] : records_) {
        if (!rec.is_deleted() && predicate(rec)) {
            result.push_back(rec);
        }
    }
    return result;
}

// ── Drop ──

void Collection::drop() {
    std::unique_lock lock(mtx_);
    records_.clear();
    indexes_.clear_all();
    wal_->destroy();
    ::unlink(data_path_.c_str());
    ::unlink(index_path_.c_str());
    dirty_ = false;
}

// ── WAL recovery ──

void Collection::recover_from_wal() {
    wal_->replay([this](const WalEntry& e) {
        switch (e.op) {
            case WalOp::PUT: {
                if (e.data.empty()) break;
                Record rec = Record::decode(e.data.data(), e.data.size());
                if (rec.id() > 0) {
                    if (rec.id() >= next_id_) next_id_ = rec.id() + 1;
                    records_[rec.id()] = std::move(rec);
                }
                break;
            }
            case WalOp::DELETE: {
                records_.erase(e.record_id);
                break;
            }
        }
    });

    // If we recovered anything, rebuild indexes and mark dirty
    if (wal_->has_uncommitted()) {
        indexes_.rebuild_all(records_);
        dirty_ = true;
    }
}

} // namespace hxs

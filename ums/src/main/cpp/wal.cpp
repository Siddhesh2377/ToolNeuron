#include "wal.h"

#include <android/log.h>
#include <cstring>

#define LOG_TAG "ums_wal"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace ums {

// ---------------------------------------------------------------------------
// Little-endian helpers
// ---------------------------------------------------------------------------

static void write_le16(uint8_t* out, uint16_t v) {
    out[0] = static_cast<uint8_t>(v);
    out[1] = static_cast<uint8_t>(v >> 8);
}

static void write_le32(uint8_t* out, uint32_t v) {
    out[0] = static_cast<uint8_t>(v);
    out[1] = static_cast<uint8_t>(v >> 8);
    out[2] = static_cast<uint8_t>(v >> 16);
    out[3] = static_cast<uint8_t>(v >> 24);
}

static uint16_t read_le16(const uint8_t* p) {
    return static_cast<uint16_t>(p[0]) |
           (static_cast<uint16_t>(p[1]) << 8);
}

static uint32_t read_le32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) |
           (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) |
           (static_cast<uint32_t>(p[3]) << 24);
}

// ---------------------------------------------------------------------------
// Construction
// ---------------------------------------------------------------------------

WAL::WAL(fo::IOEngine& io, std::string wal_path)
    : io_(io), path_(std::move(wal_path)) {}

// ---------------------------------------------------------------------------
// Entry serialization
//
// Wire format:
//   magic(4) + sequence(4) + op(1) + committed(1) +
//   collection_name_len(2) + collection_name +
//   record_id(4) + data_len(4) + data
// ---------------------------------------------------------------------------

std::vector<uint8_t> WAL::serialize_entry(const WalEntry& entry) {
    size_t name_len = entry.collection.size();
    size_t total = 4 + 4 + 1 + 1 + 2 + name_len + 4 + 4 + entry.data.size();

    std::vector<uint8_t> buf(total);
    uint8_t* p = buf.data();

    write_le32(p, WAL_ENTRY_MAGIC); p += 4;
    write_le32(p, entry.sequence);  p += 4;
    *p++ = static_cast<uint8_t>(entry.op);
    *p++ = entry.committed ? 1 : 0;
    write_le16(p, static_cast<uint16_t>(name_len)); p += 2;
    std::memcpy(p, entry.collection.data(), name_len); p += name_len;
    write_le32(p, entry.record_id); p += 4;
    write_le32(p, static_cast<uint32_t>(entry.data.size())); p += 4;
    if (!entry.data.empty()) {
        std::memcpy(p, entry.data.data(), entry.data.size());
    }

    return buf;
}

WalEntry WAL::deserialize_entry(const uint8_t* data, size_t len, size_t& consumed) {
    WalEntry entry{};
    consumed = 0;

    // Minimum size: magic(4) + seq(4) + op(1) + committed(1) + name_len(2) + record_id(4) + data_len(4) = 20
    if (len < 20) return entry;

    const uint8_t* p = data;

    uint32_t magic = read_le32(p); p += 4;
    if (magic != WAL_ENTRY_MAGIC) return entry;

    entry.sequence = read_le32(p); p += 4;
    entry.op = static_cast<WalOp>(*p++);
    entry.committed = (*p++ != 0);

    uint16_t name_len = read_le16(p); p += 2;

    // Check we have enough remaining bytes
    size_t header_so_far = static_cast<size_t>(p - data);
    if (header_so_far + name_len + 8 > len) return entry;

    entry.collection.assign(reinterpret_cast<const char*>(p), name_len);
    p += name_len;

    entry.record_id = read_le32(p); p += 4;
    uint32_t data_len = read_le32(p); p += 4;

    size_t used = static_cast<size_t>(p - data);
    if (used + data_len > len) return entry;

    if (data_len > 0) {
        entry.data.assign(p, p + data_len);
        p += data_len;
    }

    consumed = static_cast<size_t>(p - data);
    return entry;
}

// ---------------------------------------------------------------------------
// open() — read existing WAL file and parse entries
// ---------------------------------------------------------------------------

bool WAL::open() {
    entries_.clear();
    next_seq_ = 1;

    if (!io_.exists(path_)) {
        return true; // no WAL file — start fresh
    }

    auto result = io_.read(path_);
    if (!result.success) {
        LOGE("open: failed to read WAL file %s", path_.c_str());
        return false;
    }

    const uint8_t* p = result.data.data();
    size_t remaining = result.data.size();

    while (remaining > 0) {
        size_t consumed = 0;
        WalEntry entry = deserialize_entry(p, remaining, consumed);
        if (consumed == 0) {
            LOGE("open: failed to deserialize WAL entry at offset %zu",
                 result.data.size() - remaining);
            break;
        }

        if (entry.sequence >= next_seq_) {
            next_seq_ = entry.sequence + 1;
        }
        entries_.push_back(std::move(entry));
        p += consumed;
        remaining -= consumed;
    }

    return true;
}

// ---------------------------------------------------------------------------
// append() — add a new entry to the WAL
// ---------------------------------------------------------------------------

uint32_t WAL::append(WalOp op, const std::string& collection,
                     uint32_t record_id, const std::vector<uint8_t>& data) {
    WalEntry entry;
    entry.sequence = next_seq_++;
    entry.op = op;
    entry.collection = collection;
    entry.record_id = record_id;
    entry.data = data;
    entry.committed = false;

    auto serialized = serialize_entry(entry);
    auto wr = io_.append(path_, serialized.data(), serialized.size());
    if (!wr.success) {
        LOGE("append: failed to write WAL entry seq=%u", entry.sequence);
        return 0;
    }

    entries_.push_back(std::move(entry));
    return entries_.back().sequence;
}

// ---------------------------------------------------------------------------
// mark_committed() — flag an entry as committed
// ---------------------------------------------------------------------------

bool WAL::mark_committed(uint32_t sequence) {
    for (auto& e : entries_) {
        if (e.sequence == sequence) {
            e.committed = true;
            // Rewrite entire WAL to persist the committed flag
            // (Simple approach — for a production system, you'd
            //  seek to the specific byte and flip it.)
            return checkpoint() || true; // best-effort
        }
    }
    return false;
}

// ---------------------------------------------------------------------------
// checkpoint() — remove committed entries, rewrite WAL
// ---------------------------------------------------------------------------

bool WAL::checkpoint() {
    // Partition: keep only uncommitted entries
    std::vector<WalEntry> remaining;
    for (auto& e : entries_) {
        if (!e.committed) {
            remaining.push_back(std::move(e));
        }
    }
    entries_ = std::move(remaining);

    if (entries_.empty()) {
        // Remove WAL file entirely
        io_.remove(path_);
        return true;
    }

    // Rewrite the WAL with remaining entries
    std::vector<uint8_t> buf;
    for (const auto& e : entries_) {
        auto serialized = serialize_entry(e);
        buf.insert(buf.end(), serialized.begin(), serialized.end());
    }

    auto wr = io_.write(path_, buf.data(), buf.size());
    if (!wr.success) {
        LOGE("checkpoint: failed to rewrite WAL");
        return false;
    }

    return true;
}

// ---------------------------------------------------------------------------
// replay() — iterate over all entries (for crash recovery)
// ---------------------------------------------------------------------------

void WAL::replay(const std::function<void(const WalEntry&)>& fn) {
    for (const auto& e : entries_) {
        fn(e);
    }
}

} // namespace ums

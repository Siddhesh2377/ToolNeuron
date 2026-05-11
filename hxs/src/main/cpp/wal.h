#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <functional>
#include <mutex>

namespace hxs {

constexpr uint32_t WAL_MAGIC = 0x48585357; // "HXSW"

enum class WalOp : uint8_t {
    PUT    = 1,
    DELETE = 2,
};

/*
 * WAL entry format (per entry, little-endian):
 *   [0-3]   magic       0x48585357
 *   [4-7]   sequence    uint32
 *   [8]     op          uint8 (1=PUT, 2=DELETE)
 *   [9]     committed   uint8 (0 or 1)
 *   [10-13] record_id   uint32
 *   [14-17] data_len    uint32
 *   [18..]  data        encoded record (PUT only)
 */
struct WalEntry {
    uint32_t sequence;
    WalOp op;
    uint32_t record_id;
    std::vector<uint8_t> data;
    bool committed;
};

class WAL {
public:
    explicit WAL(const std::string& wal_path);
    ~WAL();

    bool open();

    // Append a new entry, returns sequence number
    uint32_t append(WalOp op, uint32_t record_id,
                    const std::vector<uint8_t>& data = {});

    // Mark entry as committed
    bool mark_committed(uint32_t sequence);

    // Remove all committed entries from WAL file
    bool checkpoint();

    // Replay all uncommitted entries for crash recovery
    void replay(const std::function<void(const WalEntry&)>& handler);

    // Check if WAL has uncommitted entries
    bool has_uncommitted() const;

    // Number of entries
    size_t entry_count() const;

    // Clear all entries (for when collection is dropped)
    void destroy();

private:
    std::string path_;
    std::vector<WalEntry> entries_;
    uint32_t next_seq_ = 1;
    mutable std::mutex mtx_;

    bool flush_to_disk();
    bool load_from_disk();
    std::vector<uint8_t> serialize_entry(const WalEntry& e) const;
    bool deserialize_entry(const uint8_t* data, size_t len,
                           WalEntry& out, size_t& consumed) const;
};

} // namespace hxs

#pragma once

#include "io_engine.h"

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace ums {

enum class WalOp : uint8_t { PUT = 1, DELETE = 2 };

struct WalEntry {
    uint32_t sequence;
    WalOp op;
    std::string collection;
    uint32_t record_id;
    std::vector<uint8_t> data;  // encoded record for PUT, empty for DELETE
    bool committed;
};

constexpr uint32_t WAL_ENTRY_MAGIC = 0x554D5357; // "UMSW"

class WAL {
public:
    WAL(fo::IOEngine& io, std::string wal_path);
    bool open();
    uint32_t append(WalOp op, const std::string& collection,
                    uint32_t record_id, const std::vector<uint8_t>& data = {});
    bool mark_committed(uint32_t sequence);
    bool checkpoint();  // clear committed entries
    void replay(const std::function<void(const WalEntry&)>& fn);

private:
    fo::IOEngine& io_;
    std::string path_;
    uint32_t next_seq_ = 1;
    std::vector<WalEntry> entries_;

    std::vector<uint8_t> serialize_entry(const WalEntry& entry);
    WalEntry deserialize_entry(const uint8_t* data, size_t len, size_t& consumed);
};

} // namespace ums

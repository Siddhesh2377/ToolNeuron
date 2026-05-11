#include "wal.h"

#include <cstring>
#include <cstdio>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <algorithm>

namespace hxs {

WAL::WAL(const std::string& wal_path) : path_(wal_path) {}
WAL::~WAL() = default;

bool WAL::open() {
    std::lock_guard<std::mutex> lock(mtx_);
    return load_from_disk();
}

uint32_t WAL::append(WalOp op, uint32_t record_id,
                     const std::vector<uint8_t>& data) {
    std::lock_guard<std::mutex> lock(mtx_);

    WalEntry e;
    e.sequence = next_seq_++;
    e.op = op;
    e.record_id = record_id;
    e.data = data;
    e.committed = false;

    entries_.push_back(std::move(e));
    flush_to_disk();
    return e.sequence;
}

bool WAL::mark_committed(uint32_t sequence) {
    std::lock_guard<std::mutex> lock(mtx_);
    for (auto& e : entries_) {
        if (e.sequence == sequence) {
            e.committed = true;
            return flush_to_disk();
        }
    }
    return false;
}

bool WAL::checkpoint() {
    std::lock_guard<std::mutex> lock(mtx_);
    entries_.erase(
        std::remove_if(entries_.begin(), entries_.end(),
            [](const WalEntry& e) { return e.committed; }),
        entries_.end()
    );
    return flush_to_disk();
}

void WAL::replay(const std::function<void(const WalEntry&)>& handler) {
    std::lock_guard<std::mutex> lock(mtx_);
    for (auto& e : entries_) {
        if (!e.committed) handler(e);
    }
}

bool WAL::has_uncommitted() const {
    std::lock_guard<std::mutex> lock(mtx_);
    for (auto& e : entries_) {
        if (!e.committed) return true;
    }
    return false;
}

size_t WAL::entry_count() const {
    std::lock_guard<std::mutex> lock(mtx_);
    return entries_.size();
}

void WAL::destroy() {
    std::lock_guard<std::mutex> lock(mtx_);
    entries_.clear();
    next_seq_ = 1;
    ::unlink(path_.c_str());
}

// ── Serialization ──

std::vector<uint8_t> WAL::serialize_entry(const WalEntry& e) const {
    size_t size = 18 + e.data.size();
    std::vector<uint8_t> buf(size);
    uint8_t* p = buf.data();

    uint32_t magic = WAL_MAGIC;
    memcpy(p, &magic, 4); p += 4;
    memcpy(p, &e.sequence, 4); p += 4;
    *p++ = static_cast<uint8_t>(e.op);
    *p++ = e.committed ? 1 : 0;
    memcpy(p, &e.record_id, 4); p += 4;
    uint32_t dlen = static_cast<uint32_t>(e.data.size());
    memcpy(p, &dlen, 4); p += 4;
    if (!e.data.empty()) {
        memcpy(p, e.data.data(), e.data.size());
    }
    return buf;
}

bool WAL::deserialize_entry(const uint8_t* data, size_t len,
                            WalEntry& out, size_t& consumed) const {
    consumed = 0;
    if (len < 18) return false;

    const uint8_t* p = data;
    uint32_t magic;
    memcpy(&magic, p, 4);
    if (magic != WAL_MAGIC) return false;
    p += 4;

    memcpy(&out.sequence, p, 4); p += 4;
    out.op = static_cast<WalOp>(*p++);
    out.committed = (*p++ != 0);
    memcpy(&out.record_id, p, 4); p += 4;

    uint32_t dlen;
    memcpy(&dlen, p, 4); p += 4;

    if (p + dlen > data + len) return false;
    out.data.assign(p, p + dlen);
    p += dlen;

    consumed = static_cast<size_t>(p - data);
    return true;
}

bool WAL::flush_to_disk() {
    int fd = ::open(path_.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) return false;

    for (auto& e : entries_) {
        auto buf = serialize_entry(e);
        ::write(fd, buf.data(), buf.size());
    }

    ::fsync(fd);
    ::close(fd);
    return true;
}

bool WAL::load_from_disk() {
    entries_.clear();
    next_seq_ = 1;

    int fd = ::open(path_.c_str(), O_RDONLY);
    if (fd < 0) return true; // no WAL file = clean state

    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size == 0) {
        ::close(fd);
        return true;
    }

    std::vector<uint8_t> buf(static_cast<size_t>(st.st_size));
    ssize_t n = ::read(fd, buf.data(), buf.size());
    ::close(fd);
    if (n <= 0) return true;

    const uint8_t* p = buf.data();
    const uint8_t* end = buf.data() + n;

    while (p < end) {
        WalEntry e;
        size_t consumed = 0;
        if (!deserialize_entry(p, static_cast<size_t>(end - p), e, consumed)) break;
        if (e.sequence >= next_seq_) next_seq_ = e.sequence + 1;
        entries_.push_back(std::move(e));
        p += consumed;
    }

    return true;
}

} // namespace hxs

#include "persistence.h"
#include <cstdio>
#include <algorithm>

namespace hxd {

static constexpr uint32_t MAGIC   = 0x48584451u;  // "HXDQ"
static constexpr uint32_t VERSION = 1u;

// ── write helpers ─────────────────────────────────────────────────────────────

static void w_u8 (FILE* f, uint8_t  v) { fwrite(&v, 1, 1, f); }
static void w_u16(FILE* f, uint16_t v) { fwrite(&v, 2, 1, f); }
static void w_u32(FILE* f, uint32_t v) { fwrite(&v, 4, 1, f); }
static void w_i64(FILE* f, int64_t  v) { fwrite(&v, 8, 1, f); }
static void w_str(FILE* f, const std::string& s) {
    auto len = (uint16_t)std::min(s.size(), (size_t)65535u);
    w_u16(f, len);
    fwrite(s.data(), 1, len, f);
}

// ── read helpers ──────────────────────────────────────────────────────────────

static bool r_u8 (FILE* f, uint8_t&  v) { return fread(&v, 1, 1, f) == 1; }
static bool r_u16(FILE* f, uint16_t& v) { return fread(&v, 2, 1, f) == 2; }
static bool r_u32(FILE* f, uint32_t& v) { return fread(&v, 4, 1, f) == 4; }
static bool r_i64(FILE* f, int64_t&  v) { return fread(&v, 8, 1, f) == 8; }
static bool r_str(FILE* f, std::string& s) {
    uint16_t len;
    if (!r_u16(f, len)) return false;
    s.resize(len);
    return fread(s.data(), 1, len, f) == (size_t)len;
}

// ── public API ────────────────────────────────────────────────────────────────

bool save_queue(const char* path, const std::vector<PersistedTask>& tasks) {
    FILE* f = fopen(path, "wb");
    if (!f) return false;

    w_u32(f, MAGIC);
    w_u32(f, VERSION);
    w_u32(f, (uint32_t)tasks.size());

    for (const auto& t : tasks) {
        w_u32(f, (uint32_t)t.id);
        w_u8 (f, (uint8_t)t.state);
        w_i64(f, t.downloaded_bytes);
        w_i64(f, t.total_bytes);
        w_str(f, t.url);
        w_str(f, t.dest_path);
    }

    fflush(f);
    fclose(f);
    return true;
}

std::vector<PersistedTask> load_queue(const char* path) {
    std::vector<PersistedTask> tasks;
    FILE* f = fopen(path, "rb");
    if (!f) return tasks;

    uint32_t magic, version, count;
    if (!r_u32(f, magic)   || magic   != MAGIC)   { fclose(f); return tasks; }
    if (!r_u32(f, version) || version != VERSION)  { fclose(f); return tasks; }
    if (!r_u32(f, count))                          { fclose(f); return tasks; }
    if (count > 10000)                             { fclose(f); return tasks; }

    tasks.reserve(count);
    for (uint32_t i = 0; i < count; ++i) {
        PersistedTask t;
        uint32_t id; uint8_t state;
        if (!r_u32(f, id))                   break;
        if (!r_u8 (f, state))                break;
        if (!r_i64(f, t.downloaded_bytes))   break;
        if (!r_i64(f, t.total_bytes))        break;
        if (!r_str(f, t.url))                break;
        if (!r_str(f, t.dest_path))          break;
        t.id    = (int)id;
        t.state = (TaskState)state;
        tasks.push_back(std::move(t));
    }

    fclose(f);
    return tasks;
}

} // namespace hxd

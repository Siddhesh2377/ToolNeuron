#include "wire_format.h"
#include <cstring>
#include <algorithm>
#include <stdexcept>

namespace hxs {

// ── Varint (LEB128 + zig-zag) ──

static uint64_t zigzag_encode(int64_t v) {
    return static_cast<uint64_t>((v << 1) ^ (v >> 63));
}

static int64_t zigzag_decode(uint64_t v) {
    return static_cast<int64_t>((v >> 1) ^ -(v & 1));
}

size_t encode_varint(int64_t value, uint8_t* out) {
    uint64_t v = zigzag_encode(value);
    size_t i = 0;
    while (v > 0x7F) {
        out[i++] = static_cast<uint8_t>((v & 0x7F) | 0x80);
        v >>= 7;
    }
    out[i++] = static_cast<uint8_t>(v);
    return i;
}

int64_t decode_varint(const uint8_t* data, size_t len, size_t& bytes_read) {
    uint64_t result = 0;
    size_t shift = 0;
    bytes_read = 0;
    for (size_t i = 0; i < len && i < 10; i++) {
        uint64_t byte = data[i];
        result |= (byte & 0x7F) << shift;
        bytes_read++;
        if ((byte & 0x80) == 0) {
            return zigzag_decode(result);
        }
        shift += 7;
    }
    return 0; // malformed
}

// ── Field helpers ──

const Field* Record::find_field(uint16_t tag) const {
    for (auto& f : fields_) {
        if (f.tag == tag) return &f;
    }
    return nullptr;
}

Field* Record::find_field_mut(uint16_t tag) {
    for (auto& f : fields_) {
        if (f.tag == tag) return &f;
    }
    return nullptr;
}

void Record::set_field(uint16_t tag, WireType type, const uint8_t* data, size_t len) {
    Field* f = find_field_mut(tag);
    if (f) {
        f->type = type;
        f->data.assign(data, data + len);
    } else {
        fields_.push_back({tag, type, std::vector<uint8_t>(data, data + len)});
    }
}

bool Record::has_field(uint16_t tag) const {
    return find_field(tag) != nullptr;
}

void Record::remove_field(uint16_t tag) {
    fields_.erase(
        std::remove_if(fields_.begin(), fields_.end(),
            [tag](const Field& f) { return f.tag == tag; }),
        fields_.end()
    );
}

void Record::clear() {
    fields_.clear();
    unknown_fields_.clear();
    id_ = 0;
    flags_ = 0;
}

std::vector<uint16_t> Record::tags() const {
    std::vector<uint16_t> result;
    result.reserve(fields_.size());
    for (auto& f : fields_) result.push_back(f.tag);
    return result;
}

// ── Setters ──

void Record::put_varint(uint16_t tag, int64_t value) {
    uint8_t buf[10];
    size_t n = encode_varint(value, buf);
    set_field(tag, WIRE_VARINT, buf, n);
}

void Record::put_fixed64(uint16_t tag, uint64_t value) {
    uint8_t buf[8];
    memcpy(buf, &value, 8); // LE on ARM/x86
    set_field(tag, WIRE_FIXED64, buf, 8);
}

void Record::put_bytes(uint16_t tag, const uint8_t* data, size_t len) {
    set_field(tag, WIRE_BYTES, data, len);
}

void Record::put_string(uint16_t tag, std::string_view value) {
    set_field(tag, WIRE_BYTES,
              reinterpret_cast<const uint8_t*>(value.data()), value.size());
}

void Record::put_fixed32(uint16_t tag, uint32_t value) {
    uint8_t buf[4];
    memcpy(buf, &value, 4);
    set_field(tag, WIRE_FIXED32, buf, 4);
}

void Record::put_bool(uint16_t tag, bool value) {
    put_varint(tag, value ? 1 : 0);
}

void Record::put_float(uint16_t tag, float value) {
    uint32_t bits;
    memcpy(&bits, &value, 4);
    put_fixed32(tag, bits);
}

void Record::put_double(uint16_t tag, double value) {
    uint64_t bits;
    memcpy(&bits, &value, 8);
    put_fixed64(tag, bits);
}

// ── Getters ──

int64_t Record::get_varint(uint16_t tag, int64_t def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_VARINT || f->data.empty()) return def;
    size_t read;
    return decode_varint(f->data.data(), f->data.size(), read);
}

uint64_t Record::get_fixed64(uint16_t tag, uint64_t def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_FIXED64 || f->data.size() < 8) return def;
    uint64_t val;
    memcpy(&val, f->data.data(), 8);
    return val;
}

const std::vector<uint8_t>* Record::get_bytes(uint16_t tag) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_BYTES) return nullptr;
    return &f->data;
}

std::string Record::get_string(uint16_t tag, std::string_view def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_BYTES) return std::string(def);
    return std::string(reinterpret_cast<const char*>(f->data.data()), f->data.size());
}

uint32_t Record::get_fixed32(uint16_t tag, uint32_t def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_FIXED32 || f->data.size() < 4) return def;
    uint32_t val;
    memcpy(&val, f->data.data(), 4);
    return val;
}

bool Record::get_bool(uint16_t tag, bool def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_VARINT) return def;
    size_t read;
    return decode_varint(f->data.data(), f->data.size(), read) != 0;
}

float Record::get_float(uint16_t tag, float def) const {
    uint32_t bits = get_fixed32(tag, 0);
    if (!has_field(tag)) return def;
    float val;
    memcpy(&val, &bits, 4);
    return val;
}

double Record::get_double(uint16_t tag, double def) const {
    uint64_t bits = get_fixed64(tag, 0);
    if (!has_field(tag)) return def;
    double val;
    memcpy(&val, &bits, 8);
    return val;
}

// ── Iteration ──

void Record::for_each_field(
    const std::function<void(uint16_t, WireType, const uint8_t*, size_t)>& fn
) const {
    for (auto& f : fields_) fn(f.tag, f.type, f.data.data(), f.data.size());
    for (auto& f : unknown_fields_) fn(f.tag, f.type, f.data.data(), f.data.size());
}

// ── Encode ──

std::vector<uint8_t> Record::encode() const {
    // Calculate total fields (known + unknown)
    size_t total_fields = fields_.size() + unknown_fields_.size();

    // Pre-calculate data size
    size_t data_size = 0;
    auto calc_field_size = [&](const Field& f) {
        data_size += 2 + 1; // tag(2) + wire_type(1)
        switch (f.type) {
            case WIRE_VARINT:
                data_size += 4 + f.data.size(); // data_len(4) + varint bytes
                break;
            case WIRE_FIXED64:
                data_size += 8;
                break;
            case WIRE_FIXED32:
                data_size += 4;
                break;
            case WIRE_BYTES:
                data_size += 4 + f.data.size(); // data_len(4) + bytes
                break;
        }
    };

    for (auto& f : fields_) calc_field_size(f);
    for (auto& f : unknown_fields_) calc_field_size(f);

    size_t header_size = 16;
    size_t total_size = header_size + data_size;

    std::vector<uint8_t> out(total_size);
    uint8_t* p = out.data();

    // Header
    uint32_t magic = RECORD_MAGIC;
    uint32_t tsize = static_cast<uint32_t>(total_size);
    uint16_t fcount = static_cast<uint16_t>(total_fields);
    uint8_t version = 1;

    memcpy(p + 0, &magic, 4);
    memcpy(p + 4, &tsize, 4);
    memcpy(p + 8, &id_, 4);
    memcpy(p + 12, &fcount, 2);
    p[14] = flags_;
    p[15] = version;
    p += 16;

    // Fields
    auto write_field = [&](const Field& f) {
        memcpy(p, &f.tag, 2); p += 2;
        *p++ = static_cast<uint8_t>(f.type);

        switch (f.type) {
            case WIRE_VARINT: {
                uint32_t dlen = static_cast<uint32_t>(f.data.size());
                memcpy(p, &dlen, 4); p += 4;
                memcpy(p, f.data.data(), f.data.size());
                p += f.data.size();
                break;
            }
            case WIRE_FIXED64:
                memcpy(p, f.data.data(), 8); p += 8;
                break;
            case WIRE_FIXED32:
                memcpy(p, f.data.data(), 4); p += 4;
                break;
            case WIRE_BYTES: {
                uint32_t dlen = static_cast<uint32_t>(f.data.size());
                memcpy(p, &dlen, 4); p += 4;
                if (!f.data.empty()) {
                    memcpy(p, f.data.data(), f.data.size());
                    p += f.data.size();
                }
                break;
            }
        }
    };

    for (auto& f : fields_) write_field(f);
    for (auto& f : unknown_fields_) write_field(f);

    return out;
}

// ── Decode ──

Record Record::decode(const uint8_t* data, size_t len) {
    Record rec;
    if (len < 16) return rec;

    const uint8_t* p = data;

    uint32_t magic;
    memcpy(&magic, p, 4);
    if (magic != RECORD_MAGIC) return rec;

    uint32_t total_size;
    memcpy(&total_size, p + 4, 4);

    memcpy(&rec.id_, p + 8, 4);

    uint16_t field_count;
    memcpy(&field_count, p + 12, 2);

    rec.flags_ = p[14];
    // p[15] = version, currently ignored (forward compat)

    p += 16;
    const uint8_t* end = data + std::min(static_cast<size_t>(total_size), len);

    for (uint16_t i = 0; i < field_count && (p + 3) <= end; i++) {
        Field f;
        memcpy(&f.tag, p, 2); p += 2;
        f.type = static_cast<WireType>(*p++);

        size_t data_len = 0;
        switch (f.type) {
            case WIRE_VARINT: {
                if (p + 4 > end) return rec;
                uint32_t dlen;
                memcpy(&dlen, p, 4); p += 4;
                data_len = dlen;
                break;
            }
            case WIRE_FIXED64:
                data_len = 8;
                break;
            case WIRE_FIXED32:
                data_len = 4;
                break;
            case WIRE_BYTES: {
                if (p + 4 > end) return rec;
                uint32_t dlen;
                memcpy(&dlen, p, 4); p += 4;
                data_len = dlen;
                break;
            }
            default:
                return rec; // unknown wire type, bail
        }

        if (p + data_len > end) return rec;
        f.data.assign(p, p + data_len);
        p += data_len;

        // All fields go into known fields; the Kotlin layer decides
        // which tags it understands. Unknown ones roundtrip safely.
        rec.fields_.push_back(std::move(f));
    }

    return rec;
}

} // namespace hxs

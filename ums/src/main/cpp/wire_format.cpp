#include "wire_format.h"

#include <cstring>

namespace ums {

// ---------------------------------------------------------------------------
// LEB128 varint encoding (signed, zig-zag like protobuf)
// ---------------------------------------------------------------------------

static uint64_t zigzag_encode(int64_t v) {
    return (static_cast<uint64_t>(v) << 1) ^ static_cast<uint64_t>(v >> 63);
}

static int64_t zigzag_decode(uint64_t v) {
    return static_cast<int64_t>((v >> 1) ^ -(v & 1));
}

size_t encode_varint(int64_t value, uint8_t* out) {
    uint64_t v = zigzag_encode(value);
    size_t i = 0;
    while (v >= 0x80) {
        out[i++] = static_cast<uint8_t>(v | 0x80);
        v >>= 7;
    }
    out[i++] = static_cast<uint8_t>(v);
    return i;
}

int64_t decode_varint(const uint8_t* data, size_t len, size_t& bytes_read) {
    uint64_t result = 0;
    size_t shift = 0;
    bytes_read = 0;

    for (size_t i = 0; i < len && i < 10; ++i) {
        uint64_t b = data[i];
        result |= (b & 0x7F) << shift;
        shift += 7;
        bytes_read = i + 1;
        if ((b & 0x80) == 0) {
            return zigzag_decode(result);
        }
    }

    // Malformed varint — return 0
    return 0;
}

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

static void write_le64(uint8_t* out, uint64_t v) {
    for (int i = 0; i < 8; ++i) {
        out[i] = static_cast<uint8_t>(v >> (i * 8));
    }
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

static uint64_t read_le64(const uint8_t* p) {
    uint64_t v = 0;
    for (int i = 0; i < 8; ++i) {
        v |= static_cast<uint64_t>(p[i]) << (i * 8);
    }
    return v;
}

// ---------------------------------------------------------------------------
// Record implementation
// ---------------------------------------------------------------------------

Record::Record(uint32_t id) : record_id_(id) {}

uint32_t Record::id() const { return record_id_; }
void Record::set_id(uint32_t id) { record_id_ = id; }
uint8_t Record::flags() const { return flags_; }
void Record::set_flags(uint8_t f) { flags_ = f; }

const Field* Record::find_field(uint16_t tag) const {
    for (const auto& f : fields_) {
        if (f.tag == tag) return &f;
    }
    return nullptr;
}

bool Record::has_field(uint16_t tag) const {
    return find_field(tag) != nullptr;
}

// ---------------------------------------------------------------------------
// Setters — replace existing field with same tag or append
// ---------------------------------------------------------------------------

static void set_field(std::vector<Field>& fields, uint16_t tag, WireType type,
                      std::vector<uint8_t> data) {
    for (auto& f : fields) {
        if (f.tag == tag) {
            f.type = type;
            f.data = std::move(data);
            return;
        }
    }
    fields.push_back({tag, type, std::move(data)});
}

void Record::put_varint(uint16_t tag, int64_t value) {
    uint8_t buf[10];
    size_t n = encode_varint(value, buf);
    set_field(fields_, tag, WIRE_VARINT, {buf, buf + n});
}

void Record::put_fixed64(uint16_t tag, uint64_t value) {
    uint8_t buf[8];
    write_le64(buf, value);
    set_field(fields_, tag, WIRE_FIXED64, {buf, buf + 8});
}

void Record::put_bytes(uint16_t tag, const uint8_t* data, size_t len) {
    set_field(fields_, tag, WIRE_BYTES, {data, data + len});
}

void Record::put_string(uint16_t tag, std::string_view value) {
    auto p = reinterpret_cast<const uint8_t*>(value.data());
    set_field(fields_, tag, WIRE_BYTES, {p, p + value.size()});
}

void Record::put_fixed32(uint16_t tag, uint32_t value) {
    uint8_t buf[4];
    write_le32(buf, value);
    set_field(fields_, tag, WIRE_FIXED32, {buf, buf + 4});
}

void Record::put_bool(uint16_t tag, bool value) {
    put_varint(tag, value ? 1 : 0);
}

// ---------------------------------------------------------------------------
// Getters
// ---------------------------------------------------------------------------

int64_t Record::get_varint(uint16_t tag, int64_t def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_VARINT || f->data.empty()) return def;
    size_t read = 0;
    return decode_varint(f->data.data(), f->data.size(), read);
}

uint64_t Record::get_fixed64(uint16_t tag, uint64_t def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_FIXED64 || f->data.size() < 8) return def;
    return read_le64(f->data.data());
}

const std::vector<uint8_t>* Record::get_bytes(uint16_t tag) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_BYTES) return nullptr;
    return &f->data;
}

std::string Record::get_string(uint16_t tag, std::string_view def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_BYTES) return std::string(def);
    return {reinterpret_cast<const char*>(f->data.data()), f->data.size()};
}

uint32_t Record::get_fixed32(uint16_t tag, uint32_t def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_FIXED32 || f->data.size() < 4) return def;
    return read_le32(f->data.data());
}

bool Record::get_bool(uint16_t tag, bool def) const {
    const Field* f = find_field(tag);
    if (!f || f->type != WIRE_VARINT || f->data.empty()) return def;
    size_t read = 0;
    return decode_varint(f->data.data(), f->data.size(), read) != 0;
}

// ---------------------------------------------------------------------------
// encode() — serialize Record to bytes
// ---------------------------------------------------------------------------
//
// Header (16 bytes):
//   magic(4) + record_size(4) + record_id(4) + field_count(2) + flags(1) + reserved(1)
//
// Per field:
//   tag(2 LE) + wire_type(1) + [length(4 LE) for BYTES only] + data
// ---------------------------------------------------------------------------

std::vector<uint8_t> Record::encode() const {
    // Calculate total field data size
    size_t fields_size = 0;
    auto calc_field_size = [&](const Field& f) {
        fields_size += 2 + 1; // tag + wire_type
        switch (f.type) {
            case WIRE_VARINT:
                fields_size += f.data.size();
                break;
            case WIRE_FIXED64:
                fields_size += 8;
                break;
            case WIRE_BYTES:
                fields_size += 4 + f.data.size(); // length prefix + data
                break;
            case WIRE_FIXED32:
                fields_size += 4;
                break;
        }
    };

    for (const auto& f : fields_) calc_field_size(f);
    for (const auto& f : unknown_fields_) calc_field_size(f);

    uint16_t total_field_count = static_cast<uint16_t>(fields_.size() + unknown_fields_.size());
    size_t total_size = RECORD_HEADER_SIZE + fields_size;

    std::vector<uint8_t> out(total_size);
    uint8_t* p = out.data();

    // Header
    write_le32(p, RECORD_MAGIC);       p += 4;
    write_le32(p, static_cast<uint32_t>(total_size)); p += 4;
    write_le32(p, record_id_);         p += 4;
    write_le16(p, total_field_count);  p += 2;
    *p++ = flags_;
    *p++ = 0; // reserved

    // Write fields
    auto write_field = [&](const Field& f) {
        write_le16(p, f.tag); p += 2;
        *p++ = static_cast<uint8_t>(f.type);
        switch (f.type) {
            case WIRE_VARINT:
                std::memcpy(p, f.data.data(), f.data.size());
                p += f.data.size();
                break;
            case WIRE_FIXED64:
                std::memcpy(p, f.data.data(), 8);
                p += 8;
                break;
            case WIRE_BYTES:
                write_le32(p, static_cast<uint32_t>(f.data.size()));
                p += 4;
                std::memcpy(p, f.data.data(), f.data.size());
                p += f.data.size();
                break;
            case WIRE_FIXED32:
                std::memcpy(p, f.data.data(), 4);
                p += 4;
                break;
        }
    };

    for (const auto& f : fields_) write_field(f);
    for (const auto& f : unknown_fields_) write_field(f);

    return out;
}

// ---------------------------------------------------------------------------
// decode() — deserialize Record from bytes
// ---------------------------------------------------------------------------

Record Record::decode(const uint8_t* data, size_t len) {
    if (len < RECORD_HEADER_SIZE) {
        return Record();
    }

    const uint8_t* p = data;

    uint32_t magic = read_le32(p); p += 4;
    if (magic != RECORD_MAGIC) {
        return Record();
    }

    uint32_t record_size = read_le32(p); p += 4;
    if (record_size > len) {
        return Record();
    }

    uint32_t record_id = read_le32(p); p += 4;
    uint16_t field_count = read_le16(p); p += 2;
    uint8_t flags = *p++;
    p++; // reserved

    Record rec(record_id);
    rec.set_flags(flags);

    const uint8_t* end = data + record_size;

    for (uint16_t i = 0; i < field_count && p + 3 <= end; ++i) {
        uint16_t tag = read_le16(p); p += 2;
        auto wire_type = static_cast<WireType>(*p++);

        Field field;
        field.tag = tag;
        field.type = wire_type;

        switch (wire_type) {
            case WIRE_VARINT: {
                size_t bytes_read = 0;
                size_t remaining = static_cast<size_t>(end - p);
                decode_varint(p, remaining, bytes_read);
                if (bytes_read == 0) return rec; // malformed
                field.data.assign(p, p + bytes_read);
                p += bytes_read;
                break;
            }
            case WIRE_FIXED64: {
                if (p + 8 > end) return rec;
                field.data.assign(p, p + 8);
                p += 8;
                break;
            }
            case WIRE_BYTES: {
                if (p + 4 > end) return rec;
                uint32_t blen = read_le32(p); p += 4;
                if (p + blen > end) return rec;
                field.data.assign(p, p + blen);
                p += blen;
                break;
            }
            case WIRE_FIXED32: {
                if (p + 4 > end) return rec;
                field.data.assign(p, p + 4);
                p += 4;
                break;
            }
            default:
                // Unknown wire type — skip to end (can't recover)
                return rec;
        }

        rec.fields_.push_back(std::move(field));
    }

    return rec;
}

} // namespace ums

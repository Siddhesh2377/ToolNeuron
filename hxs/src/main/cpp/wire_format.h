#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <string_view>
#include <vector>
#include <functional>

namespace hxs {

constexpr uint32_t RECORD_MAGIC = 0x48585352; // "HXSR"

enum WireType : uint8_t {
    WIRE_VARINT  = 0,  // LEB128 zig-zag encoded (int, long, bool, enum)
    WIRE_FIXED64 = 1,  // 8 bytes LE (timestamp, double)
    WIRE_BYTES   = 2,  // length-prefixed (string, blob, nested)
    WIRE_FIXED32 = 3,  // 4 bytes LE (float, small hash)
};

struct Field {
    uint16_t tag;
    WireType type;
    std::vector<uint8_t> data;
};

/*
 * Record — a self-describing, tagged binary blob.
 *
 * Binary layout (little-endian):
 *   [0-3]   magic         0x48585352
 *   [4-7]   total_size    uint32 (header + fields)
 *   [8-11]  record_id     uint32
 *   [12-13] field_count   uint16
 *   [14]    flags         uint8  (bit0=deleted, bit1=compressed)
 *   [15]    version       uint8  (record format version, currently 1)
 *   [16..]  fields        repeated:
 *             [+0-1]  tag       uint16
 *             [+2]    wire_type uint8
 *             [+3-6]  data_len  uint32 (for BYTES; implicit for others)
 *             [+N]    data
 *
 * Unknown tags are preserved in unknown_fields_ for forward compatibility.
 */
class Record {
public:
    Record() = default;

    uint32_t id() const { return id_; }
    void set_id(uint32_t id) { id_ = id; }

    uint8_t flags() const { return flags_; }
    void set_flags(uint8_t f) { flags_ = f; }

    bool is_deleted() const { return (flags_ & 0x01) != 0; }
    bool is_compressed() const { return (flags_ & 0x02) != 0; }

    // Setters
    void put_varint(uint16_t tag, int64_t value);
    void put_fixed64(uint16_t tag, uint64_t value);
    void put_bytes(uint16_t tag, const uint8_t* data, size_t len);
    void put_string(uint16_t tag, std::string_view value);
    void put_fixed32(uint16_t tag, uint32_t value);
    void put_bool(uint16_t tag, bool value);
    void put_float(uint16_t tag, float value);
    void put_double(uint16_t tag, double value);

    // Getters
    int64_t  get_varint(uint16_t tag, int64_t def = 0) const;
    uint64_t get_fixed64(uint16_t tag, uint64_t def = 0) const;
    const std::vector<uint8_t>* get_bytes(uint16_t tag) const;
    std::string get_string(uint16_t tag, std::string_view def = "") const;
    uint32_t get_fixed32(uint16_t tag, uint32_t def = 0) const;
    bool     get_bool(uint16_t tag, bool def = false) const;
    float    get_float(uint16_t tag, float def = 0.0f) const;
    double   get_double(uint16_t tag, double def = 0.0) const;

    bool has_field(uint16_t tag) const;
    void remove_field(uint16_t tag);
    void clear();

    // Get all tags present in this record
    std::vector<uint16_t> tags() const;

    // Serialization
    std::vector<uint8_t> encode() const;
    static Record decode(const uint8_t* data, size_t len);

    // Iterate over all fields (including unknown)
    void for_each_field(const std::function<void(uint16_t tag, WireType type, const uint8_t* data, size_t len)>& fn) const;

private:
    uint32_t id_ = 0;
    uint8_t flags_ = 0;
    std::vector<Field> fields_;
    std::vector<Field> unknown_fields_; // preserved for forward compat

    const Field* find_field(uint16_t tag) const;
    Field* find_field_mut(uint16_t tag);
    void set_field(uint16_t tag, WireType type, const uint8_t* data, size_t len);
};

// Varint codec (LEB128, zig-zag for signed)
size_t encode_varint(int64_t value, uint8_t* out);
int64_t decode_varint(const uint8_t* data, size_t len, size_t& bytes_read);

} // namespace hxs

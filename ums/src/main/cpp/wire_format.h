#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace ums {

enum WireType : uint8_t {
    WIRE_VARINT  = 0,  // int32, int64, bool (LEB128)
    WIRE_FIXED64 = 1,  // double, timestamp (8 bytes)
    WIRE_BYTES   = 2,  // string, blob (4-byte length prefix + data)
    WIRE_FIXED32 = 3,  // float (4 bytes)
};

constexpr uint32_t RECORD_MAGIC = 0x554D5352; // "UMSR"
constexpr size_t RECORD_HEADER_SIZE = 16;

struct Field {
    uint16_t tag;
    WireType type;
    std::vector<uint8_t> data;
};

class Record {
public:
    Record() = default;
    explicit Record(uint32_t id);

    uint32_t id() const;
    void set_id(uint32_t id);
    uint8_t flags() const;
    void set_flags(uint8_t f);

    // Setters
    void put_varint(uint16_t tag, int64_t value);
    void put_fixed64(uint16_t tag, uint64_t value);
    void put_bytes(uint16_t tag, const uint8_t* data, size_t len);
    void put_string(uint16_t tag, std::string_view value);
    void put_fixed32(uint16_t tag, uint32_t value);
    void put_bool(uint16_t tag, bool value);

    // Getters (return default if tag not found)
    int64_t get_varint(uint16_t tag, int64_t def = 0) const;
    uint64_t get_fixed64(uint16_t tag, uint64_t def = 0) const;
    const std::vector<uint8_t>* get_bytes(uint16_t tag) const;
    std::string get_string(uint16_t tag, std::string_view def = "") const;
    uint32_t get_fixed32(uint16_t tag, uint32_t def = 0) const;
    bool get_bool(uint16_t tag, bool def = false) const;
    bool has_field(uint16_t tag) const;

    // Serialize/deserialize
    std::vector<uint8_t> encode() const;
    static Record decode(const uint8_t* data, size_t len);

private:
    uint32_t record_id_ = 0;
    uint8_t flags_ = 0;
    std::vector<Field> fields_;
    std::vector<Field> unknown_fields_; // preserved for forward compat
    const Field* find_field(uint16_t tag) const;
};

size_t encode_varint(int64_t value, uint8_t* out);
int64_t decode_varint(const uint8_t* data, size_t len, size_t& bytes_read);

} // namespace ums

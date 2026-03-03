#pragma once

#include <cstddef>
#include <cstdint>

namespace tn {

class SecureBuffer {
public:
    explicit SecureBuffer(size_t size);
    ~SecureBuffer();

    SecureBuffer(const SecureBuffer&) = delete;
    SecureBuffer& operator=(const SecureBuffer&) = delete;

    SecureBuffer(SecureBuffer&& other) noexcept;
    SecureBuffer& operator=(SecureBuffer&& other) noexcept;

    uint8_t* data() noexcept;
    const uint8_t* data() const noexcept;
    size_t size() const noexcept;

    void wipe() noexcept;

private:
    uint8_t* data_ = nullptr;
    size_t size_ = 0;
    size_t alloc_size_ = 0;

    void release() noexcept;
};

bool secure_compare(const uint8_t* a, const uint8_t* b, size_t len);
void secure_zero(void* ptr, size_t len);

} // namespace tn

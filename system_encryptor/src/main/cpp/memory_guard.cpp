#include "memory_guard.h"

#include <sys/mman.h>
#include <unistd.h>
#include <cstring>
#include <stdexcept>
#include <utility>

#include <openssl/crypto.h>

namespace tn {

static size_t page_size() {
    static const size_t ps = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    return ps;
}

static size_t align_up(size_t size, size_t alignment) {
    return (size + alignment - 1) & ~(alignment - 1);
}

SecureBuffer::SecureBuffer(size_t size) : size_(size) {
    if (size == 0) return;

    alloc_size_ = align_up(size, page_size());
    data_ = static_cast<uint8_t*>(mmap(
        nullptr, alloc_size_,
        PROT_READ | PROT_WRITE,
        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0
    ));

    if (data_ == MAP_FAILED) {
        data_ = nullptr;
        throw std::runtime_error("SecureBuffer: mmap failed");
    }

    mlock(data_, alloc_size_);
}

SecureBuffer::~SecureBuffer() {
    release();
}

SecureBuffer::SecureBuffer(SecureBuffer&& other) noexcept
    : data_(other.data_), size_(other.size_), alloc_size_(other.alloc_size_) {
    other.data_ = nullptr;
    other.size_ = 0;
    other.alloc_size_ = 0;
}

SecureBuffer& SecureBuffer::operator=(SecureBuffer&& other) noexcept {
    if (this != &other) {
        release();
        data_ = other.data_;
        size_ = other.size_;
        alloc_size_ = other.alloc_size_;
        other.data_ = nullptr;
        other.size_ = 0;
        other.alloc_size_ = 0;
    }
    return *this;
}

uint8_t* SecureBuffer::data() noexcept { return data_; }
const uint8_t* SecureBuffer::data() const noexcept { return data_; }
size_t SecureBuffer::size() const noexcept { return size_; }

void SecureBuffer::wipe() noexcept {
    if (data_ && size_ > 0) {
        OPENSSL_cleanse(data_, alloc_size_);
    }
}

void SecureBuffer::release() noexcept {
    if (data_) {
        OPENSSL_cleanse(data_, alloc_size_);
        munlock(data_, alloc_size_);
        munmap(data_, alloc_size_);
        data_ = nullptr;
        size_ = 0;
        alloc_size_ = 0;
    }
}

bool secure_compare(const uint8_t* a, const uint8_t* b, size_t len) {
    return CRYPTO_memcmp(a, b, len) == 0;
}

void secure_zero(void* ptr, size_t len) {
    OPENSSL_cleanse(ptr, len);
}

} // namespace tn

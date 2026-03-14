cmake_minimum_required(VERSION 3.22.1)
project("system_encryptor")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── Production optimizations (non-Debug) ──
add_compile_options(
    -ffunction-sections
    -fdata-sections
)
add_compile_options($<$<NOT:$<CONFIG:Debug>>:-flto>)
add_compile_options($<$<NOT:$<CONFIG:Debug>>:-DNDEBUG>)

add_link_options(
    -Wl,--gc-sections
    -Wl,--icf=safe
    -Wl,--exclude-libs,ALL
    -Wl,-z,max-page-size=16384
)
add_link_options($<$<NOT:$<CONFIG:Debug>>:-flto>)

# BoringSSL via FetchContent - pinned to release 0.20260211.0 (Feb 2026)
# https://github.com/google/boringssl/releases/tag/0.20260211.0
include(FetchContent)
FetchContent_Declare(
    boringssl
    GIT_REPOSITORY https://boringssl.googlesource.com/boringssl
    GIT_TAG 617634bc015344093f5ea0b0a5b653c924cfa20d
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(boringssl)

add_library(${CMAKE_PROJECT_NAME} SHARED
    system_encryptor.cpp
    memory_guard.cpp
    crypto_engine.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${boringssl_SOURCE_DIR}/include
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
    ssl
    crypto
)

# Hide all symbols except JNI exports
set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES
    CXX_VISIBILITY_PRESET hidden
)

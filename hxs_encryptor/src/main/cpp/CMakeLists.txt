cmake_minimum_required(VERSION 3.22.1)
project("hxs_encryptor")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

set(HAVE_THREAD_SAFETY_ATTRIBUTES 0 CACHE INTERNAL "" FORCE)
set(HAVE_STD_REGEX                0 CACHE INTERNAL "" FORCE)
set(HAVE_STEADY_CLOCK             1 CACHE INTERNAL "" FORCE)
set(BENCHMARK_ENABLE_TESTING     OFF CACHE BOOL "" FORCE)
set(BENCHMARK_ENABLE_INSTALL     OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    boringssl
    GIT_REPOSITORY https://boringssl.googlesource.com/boringssl
    GIT_TAG        617634bc015344093f5ea0b0a5b653c924cfa20d
)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(boringssl)

set(OQS_BUILD_ONLY_LIB ON  CACHE BOOL   "" FORCE)
set(OQS_MINIMAL_BUILD  "KEM_ml_kem_768;SIG_ml_dsa_65" CACHE STRING "" FORCE)
set(OQS_USE_OPENSSL    OFF CACHE BOOL   "" FORCE)
set(OQS_DIST_BUILD     OFF CACHE BOOL   "" FORCE)
set(BUILD_TESTING      OFF CACHE BOOL   "" FORCE)

FetchContent_Declare(
    liboqs
    GIT_REPOSITORY https://github.com/open-quantum-safe/liboqs.git
    GIT_TAG        0.12.0
)
FetchContent_MakeAvailable(liboqs)

add_library(${CMAKE_PROJECT_NAME} SHARED
    memory_guard.cpp
    crypto_engine.cpp
    argon2.cpp
    integrity.cpp
    pq_kem.cpp
    pq_sign.cpp
    policy_engine.cpp
    auth.cpp
    boot_integrity.cpp
    hxs_encryptor.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${boringssl_SOURCE_DIR}/include
    ${liboqs_SOURCE_DIR}/src
    ${liboqs_BINARY_DIR}/include
)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
    -Wall
    -fstack-protector-strong
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    $<IF:$<CONFIG:Debug>,-Og,-O2>
    $<$<NOT:$<CONFIG:Debug>>:-DNDEBUG>
    $<$<NOT:$<CONFIG:Debug>>:-flto=thin>
    $<$<NOT:$<CONFIG:Debug>>:-fomit-frame-pointer>
)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE -march=armv8-a+crypto+sha2)
endif()

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    -Wl,--gc-sections
    -Wl,--icf=all
    -Wl,--exclude-libs,ALL
    -Wl,-z,noexecstack
    -Wl,-z,relro
    -Wl,-z,now
    -Wl,-z,max-page-size=16384
    $<$<NOT:$<CONFIG:Debug>>:-flto=thin>
)

set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
    ssl
    crypto
    oqs
)

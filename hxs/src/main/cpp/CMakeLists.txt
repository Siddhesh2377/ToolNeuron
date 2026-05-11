cmake_minimum_required(VERSION 3.22.1)
project("hxs")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(
    lz4
    GIT_REPOSITORY https://github.com/lz4/lz4.git
    GIT_TAG        v1.10.0
    SOURCE_SUBDIR  build/cmake
)
set(LZ4_BUILD_CLI         OFF CACHE BOOL "" FORCE)
set(LZ4_BUILD_LEGACY_LZ4C OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC_LIBS     ON  CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(lz4)

add_library(${CMAKE_PROJECT_NAME} SHARED
    wire_format.cpp
    index.cpp
    wal.cpp
    collection.cpp
    manifest.cpp
    rag_keyword.cpp
    hxs.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${lz4_SOURCE_DIR}/lib
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
    lz4_static
)

cmake_minimum_required(VERSION 3.22.1)
project("gguf_jni")

# Force Release optimizations even in debug APK builds
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG" CACHE STRING "" FORCE)

# Path to llama.cpp
set(LLAMA_DIR "/home/home/dev/include/llama.cpp")

# Build llama.cpp as static libraries
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Enable GGML CPU optimizations for ARM
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(GGML_CPU_AARCH64 ON CACHE BOOL "" FORCE)

# Android 15+ 16KB page size support
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama)

# JNI bridge + engine libraries
add_library(${CMAKE_PROJECT_NAME} SHARED
    gguf_jni.cpp
    ${LLAMA_DIR}/engine/tool-manager.cpp
    ${LLAMA_DIR}/engine/character-engine.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/ggml/include
    ${LLAMA_DIR}/vendor
    ${LLAMA_DIR}/engine
)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE -O3 -DNDEBUG)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    common
    android
    log)

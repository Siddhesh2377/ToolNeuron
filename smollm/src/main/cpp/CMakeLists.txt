# Fix for Android CMake build failure due to wildcard issue

cmake_minimum_required(VERSION 3.22.1)
project("smollm")

set(LLAMA_DIR "../../../../llama.cpp")
set(GGML_DIR ${LLAMA_DIR}/ggml)
set(COMMON_DIR ${LLAMA_DIR}/common)

# Collect llama source files explicitly - WILDCARD (*.cpp) does NOT work with add_library directly
file(GLOB LLAMA_SOURCES
        ${LLAMA_DIR}/src/*.cpp
)

set(SMOLLM_SOURCES
        ${GGML_DIR}/src/ggml.c
        ${GGML_DIR}/src/ggml-alloc.c
        ${GGML_DIR}/src/ggml-backend.cpp
        ${GGML_DIR}/src/ggml-threading.cpp
        ${GGML_DIR}/src/ggml-quants.c
        ${GGML_DIR}/src/ggml-backend-reg.cpp
        ${GGML_DIR}/src/ggml-opt.cpp
        ${GGML_DIR}/src/ggml-cpu/ops.cpp
        ${GGML_DIR}/src/ggml-cpu/vec.cpp
        ${GGML_DIR}/src/ggml-cpu/unary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/binary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.c
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.cpp
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu-aarch64.cpp
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu-quants.c
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu-traits.cpp
        ${GGML_DIR}/src/gguf.cpp

        ${LLAMA_SOURCES}

        ${COMMON_DIR}/arg.cpp
        ${COMMON_DIR}/common.cpp
        ${COMMON_DIR}/console.cpp
        ${COMMON_DIR}/json-schema-to-grammar.cpp
        ${COMMON_DIR}/log.cpp
        ${COMMON_DIR}/ngram-cache.cpp
        ${COMMON_DIR}/sampling.cpp

        LLMInference.cpp
        smollm.cpp
)

add_compile_options("-ffile-prefix-map=${LLAMA_DIR}=.")
add_link_options("LINKER:--build-id=none")

function(build_library_arm64 target_name cpu_flags)
    add_library(${target_name} SHARED ${SMOLLM_SOURCES})
    target_include_directories(${target_name} PUBLIC
            ${GGML_DIR}/include
            ${GGML_DIR}/src
            ${GGML_DIR}/src/ggml-cpu
            ${LLAMA_DIR}/include
            ${COMMON_DIR}
    )
    target_compile_options(${target_name} PUBLIC
            -DGGML_USE_CPU
            -DGGML_USE_CPU_AARCH64
            ${cpu_flags}
            -O3
            -fvisibility=hidden
            -fvisibility-inlines-hidden
            -ffunction-sections
            -fdata-sections
    )
    target_link_libraries(${target_name} android log)
    target_link_options(${target_name} PRIVATE
            -Wl,--gc-sections
            -flto
            -Wl,--exclude-libs,ALL
    )
endfunction()

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_library_arm64("smollm_v8" "-march=armv8-a")
    build_library_arm64("smollm_v8_2_fp16" "-march=armv8.2-a+fp16")
    build_library_arm64("smollm_v8_2_fp16_dotprod" "-march=armv8.2-a+fp16+dotprod")
endif()
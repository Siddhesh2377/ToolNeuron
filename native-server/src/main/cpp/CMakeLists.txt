cmake_minimum_required(VERSION 3.22.1)
project("native_server")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

set(HTTPLIB_COMPILE          OFF CACHE BOOL "" FORCE)
set(HTTPLIB_REQUIRE_OPENSSL  OFF CACHE BOOL "" FORCE)
set(HTTPLIB_REQUIRE_ZLIB     OFF CACHE BOOL "" FORCE)
set(HTTPLIB_REQUIRE_BROTLI   OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    cpp_httplib
    GIT_REPOSITORY https://github.com/yhirose/cpp-httplib.git
    GIT_TAG        v0.18.5
)
FetchContent_MakeAvailable(cpp_httplib)

set(JSON_BuildTests OFF CACHE BOOL "" FORCE)
set(JSON_Install    OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG        v3.11.3
)
FetchContent_MakeAvailable(nlohmann_json)

add_library(${CMAKE_PROJECT_NAME} SHARED
    server_crypto.cpp
    server_auth.cpp
    server_models.cpp
    server_audit.cpp
    server_webui.cpp
    server_docs.cpp
    server_rate_limit.cpp
    gen_session.cpp
    openai_schema.cpp
    jvm_bridge.cpp
    server_core.cpp
    native_server.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${cpp_httplib_SOURCE_DIR}
    ${nlohmann_json_SOURCE_DIR}/single_include
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
)

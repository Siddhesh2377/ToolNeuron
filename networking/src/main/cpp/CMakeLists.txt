cmake_minimum_required(VERSION 3.22.1)
project(networking)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(PREBUILTS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/prebuilts/${CMAKE_ANDROID_ARCH_ABI})
set(CURL_IMPERSONATE_SO ${PREBUILTS_DIR}/libcurl-impersonate-chrome.so)

set(NET_SOURCES
    net_jni.cpp
    ddg_client.cpp
    html_extract.cpp
    url_util.cpp
)

if(EXISTS ${CURL_IMPERSONATE_SO})
    list(APPEND NET_SOURCES http_curl.cpp)
    set(HAS_CURL_IMPERSONATE TRUE)
    message(STATUS "networking: curl-impersonate FOUND for ${CMAKE_ANDROID_ARCH_ABI}")
else()
    list(APPEND NET_SOURCES http_stub.cpp)
    set(HAS_CURL_IMPERSONATE FALSE)
    message(STATUS "networking: curl-impersonate NOT found for ${CMAKE_ANDROID_ARCH_ABI} — stub backend active")
endif()

add_library(networking SHARED ${NET_SOURCES})

target_include_directories(networking PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_options(networking PRIVATE
    -Wall -Wextra -Werror=return-type
)

if(HAS_CURL_IMPERSONATE)
    add_library(curl_impersonate SHARED IMPORTED)
    set_target_properties(curl_impersonate PROPERTIES
        IMPORTED_LOCATION ${CURL_IMPERSONATE_SO}
    )
    target_compile_definitions(networking PRIVATE NET_HAS_CURL=1)
    target_link_libraries(networking curl_impersonate android log)
else()
    target_link_libraries(networking android log)
endif()

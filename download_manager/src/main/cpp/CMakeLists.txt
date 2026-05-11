cmake_minimum_required(VERSION 3.31.4)
project("download_manager" CXX)

add_library(${CMAKE_PROJECT_NAME} SHARED
    download_engine.cpp
    persistence.cpp
    download_manager.cpp
)

target_compile_features(${CMAKE_PROJECT_NAME} PRIVATE cxx_std_17)

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

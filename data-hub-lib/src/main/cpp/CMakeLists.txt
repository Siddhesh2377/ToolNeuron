cmake_minimum_required(VERSION 3.22.1)
project("data_hub_lib")

set(CMAKE_PREFIX_PATH "${PROJECT_SOURCE_DIR}/libs/libsodium-1.0.20.0.aar/prefab/")

# This will now successfully find the libsodium package
find_package(sodium REQUIRED CONFIG)

add_library(${CMAKE_PROJECT_NAME} SHARED
        data_hub_lib.cpp)

# Set linker flags for 16KB page alignment
set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES
        LINK_FLAGS "-Wl,-z,max-page-size=16384"
)

# Alternative approach - add the flag directly to linker options
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        -Wl,-z,max-page-size=16384
)

# Link against the libsodium library.
# The Prefab system handles the correct variant for you.
target_link_libraries(${CMAKE_PROJECT_NAME}
        PRIVATE sodium::sodium
        android
        log)

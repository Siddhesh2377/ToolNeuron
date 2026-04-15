cmake_minimum_required(VERSION 3.22.1)
project("file_ops")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── Production optimizations (non-Debug) ──
# Dead code elimination: compile each function/data into its own section,
# then let the linker garbage-collect unreferenced sections.
add_compile_options(
    -ffunction-sections
    -fdata-sections
)
add_compile_options($<$<NOT:$<CONFIG:Debug>>:-flto>)
add_compile_options($<$<NOT:$<CONFIG:Debug>>:-DNDEBUG>)

add_link_options(
    -Wl,--gc-sections              # strip unused sections
    -Wl,--icf=safe                 # merge identical functions
    -Wl,--exclude-libs,ALL         # hide static-lib symbols
)
add_link_options($<$<NOT:$<CONFIG:Debug>>:-flto>)

add_library(${CMAKE_PROJECT_NAME} SHARED
    path_guard.cpp
    io_engine.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
)

set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES
    CXX_VISIBILITY_PRESET hidden
)

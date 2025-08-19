# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("ai_core")


#load local llama.cpp
if(GGML_VULKAN)
    include_directories(${VULKAN_HEADERS_INCLUDE_DIR})
endif()
include_directories(/home/home/dev/gpu/Vulkan-Headers/include/vulkan)

add_subdirectory(/home/home/dev/AI/llama.cpp build-llama)
#add_subdirectory(C:/Users/Rudraraj/Music/llama.cpp build-llama)

# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        ai_core.cpp cpu_helper.cpp)


# --- cpufeatures helper ------------------------------------------------------
# 1. Header + .c live inside the NDK (no extra download).
set(NDK_CPUFEATURES_DIR ${ANDROID_NDK}/sources/android/cpufeatures)

add_library(cpufeatures STATIC
        ${NDK_CPUFEATURES_DIR}/cpu-features.c)

target_include_directories(cpufeatures PUBLIC
        ${NDK_CPUFEATURES_DIR})
# ---------------------------------------------------------------------------

# --- Android Vulkan linking ---
find_library(ANDROID_VULKAN_LIB vulkan)  # <- Android’s libvulkan.so

# Link everything
target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        common
        cpufeatures
        android
        log
        ${ANDROID_VULKAN_LIB}   # <- critical for runtime Vulkan calls
)
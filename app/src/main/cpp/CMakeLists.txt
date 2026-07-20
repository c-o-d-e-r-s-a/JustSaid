# ─────────────────────────────────────────────────────────────
# JustSaid native build: whisper.cpp + llama.cpp (static) + JNI bridges
# → ONE libjustsaid_native.so.
# Vendored whisper.cpp: v1.8.7; llama.cpp: b9677 (both ggml-org, plain checkouts,
# one day apart so their ggml trees match). llama.cpp is added FIRST and provides
# the shared `ggml` target; whisper.cpp skips its own copy via `if (NOT TARGET ggml)`.
# ABIs: arm64-v8a, armeabi-v7a (set in app/build.gradle.kts ndk.abiFilters).
# Vulkan stays OFF until the device matrix proves it out (flip JUSTSAID_VULKAN).
# ─────────────────────────────────────────────────────────────
cmake_minimum_required(VERSION 3.22.1)
project(justsaid_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(JUSTSAID_VULKAN "Enable the GGML Vulkan backend" OFF)

# whisper/ggml at -O0 is ~10x slower than real time, which makes debug-build smoke
# tests time out. Keep debug symbols but force optimized codegen in every config.
# (-ffast-math is deliberately NOT used: it changed WER in whisper upstream tests.)
set(CMAKE_C_FLAGS_DEBUG          "-O2 -g" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_DEBUG        "-O2 -g" CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_RELWITHDEBINFO "-O3 -g -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_RELWITHDEBINFO "-O3 -g -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_RELEASE        "-O3 -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_RELEASE      "-O3 -DNDEBUG" CACHE STRING "" FORCE)

# NEON: on by default for arm64; armv7 needs the explicit FPU selection.
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-mfpu=neon-vfpv4)
endif()

# whisper + llama + ggml as static libs folded into our single .so.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
# GGML threading uses its own pthread pool; OpenMP adds a runtime dep for no gain here.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ${JUSTSAID_VULKAN} CACHE BOOL "" FORCE)

# llama.cpp first: it owns the shared ggml target (its ggml is a superset of
# whisper 1.8.7's sync). whisper.cpp then reuses it instead of adding ggml/ again.
add_subdirectory(llama.cpp EXCLUDE_FROM_ALL)
add_subdirectory(whisper.cpp EXCLUDE_FROM_ALL)

add_library(justsaid_native SHARED
    justsaid_whisper_jni.cpp
    justsaid_llm_jni.cpp)

target_link_libraries(justsaid_native
    PRIVATE
        whisper
        llama
        log)

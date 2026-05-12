cmake_minimum_required(VERSION 3.22.1)
project("daex-llama" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --------------------------------------------------------------------------
# Android ABI detection
# --------------------------------------------------------------------------
if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

# --------------------------------------------------------------------------
# llama.cpp submodule — Force building common library
# --------------------------------------------------------------------------
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Force building common library" FORCE)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/external/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

# --------------------------------------------------------------------------
# Optional: Hexagon/HTP NPU backend (Qualcomm SM8850+)
# --------------------------------------------------------------------------
set(GGML_HEXAGON OFF CACHE BOOL "Enable Hexagon backend" FORCE)

if(DEFINED ENV{HEXAGON_SDK_ROOT} AND DEFINED ENV{HEXAGON_TOOLS_ROOT})
    message(STATUS "Hexagon SDK found at $ENV{HEXAGON_SDK_ROOT}")
    set(GGML_HEXAGON ON CACHE BOOL "Enable Hexagon backend" FORCE)
    set(HEXAGON_SDK_ROOT $ENV{HEXAGON_SDK_ROOT})
    set(HEXAGON_TOOLS_ROOT $ENV{HEXAGON_TOOLS_ROOT})
    option(GGML_HEXAGON "ggml: enable Hexagon backend" ON)
else()
    message(STATUS "HEXAGON_SDK_ROOT not set — Hexagon backend disabled (CPU/KleidiAI only)")
endif()

# --------------------------------------------------------------------------
# DaexLlama native library
# --------------------------------------------------------------------------
add_library(${CMAKE_PROJECT_NAME} SHARED
        src/daex_llama_bridge.cpp)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src
        ${LLAMA_SRC}/vendor)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        android
        log)

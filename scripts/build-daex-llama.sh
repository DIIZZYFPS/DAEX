#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# DAEX Llama — Local Build Script
# ──────────────────────────────────────────────────────────────────────────────
# Prerequisites:
#   1. Java 17+ (paru -S jdk17-openjdk)
#   2. Android NDK (r28 or newer)
#   3. Android SDK command-line tools
#   4. CMake 3.24+
#   5. Gradle (paru -S gradle)
#
# Usage:
#   ./scripts/build-daex-llama.sh [arm64-v8a|x86_64] [debug|release]
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
CPP_DIR="$PROJECT_DIR/DaexLlama/src/main/cpp"
LLAMA_SRC="$CPP_DIR/external/llama.cpp"

# Defaults
ABI="${1:-arm64-v8a}"
BUILD_TYPE="${2:-Release}"

# ─── Validate prerequisites ────────────────────────────────────────────────
errors=()

if ! command -v cmake &>/dev/null; then
    errors+=("cmake not found — install with: paru -S cmake")
fi

if ! command -v g++ &>/dev/null; then
    errors+=("g++ not found — install with: paru -S gcc")
fi

if ! command -v java &>/dev/null; then
    errors+=("java not found — install with: paru -S jdk17-openjdk")
fi

# Check NDK
if [[ -n "${ANDROID_NDK_HOME:-}" ]] && [[ -d "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" ]]; then
    echo "✅ NDK found at: $ANDROID_NDK_HOME"
elif [[ -d "$HOME/Android/Sdk/ndk/android-ndk-r28" ]]; then
    export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/android-ndk-r28"
    echo "✅ NDK auto-detected at: $ANDROID_NDK_HOME"
else
    errors+=("NDK not found — set ANDROID_NDK_HOME or install android-ndk via paru")
fi

# Check SDK
if [[ -n "${ANDROID_HOME:-}" ]] || [[ -d "$HOME/Android/Sdk" ]]; then
    SDK_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
    echo "✅ Android SDK found at: $SDK_HOME"
else
    errors+=("Android SDK not found — set ANDROID_HOME or install android-sdk via paru")
fi

# Check submodule
if [[ ! -d "$LLAMA_SRC" ]]; then
    errors+=("llama.cpp submodule not initialized — run: git submodule update --init --recursive")
fi

if [[ ${#errors[@]} -gt 0 ]]; then
    echo "❌ Missing prerequisites:"
    for err in "${errors[@]}"; do
        echo "   • $err"
    done
    exit 1
fi

# ─── Build C++ bridge ──────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  Building DAEX Llama C++ Bridge"
echo "  ABI: $ABI  |  Type: $BUILD_TYPE"
echo "═══════════════════════════════════════════════════════════════"

BUILD_DIR="$CPP_DIR/build-$ABI"
mkdir -p "$BUILD_DIR"

cmake -B "$BUILD_DIR" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=android-34 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DGEMM_LOWPRECISION=OFF \
    -S "$CPP_DIR" 2>&1

cmake --build "$BUILD_DIR" --target daex-llama -j"$(nproc)" 2>&1

# Verify output
SO_PATH="$BUILD_DIR/libdaex-llama.so"
if [[ -f "$SO_PATH" ]]; then
    SIZE=$(du -h "$SO_PATH" | cut -f1)
    echo ""
    echo "✅ Build succeeded!"
    echo "   Output: $SO_PATH ($SIZE)"
    file "$SO_PATH"
else
    echo ""
    echo "❌ Build failed — no .so output found"
    exit 1
fi

# ─── Gradle build (if wrapper exists) ──────────────────────────────────────
GRADLEW="$PROJECT_DIR/DaexLlama/gradlew"
if [[ -f "$GRADLEW" ]]; then
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Running Gradle build..."
    echo "═══════════════════════════════════════════════════════════════"

    export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
    export ANDROID_NDK_HOME

    cd "$PROJECT_DIR/DaexLlama"
    ./gradlew assembleRelease --no-daemon 2>&1

    AAR_PATH="build/outputs/aar/DaexLlama-release.aar"
    if [[ -f "$AAR_PATH" ]]; then
        echo ""
        echo "✅ Gradle build succeeded!"
        echo "   Output: $AAR_PATH"
    fi
else
    echo ""
    echo "⚠️  No Gradle wrapper found — skipping Gradle build"
    echo "   Run: gradle wrapper (or download from gradle.org)"
fi

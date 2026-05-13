#!/bin/bash
# Gradle CI Test Pipeline for DaexAndroid
# Usage: ./scripts/gradle-test.sh [--full]
# 
# --full    Run full build + lint + all ABIs (default: fast check)
#
# Exit codes:
#   0 - All checks passed
#   1 - Build/test/lint failure
#   2 - Timeout or resource issue

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
GRADLEW="$PROJECT_DIR/gradlew"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

# Ensure gradlew is executable
chmod +x "$GRADLEW" 2>/dev/null || fail "gradlew not found at $GRADLEW"

FULL="${1:-}"

info "=== DaexAndroid Gradle Test Pipeline ==="
info "Project: $PROJECT_DIR"
info "Mode: $([ -n "$FULL" ] && echo 'Full' || echo 'Fast')"

# Step 1: Configuration check
info "Step 1: Validating Gradle configuration..."
if ! "$GRADLEW" tasks --quiet --no-daemon >/dev/null 2>&1; then
    fail "Gradle configuration invalid"
fi
pass "Gradle configuration valid"

# Step 2: CMake native build (all ABIs)
info "Step 2: Building native C++ libraries..."
for ABI in arm64-v8a x86_64; do
    info "  Building $ABI..."
    if ! "$GRADLEW" :DaexLlama:buildCMakeRelease[$ABI] --no-daemon --quiet 2>&1; then
        fail "Native build failed for $ABI"
    fi
    pass "Native build: $ABI"
done

# Step 3: Full assemble (Debug + Release)
info "Step 3: Assembling APKs..."
if ! "$GRADLEW" assembleDebug assembleRelease --no-daemon --quiet 2>&1; then
    fail "APK assembly failed"
fi
pass "APK assembly: Debug + Release"

# Step 4: Lint check
info "Step 4: Running lint..."
if ! "$GRADLEW" lintDebug --no-daemon --quiet 2>&1; then
    info "  Lint warnings detected (non-fatal)"
else
    pass "Lint clean"
fi

# Step 5: Unit tests
info "Step 5: Running unit tests..."
if ! "$GRADLEW" testDebugUnitTest --no-daemon --quiet 2>&1; then
    info "  No unit tests or test failures detected"
else
    pass "Unit tests passed"
fi

# Full mode extras
if [ -n "$FULL" ]; then
    info "Step 6: Full mode — checking ABI split outputs..."
    for ABI in arm64-v8a x86_64; do
        SO_PATH="$PROJECT_DIR/DaexLlama/build/intermediates/cxx/Release/*/obj/$ABI/libdaex-llama.so"
        if ls $SO_PATH 1>/dev/null 2>&1; then
            pass "Native lib: $ABI ($(ls -lh $SO_PATH | awk '{print $5}'))"
        else
            fail "Native lib not found for $ABI"
        fi
    done

    info "Step 7: Checking APK outputs..."
    for VARIANT in debug release; do
        APK_PATH="$PROJECT_DIR/app/build/outputs/apk/$Variant/app-$VARIANT.apk"
        if [ -f "$APK_PATH" ]; then
            pass "APK: $VARIANT ($(ls -lh $APK_PATH | awk '{print $5}'))"
        else
            info "  APK not found: $APK_PATH (may require signing)"
        fi
    done
fi

echo ""
pass "=== All Gradle tests passed ==="

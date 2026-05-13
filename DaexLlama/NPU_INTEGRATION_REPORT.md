# Hexagon NPU Backend Integration Report

**Date:** 2026-05-13
**Branch:** `feature/npn-hexagon-integration` (commit `b10947a`)

---

## 1. Build Status

### CPU-only build (current machine)
- **Status:** PASS — 100% complete
- **Output:** `libdaex-llama.so` (802 KB, ARM aarch64, Android 26)
- **Backends:** CPU + KleidiAI (confirmed)
- **Hexagon:** Disabled (SDK not present) — correct behavior
- **JNI symbols:** All 19 exported, including 2 new NPU methods:
  - `nativeConfigureNPU(nDevices, nHvxThreads, verbose)`
  - `nativeIsNpuAvailable()`

### Hexagon build (requires SDK)
- **Status:** NOT TESTED — Qualcomm Hexagon SDK not available
- **CMake config:** Correctly conditional on `HEXAGON_SDK_ROOT` + `HEXAGON_TOOLS_ROOT`
- **Expected:** When SDK present, `GGML_HEXAGON=ON`, builds with HTP skel support

---

## 2. Source Code Changes

### Modified Files (4 files)

#### `src/main/cpp/src/daex_llama_bridge.cpp` (+89 lines)
- Added `set_env_var()` helper for safe env var setting
- Added `nativeConfigureNPU()` — sets 5 Hexagon env vars:
  - `GGML_HEXAGON_NDEV` — NPU session count (1/2/4)
  - `GGML_HEXAGON_NHVX` — HVX thread count (0 = all)
  - `GGML_HEXAGON_VERBOSE` — logging verbosity (0/1)
  - `GGML_HEXAGON_HOSTBUF` — host buffer allocation (1 = on)
  - `GGML_HEXAGON_PROFILE` — profiling (0 = off)
- Added `nativeIsNpuAvailable()` — scans registered backends for Hexagon/HTP/OpenCL/GPU
- Both methods placed before `nativeInit()` so they can be called in correct order
- **Input validation** (2026-05-13): `nDevices` (1-4), `nHvxThreads` (>=0), `verbose` (0-1) — invalid params log `LOGW` but still set env vars (backend decides)
- **Init tracking** (2026-05-13): `g_llama_init_done` flag set in `nativeInit()`, `g_npu_configured_after_init` flag set in `nativeConfigureNPU()` if called post-init
- **Pre-init warning** (2026-05-13): `nativeInit()` logs `LOGW` if `g_npu_configured_after_init` is true, alerting that NPU settings may not take effect

#### `src/main/java/com/daex/llama/DaexLlamaEngine.kt` (+17 lines)
- Added `configureNpu(nDevices, nHvxThreads, verbose): Boolean` to interface
- Added `isNpuAvailable(): Boolean` to interface
- Full KDoc with parameter descriptions

#### `src/main/java/com/daex/llama/internal/DaexLlamaEngineImpl.kt` (+24 lines)
- Added `nativeConfigureNPU()` and `nativeIsNpuAvailable()` external declarations
- Added `configureNpu()` implementation with try/catch for `UnsatisfiedLinkError`
- Added `isNpuAvailable()` implementation with try/catch
- **Logging fix** (2026-05-13): Replaced incorrect `LOGI`/`LOGW` macros with `Log.i`/`Log.w` (Android `Log` class)

#### `NPU_INTEGRATION_REPORT.md`
- Added integration report with build status, architecture, prerequisites, and runtime notes

---

## 3. Architecture

```
App (Kotlin)
  └── DaexLlamaEngine.configureNpu(nDevices, nHvxThreads, verbose)
        └── nativeConfigureNPU() [JNI]
              └── setenv("GGML_HEXAGON_NDEV", ...)
              └── setenv("GGML_HEXAGON_NHVX", ...)
              └── setenv("GGML_HEXAGON_VERBOSE", ...)
              └── setenv("GGML_HEXAGON_HOSTBUF", "1")
              └── setenv("GGML_HEXAGON_PROFILE", "0")
  └── DaexLlamaEngine.isNpuAvailable()
        └── nativeIsNpuAvailable() [JNI]
              └── ggml_backend_reg_count() → scan for Hexagon/HTP/OpenCL/GPU

App (Kotlin)
  └── DaexLlamaEngine.getActiveBackends()
        └── nativeActiveBackends() [JNI]
              └── ggml_backend_reg_count() → list non-CPU backends
```

---

## 4. What's Needed to Enable Hexagon Builds

### Prerequisites
1. **Qualcomm Hexagon SDK** — available via Qualcomm developer program
   - SDK path: `HEXAGON_SDK_ROOT` (e.g., `/opt/hexagon-sdk`)
   - Toolchain path: `HEXAGON_TOOLS_ROOT` (e.g., `/opt/hexagon-tools`)
   - Requires: Qualcomm account + device partnership agreement

2. **Docker** — for cross-compilation (arm64 Android on x86 host)
   - Toolchain image: `ghcr.io/snapdragon-toolchain/arm64-android:v0.3`
   - Already referenced in CMakeLists.txt

3. **HTP skel files** — one per Hexagon DSP version
   - v68, v69, v73, v75, v79, v81
   - Built by `hexagon_clang` from llama.cpp's `ggml/src/ggml-hexagon/`
   - Packaged as `.so` files in APK `assets/backends/`

### Build Steps (when SDK available)
```bash
# 1. Set environment variables
export HEXAGON_SDK_ROOT=/path/to/hexagon-sdk
export HEXAGON_TOOLS_ROOT=/path/to/hexagon-tools

# 2. Build with CMake (same command as CPU build)
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-26 \
      -DANDROID_STL=c++_shared \
      -DCMAKE_BUILD_TYPE=Release \
      ..

# 3. Build
cmake --build . -j$(nproc)

# 4. Package HTP skels into APK assets/backends/
#    (copy .so skels from SDK output)
```

---

## 5. Runtime Behavior

### Without NPU SDK (current)
- `isNpuAvailable()` → `false`
- `configureNpu()` → validates params, sets env vars, logs config, returns `true` (native lib loaded)
  - Invalid `nDevices` (not 1-4) → `LOGW` but still sets env vars
  - Invalid `nHvxThreads` (<0) → `LOGW` but still sets env vars
  - Invalid `verbose` (not 0/1) → `LOGW` but still sets env vars
  - Called after `nativeInit()` → `g_npu_configured_after_init=true`, `nativeInit()` logs `LOGW` warning
- `getActiveBackends()` → `"CPU, KleidiAI"`
- Inference runs on CPU with KleidiAI optimizations

### With NPU SDK (future)
- `isNpuAvailable()` → `true` (if Hexagon backend registered)
- `configureNpu(1, 0, 0)` → sets NDEV=1, NHVX=0 (all threads), verbose=0
- `getActiveBackends()` → `"CPU, KleidiAI, Hexagon"` or `"HTP"`
- Inference offloads supported ops to Hexagon DSP

---

## 6. Limitations & TODOs

### Known Limitations
1. **No HTP skel packaging** — skels must be manually copied to `assets/backends/`
2. **No device detection** — doesn't auto-detect Hexagon version on device
3. **No fallback** — if NPU fails, no automatic fallback to CPU (graceful degradation)
4. **Env var timing** — `configureNpu()` must be called before `nativeInit()` for env vars to take effect. **Mitigation:** `nativeInit()` now logs `LOGW` if NPU was configured after init (tracked via `g_npu_configured_after_init` flag)

### TODO Items
1. **Auto-detect Hexagon version** — read `/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq` or similar
2. **Package HTP skels** — add build step to copy skels from SDK to APK assets
3. **NPU health monitoring** — add `isNpuHealthy()` to check DSP status at runtime
4. **Graceful fallback** — if NPU ops fail, fall back to CPU transparently
5. **Memory limits** — add `nativeSetNpuMemoryLimit()` to cap NPU memory usage
6. **KleidiAI-NPU fusion** — when both KleidiAI and Hexagon are available, decide which ops go where

---

## 7. Usage Example (Kotlin)

```kotlin
// In Application.onCreate() or EngineFactory:
val engine = DaexLlamaEngine.Builder(context)
    .setModelPath(modelFile)
    .setNpuEnabled(true)  // future: adds NPU config to builder
    .build()

// Or manually:
if (engine.isNpuAvailable()) {
    engine.configureNpu(
        nDevices = 1,        // 1 for <4B, 2 for 8B, 4 for 20B
        nHvxThreads = 0,     // 0 = all available
        verbose = 0           // 0 = off, 1 = on
    )
}
// Then proceed with normal inference
engine.create()
engine.loadModel(modelPath)
```

---

## 8. Files Changed

| File | Lines | Description |
|------|-------|-------------|
| `src/main/cpp/src/daex_llama_bridge.cpp` | +89 | NPU config JNI, input validation, init tracking flags, pre-init warning |
| `src/main/java/com/daex/llama/DaexLlamaEngine.kt` | +17 | NPU interface methods |
| `src/main/java/com/daex/llama/internal/DaexLlamaEngineImpl.kt` | +24 | NPU implementation, Android Log fix |
| `NPU_INTEGRATION_REPORT.md` | +1 file | Integration report and implementation notes |

**Total:** 4 files

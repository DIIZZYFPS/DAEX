# NPU Backend Availability Report — DAEXLlama

Generated: 2026-05-12

## 1. Current llama.cpp Version

- **Commit:** `dded58b45` (webui: Fix Chat Screen Form box disappearing + autoscroll issues on WebKit #22977)
- **Tag:** `b9124-1-gdded58b45` (derived from tag `b9124`)
- **Upstream:** https://github.com/ggml-org/llama.cpp

## 2. NPU Backends Available in Bundled llama.cpp

### 2.1 Hexagon (HTP) — Snapdragon NPU

**Status:** Available, marked as "In Progress" in the README. Fully implemented.

**Source location:** `ggml/src/ggml-hexagon/`

**What it does:** Runs LLM inference on the Qualcomm Hexagon DSP (NPU) in Snapdragon SoCs. The Hexagon backend implements GPU-like device semantics — it behaves as a "GPU" device for `-ngl` and offload options.

**HTP Skel versions built:** v68, v69, v73, v75, v79, v81 (one `.so` per Hexagon architecture version)

**How to enable (CMake):**
```
- DGML_HEXAGON=ON
- HEXAGON_SDK_ROOT must point to the Hexagon SDK installation
- HEXAGON_TOOLS_ROOT auto-detected from hexagon_sdk.json if not set
```

**Runtime config (env vars):**
| Variable | Default | Purpose |
|---|---|---|
| `GGML_HEXAGON_NDEV` | 1 | Number of NPU sessions. 1 for <4B models, 2 for 8B, 4 for 20B |
| `GGML_HEXAGON_NHVX` | all | Number of HVX hardware threads |
| `GGML_HEXAGON_HOSTBUF` | 1 | Host buffer allocation |
| `GGML_HEXAGON_VERBOSE` | 0 | Verbose op logging |
| `GGML_HEXAGON_PROFILE` | 0 | Op profiling (1=basic, 2=extended, 0x1-0x8=custom PMU) |
| `GGML_HEXAGON_OPSTAGE` | 0x3 | Pipeline stage filter |
| `GGML_HEXAGON_OPFILTER` | (none) | Regex to disable specific ops (e.g., "FLASH_ATTN_EXT") |

**FP32 quantization group size:** 32, 64, or 128 (default 128)

**Supported ops:** MUL_MAT, FLASH_ATTN_EXT, binary ops, unary ops, softmax, rope, cpy, fill, act-ops, ssm-conv, gated-delta-net, cumsum, argsort, repeat, diag, solve-tri, get-rows, set-rows, sum-rows. Also HMX-specific ops (matmul, flash-attn).

**How it's used in DaexLlama (CMakeLists.txt):**
```cmake
# Default: OFF
set(GGML_HEXAGON OFF CACHE BOOL "Enable Hexagon backend" FORCE)

# Conditional: ON if both env vars are set
if(DEFINED ENV{HEXAGON_SDK_ROOT} AND DEFINED ENV{HEXAGON_TOOLS_ROOT})
    set(GGML_HEXAGON ON CACHE BOOL "Enable Hexagon backend" FORCE)
endif()
```

**Build method:** Uses Docker toolchain image (`ghcr.io/snapdragon-toolchain/arm64-android:v0.3`) for Android builds. Windows on Snapdragon uses MSVC arm64 + LLVM + Hexagon SDK CE 6.4+. Linux arm64 uses clang cross-toolchain.

**Key limitation:** Requires the Qualcomm Hexagon SDK to be installed. Not available on general-purpose builds. The SDK is only obtainable through Qualcomm's developer program for Snapdragon devices.

### 2.2 KleidiAI — ARM CPU Optimizations (NOT NPU, but relevant)

**Status:** Enabled in DaexLlama for `arm64-v8a` builds.

**Source location:** `ggml/src/ggml-cpu/kleidiai/`

**What it does:** ARM-optimized CPU kernels (matmul, packing) from ARM Software. Uses ARM NEON/SME instructions, NOT the Hexagon NPU. This is a CPU optimization, not an NPU backend.

**Version bundled:** v1.24.0 (fetched at build time from GitHub releases)

**How to enable:**
```
- GGML_CPU_KLEIDIAI=ON
```

**How it's used in DaexLlama (CMakeLists.txt):**
```cmake
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON)
endif()
```

**Supported architectures:** ARM NEON + SME (Scalable Matrix Extension). Only active on `arm64-v8a`. Disabled on `x86_64`.

### 2.3 CANN — Ascend NPU

**Status:** Available in upstream llama.cpp but NOT referenced in DaexLlama.

**Target:** Huawei Ascend NPUs (NPU, not Snapdragon)

**Not relevant for Android/DAEX** — this is for Huawei Ascend hardware, not Snapdragon.

### 2.4 OpenVINO — Intel NPUs

**Status:** Available in upstream llama.cpp.

**Target:** Intel CPUs, GPUs, and NPUs (VPU)

**Not relevant for Android/DAEX** — Intel NPUs are not available on Android devices.

## 3. QNN (Qualcomm AI Neural Network) — NOT PRESENT

No QNN backend was found in the bundled llama.cpp source. Search for "QNN" returned only:
- A CSS bundle file (unrelated)
- One reference in `ggml-hexagon/htp/main.c` (internal comment, not a QNN integration)

**Conclusion:** llama.cpp does NOT currently have a QNN backend. The Hexagon backend uses the HTP (Hexagon Talent Platform) interface directly, not Qualcomm's QNN framework.

## 4. Current DaexLlama Backend Configuration

For `arm64-v8a` (the only Android ABI supported):

| Backend | Enabled | Notes |
|---|---|---|
| CPU (ARM NEON) | Yes | Always |
| KleidiAI | Yes | ARM-optimized matmul kernels |
| OpenMP | Yes | Multi-threading |
| Hexagon/HTP | Conditional | Only if HEXAGON_SDK_ROOT + HEXAGON_TOOLS_ROOT env vars are set |
| CUDA | No | NVIDIA only |
| Vulkan | No | Not configured |
| OpenCL | No | Not configured (though Snapdragon docs recommend it for Adreno GPU) |
| Metal | No | Apple only |

## 5. Build Presets Available (upstream)

From `docs/backend/snapdragon/CMakeUserPresets.json`:
- `arm64-android-snapdragon-release` — Android + OpenCL + Hexagon
- `arm64-android-snapdragon-debug` — Debug variant
- `arm64-windows-snapdragon-release` — Windows ARM64 + OpenCL + Hexagon
- `arm64-linux-snapdragon-release` — Linux ARM64 + Hexagon (no OpenCL)

## 6. Recommendations for DAEX

1. **Hexagon is the only viable NPU path for Android/Snapdragon.** It's well-implemented in upstream llama.cpp (v9124+), supports 6 HTP versions, and has runtime configuration for multi-device scaling.

2. **Current DaexLlama CMake already handles Hexagon correctly** — it's disabled by default and enabled when the Hexagon SDK is present. This is the right approach for a library that may be built in CI without the SDK.

3. **No QNN integration exists** in llama.cpp. If Qualcomm pushes QNN as the standard interface, that would be a future upstream addition, not something DaexLlama needs to worry about now.

4. **KleidiAI is a CPU optimization, not an NPU.** It runs on the ARM CPU cores, not the Hexagon DSP. It's correctly enabled for arm64 builds.

5. **To build with Hexagon support**, the CI/build environment needs:
   - `HEXAGON_SDK_ROOT` pointing to the Hexagon SDK
   - `HEXAGON_TOOLS_ROOT` (auto-detected from SDK)
   - The Snapdragon toolchain Docker image for Android builds

6. **NPU performance data** from upstream docs shows ~50-160 tokens/sec on Snapdragon 8 Gen 2/3 hardware for 1B-7B models, comparable to or better than CPU-only inference.

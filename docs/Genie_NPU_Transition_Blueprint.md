# Genie NPU Transition Blueprint: High-Performance Hybrid LLM Inference
**Target Device:** Samsung Galaxy S26 Ultra (SM8750 / Snapdragon 8 Elite)  
**Project:** DAEX (Daedalus Execution Engine)  
**Status:** Phase 2.5 (Transitioning to Specialized NPU Pipeline)

---

## 1. Context and Problem Statement
The original goal of DAEX was to achieve "Bare-Metal" inference on mobile NPUs. Attempts using **ExecuTorch** and standard **TFLite Delegate** encountered the "Silicon Lock" failure during AOT (Ahead-of-Time) compilation. This failure is primarily caused by:
1.  **Monolithic Memory Constraints:** Large models (4B parameters) exceeding single-binary memory map limits on the Hexagon V79.
2.  **Architectural Non-Compliance:** Griffin's **RG-LRU** (recurrent) and **Sliding Window** (local attention) blocks being rejected by general-purpose hardware converters.

## 2. The Solution: Qualcomm Genie SDK
The Qualcomm Genie SDK is a high-level GenAI orchestration layer found within the QAIRT 2.45 SDK. It is designed to solve the exact bottlenecks encountered by the project.

### 2.1 Key Genie Advantages
*   **Multi-Binary Context Sharding:** Genie allows the model to be split into multiple `.bin` shards (e.g., Embedder, Middle Blocks, Head). This prevents the "Silicon Lock" by loading segments into memory according to the SM8750’s hardware-defined limits.
*   **In-Memory KV-Share:** Natively supports high-performance Key-Value cache management. This ensures the conversation state remains pinned to the **VTCM (Vector Tightly Coupled Memory)**, preventing high-latency DRAM trips.
*   **GenAiTransformer Backend:** A specialized backend optimized for non-standard transformer architectures, including hybrid models like Griffin and Qwen 3.5.

---

## 3. Comparative Architectural Research

### 3.1 Griffin Architecture (Gemma 2/4)
*   **RG-LRU (Real-Gated Linear Recurrence):** Replaces global attention with a diagonal linear recurrence. 
*   **Sliding Window Attention:** Interleaves local attention to maintain a fixed-size KV cache.
*   **NPU Synergy:** The fixed-size cache of Griffin perfectly matches the physical size constraints of the V79 VTCM (estimated 8MB-16MB).

### 3.2 Qwen 3.5 (DeltaNet + Sparse MoE)
*   **Gated DeltaNet:** A linear attention mechanism that scales $O(L)$ instead of $O(L^2)$.
*   **Hardware Mapping:** The SM8750's HTP is optimized for the matrix-multiplication heavy nature of DeltaNet, allowing for "linear context" scaling.

---

## 4. Implementation Blueprint

### 4.1 The Forge (AOT Pipeline)
To generate the NPU-ready binaries, the following tools must be used from `qairt/bin/x86_64-windows-msvc/`:
1.  **qairt-converter:** To move from `.tflite` to an intermediate QNN representation.
2.  **qnn-genai-transformer-composer:** The critical tool for sharding the model and creating the Genie-compatible binaries.
3.  **JSON Pipeline Config:** A new `genie_config.json` must define the `kv-share` and `mc_spill_fill` (multi-core optimization) settings.

### 4.2 The Shield (Kotlin Application)
The transition from `llama.cpp` to Genie requires a shift in how the Android app handles inference:

1.  **JNI Bridge:** A small C++ wrapper linking against `libGenie.so` and `libQnnHtp.so`.
2.  **Genie Service:** A new Kotlin service implementing the `LlamaService` interface but delegating to the `GeniePipeline_execute` C++ API.
3.  **Library Placement:** 
    *   **Source:** `qairt/lib/aarch64-android/`
    *   **Destination:** `DaexAndroid/app/src/main/jniLibs/arm64-v8a/`

---

## 5. Technical Recommendations for SM8750 (V79)
*   **Precision Strategy:** Use **INT16** for the RG-LRU recurrent states to maintain accuracy over long sequences, while using **INT4** for static weights to maximize NPU throughput.
*   **DLBC Activation:** Enable Deep Learning Bandwidth Compression in the Genie JSON to reduce the bus power consumed during token generation.
*   **Graph Switching:** Utilize Genie's ability to switch between "Prefill" and "Decode" graphs seamlessly on the HTP.

---

## 6. Project Roadmap
1.  **Shard Gemma 4:** Use the Genie composer to create the context binaries.
2.  **Draft Genie JSON:** Define the pipeline for the SM8750 V79 architecture.
3.  **Integrate Native Libs:** Move `libGenie.so` into the Android project.
4.  **Rewrite Native Bridge:** Replace `LlamaAndroid` calls with `GeniePipeline` calls.

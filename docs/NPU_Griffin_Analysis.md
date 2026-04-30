# NPU Optimization Research: Griffin Architecture and SM8750 (V79) HTP

**Project:** DAEX  
**Date:** April 27, 2026  
**Subject:** Technical Analysis of Hybrid LLM Inference on Snapdragon 8 Elite (SM8750)

---

## 1. Executive Summary
Project DAEX is transitioning from a standard Transformer baseline (GGUF) to a specialized, hardware-accelerated NPU pipeline. The target model architecture—**Griffin** (utilized in Gemma 2/4)—and the **Qwen 3.5** hybrid lineup represent a paradigm shift from quadratic self-attention to linear recurrence. While theoretically NPU-friendly, these architectures trigger specific failures in the Qualcomm AI Stack (QNN) during AOT (Ahead-of-Time) compilation, colloquially known as the "Silicon Lock" failure.

## 2. Architecture Deep Dive: Griffin & Qwen 3.5

### 2.1 The Griffin Hybrid (Google DeepMind)
Griffin replaces the traditional "All-to-All" attention with an interleaved approach:
*   **RG-LRU (Real-Gated Linear Recurrent Unit):** A diagonal recurrence that replaces the $O(N^2)$ attention mechanism with $O(N)$ complexity.
*   **Sliding Window Attention:** Bounds the KV cache to a fixed size (e.g., 1024-2048 tokens).

**NPU Impact:**
*   **KV Cache Stability:** Unlike Llama models where the KV cache grows indefinitely, Griffin's cache is static. This allows us to pin the cache to the HTP's **VTCM (Vector Tightly Coupled Memory)**, eliminating DRAM bottlenecks.
*   **Recurrence Bottleneck:** The RG-LRU requires a "Parallel Scan" or "Associative Scan" to be efficient. On the SM8750, this must be mapped to HVX (Hexagon Vector eXtensions).

### 2.2 Qwen 3.5 (The "Griffin-like" Challenger)
Qwen 3.5 utilizes **Gated DeltaNet** combined with **Sparse Mixture-of-Experts (MoE)**.
*   **Gated DeltaNet:** Functions as a linear attention mechanism with a fixed-size hidden state.
*   **Sparse MoE:** Only activates a subset of parameters per token (e.g., 17B out of 397B).

**NPU Impact:**
*   **Memory Bandwidth:** Qwen 3.5 is significantly less bandwidth-intensive than Qwen 2.5 (standard Transformer).
*   **Conditional Execution:** The MoE routing requires the NPU to handle dynamic branching or "predicated execution," which is a high-performance feature of the Hexagon V79.

---

## 3. Hardware Analysis: Snapdragon 8 Elite (SM8750)

### 3.1 Hexagon V79 Architecture
The SM8750 features the Gen 5 AI Engine (V79). Key upgrades relevant to DAEX:
*   **Expanded VTCM:** Estimated at 8MB-16MB. This is the "Bare-Metal" scratchpad where our kernels must live.
*   **DLBC (Deep Learning Bandwidth Compression):** A hardware-level compression for weights and activations.
*   **Transformer Accelerators:** Native support for standard MHA (Multi-Head Attention), but *not* natively for Griffin's RG-LRU recurrence.

### 3.2 The "Silicon Lock" Root Cause
The failure during `npu_stamp.py` (AOT compilation) is likely due to:
1.  **Unsupported Recurrent Primitives:** QNN's TFLite delegate often fails to "lower" RNN-style gates (RG-LRU) into HTP native instructions, causing a fallback to the CPU.
2.  **Monolithic Graph Constraints:** Trying to compile a 4B parameter model into a single `.bin` file exceeds the V79's memory-map budget.
3.  **Static Shape Rigidity:** The QNN HTP backend requires exact input/output shapes. Griffin’s sliding window attention creates internal "circular buffers" that the standard TFLite-to-QNN converter cannot resolve without manual tiling.

---

## 4. Strategic Recommendations

### 4.1 Transition to Qualcomm Genie SDK
The standard QNN TFLite Delegate is too generic for hybrid models. We should utilize the **Genie (GenAI) SDK**:
*   **Genie Pipeline:** Specifically handles `kv-share` and `mc_spill_fill` (multi-core spill/fill) logic.
*   **Sharded Binary Support:** Allows us to split the model into multiple context binaries (e.g., `embedder.bin`, `layers_1_10.bin`, etc.), bypassing the Silicon Lock memory limits.

### 4.2 Custom HTP Op Packages
To achieve the "Bare-Metal" goal, we must implement the RG-LRU recurrence as a **Custom Op Package**:
*   **Language:** C++ using Hexagon SDK.
*   **Optimization:** Implement the parallel scan using HVX intrinsics (`vmem` loads/stores).
*   **Tiling:** Manually manage VTCM slices to ensure the recurrence state never leaves the DSP.

### 4.3 "The Forge" V3 Roadmap
1.  **Architecture Check:** Use `qnn-architecture-checker` to identify exactly which Griffin ops are falling back.
2.  **Sharded Export:** Modify the Python export script to produce a "Genie-ready" multi-binary output.
3.  **Precision Calibration:** Use **INT16** for the recurrent states to prevent the "accuracy drift" common in low-precision RNNs, while keeping weights in **INT4**.

---

## 5. Next Steps
1.  **Verify SM8750 Op Support:** Run diagnostic tools on the current `model_quantized.tflite`.
2.  **Draft Genie Config:** Convert the single-JSON model config into a multi-binary Genie layout.
3.  **Implement Recurrence Kernel:** Begin C++ implementation of the RG-LRU for the HTP V79 backend.

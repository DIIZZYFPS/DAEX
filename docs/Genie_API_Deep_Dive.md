# Qualcomm Genie SDK: Deep Dive Analysis
**Project:** DAEX (Daedalus Execution Engine)  
**Date:** April 27, 2026  
**Subject:** File-by-File Breakdown of the Genie C API (`include/Genie/`)

This document provides a comprehensive, file-by-file analysis of the Qualcomm GenAI Inference Extensions (Genie) SDK. It serves as the technical blueprint for migrating DAEX to a "Bare-Metal" NPU runtime.

---

## 1. Core Definitions (`GenieCommon.h`)
This file establishes the foundational macros, data types, and status codes used across the entire SDK.
*   **Version:** Defines `GENIE_API_VERSION` (Currently `1.17.0`).
*   **Status Codes:** Uses a standard `Genie_Status_t` (int32). Errors are negative (e.g., `GENIE_STATUS_ERROR_CONTEXT_EXCEEDED`), while successes are 0.
*   **Performance Policies (`Genie_PerformancePolicy_t`):** Provides a granular enum to control NPU power/clock states. Options range from `GENIE_PERFORMANCE_BURST` (maximum power/clocks for immediate token generation) to `GENIE_PERFORMANCE_EXTREME_POWER_SAVER`.
*   **Memory Management:** Introduces `Genie_AllocCallback_t`. Genie rarely allocates large strings internally; instead, it uses client-provided callbacks to manage memory safely.

---

## 2. The Orchestrator (`GeniePipeline.h`)
The Pipeline is the highest level of abstraction in the SDK. It manages the entire lifecycle of a request, orchestrating the Tokenizer, Node (Model), and Sampler.
*   **Config Creation:** `GeniePipelineConfig_createFromJson` is the entry point. It takes the `genie_config.json` that defines the sharded model and KV-share settings.
*   **Execution:** `GeniePipeline_execute` takes a query and fires a callback (`GeniePipeline_TextOutput_Callback_t`) as tokens are generated.
*   **Signaling:** `GeniePipeline_signal` allows the client to asynchronously `PAUSE` or `ABORT` an active generation loop—critical for voice assistants like Icarus where a user might interrupt.

---

## 3. The Conversation Manager (`GenieDialog.h`)
While `GeniePipeline` handles raw execution, `GenieDialog` is specifically built for stateful, multi-turn LLM interactions.
*   **Sentence Codes:** `GenieDialog_SentenceCode_t` defines the state of the stream (`BEGIN`, `CONTINUE`, `END`, and `REWIND`). The `REWIND` code is critical; it tells the NPU to revert the KV cache to a previous point, saving massive compute overhead during complex reasoning loops.
*   **Context Management:** Exposes `GenieDialog_Param_t` (like `GENIE_DIALOG_PARAM_CONTEXT_OCCUPANCY`) so the application can monitor how full the NPU's VTCM is getting.
*   **LoRA Adapters:** Contains `GenieDialog_applyLora` and `GenieDialog_setLoraStrength`. This allows DAEX to load a base model (e.g., Gemma 4) and dynamically apply personality weights (like a "Sardonic Icarus LoRA") directly on the NPU at runtime.

---

## 4. Hardware Interaction (`GenieEngine.h` & `GenieNode.h`)
These files manage the actual mapping of the model to the Qualcomm Hardware (QNN).
*   **GenieEngine:** Represents the physical or logical execution unit (e.g., the HTP backend). It allows for binding "Target" (Main) and "Draft" (Speculative Decoding) models.
*   **GenieNode:** Represents a specific algorithmic block. The `GenieNode_IOName_t` enum reveals explicit support for specialized models:
    *   Text Generation (`GENIE_NODE_TEXT_GENERATOR_TEXT_INPUT`)
    *   Image Encoders with Window Attention (`GENIE_NODE_IMAGE_ENCODER_IMAGE_WINDOW_ATTN_MASK`) - *This confirms underlying SDK support for sliding window attention.*

---

## 5. Model Loading (`GenieDlc.h`)
Handles the loading of Qualcomm's proprietary Deep Learning Container (.dlc) format or QNN Context Binaries (.bin).
*   **Initialization:** `GenieDlc_create` takes the sharded model definitions and prepares them for the `GenieEngine`.
*   **Use Cases:** `GenieDlc_getUseCases` interrogates the binary to ensure it matches the expected hardware targets (e.g., confirming it's an SM8750 HTP binary).

---

## 6. Text Processing (`GenieTokenizer.h` & `GenieSampler.h`)
Genie offloads tokenization and sampling from the host application (Python/Kotlin) to highly optimized C++ routines.
*   **GenieTokenizer:** Provides standard `encode` and `decode` functions.
*   **GenieSampler:** Handles the probabilistic generation of the next token. It takes raw logits from the `GenieEngine` and returns a token based on configurations (Temperature, Top-K, Top-P) set via `GenieSamplerConfig_setParam`.
*   **Custom Sampling:** `GenieSampler_registerUserDataCallback` allows DAEX to inject custom sampling logic (e.g., penalizing repetition or forcing specific JSON outputs).

---

## 7. Diagnostics (`GenieProfile.h`, `GenieLog.h`, `GenieAccuracy.h`)
Essential tools for debugging the "Silicon Lock" and ensuring the model runs efficiently on the SM8750.
*   **GenieProfile:** Can be bound to a Pipeline or Dialog. It tracks precise hardware metrics (execution time, VTCM cache hits, token generation speed).
*   **GenieLog:** Provides a thread-safe callback (`GenieLog_Callback_t`) to pipe Qualcomm's internal C++ logging directly to the Kotlin Android Logcat or Python stdout.
*   **GenieAccuracy:** A specialized module for calculating perplexity or evaluating model drift, especially useful when validating INT4 quantizations.

---

## 8. Specific Implementations (`GenieEmbedding.h`)
For Retrieval-Augmented Generation (RAG) or text-to-vector tasks.
*   **Dedicated API:** `GenieEmbedding_generate` is separate from the `GenieDialog`, allowing DAEX to run a smaller embedding model (like `bge-small`) on the NPU concurrently with the main LLM.

---

## Architectural Synthesis for DAEX Integration

The Genie SDK completely shifts the development paradigm. Instead of treating the NPU as a generic math accelerator (like standard TFLite), Genie acts as a **"GenAI Operating System"**.

1.  **State is Native:** The KV cache is managed entirely by `GenieDialog` in C++. The Android App (The Shield) only needs to send text strings.
2.  **Speculative Decoding is Built-In:** The SDK expects you to use `GenieEngine_bindEngine` with a "draft" model to accelerate inference.
3.  **Hardware Abstraction:** The `Genie_PerformancePolicy_t` abstracts away complex Linux kernel/Hexagon DSP frequency voting, allowing simple commands like `GENIE_PERFORMANCE_BURST` to handle power management.

**Next Step for DAEX:** We must map the `Icarus` Python backend to these C API calls using `ctypes` or `pybind11`, bypassing the slower Python frameworks completely.

PROJECT AEGIS: Edge Inference Engine

Document Status: V1 Specification

Lead Engineer: DIIZZY

Core Directive: Zero-Trust, Zero-Latency, Bare-Metal Mobile Compute.

1. Executive Summary

The mobile AI ecosystem is currently suffocating under heavy OS abstractions (like Google's AICore) and cloud-tethered API wrappers. Project Aegis is a localized, hardware-accelerated inference engine that bypasses the cloud and Android's OS-level ML bloat entirely.

By leveraging Ahead-Of-Time (AOT) compiled PyTorch graphs and the Qualcomm Neural Network (QNN) Partitioner, Aegis pipes heavily quantized LLMs directly into the Snapdragon Hexagon NPU. The result is deterministic, offline, high-speed inference with zero data exfiltration.

2. The Wedge (The Problem We Are Solving)

Power users do not trust cloud AI with localized personal data (finance, health logs, proprietary code), but running models like llama.cpp on mobile CPUs drains the battery and throttles the device. Aegis provides the missing infrastructure: a React Native translation layer that allows developers to run sub-3B parameter models efficiently on mobile silicon without writing custom C++ JNI drivers.

3. System Architecture & Tech Stack

Aegis is split into two distinct operational theaters: The Forge (Desktop Pipeline) and The Shield (Mobile Runtime).

3.1 The Forge (Desktop Pipeline - RTX 4080)

Role: Model ingestion, aggressive quantization, and static graph compilation.

OS: Ubuntu 22.04 LTS (Native or WSL2 - Windows native is unsupported for QNN builds).

Framework: PyTorch 2.x, ExecuTorch (Built from source).

Quantization: torchao (Targeting PT2E INT4/INT8 depending on Hexagon support blocks).

Delegation: Qualcomm AI Engine Direct SDK (QNN SDK v2.37.0).

Output: Serialized .pte binaries mapped explicitly to Gen 5 silicon.

3.2 The Shield (Mobile Runtime - Samsung Galaxy S26 Ultra)

Role: User interface, tensor ingestion, and JSI execution.

Framework: React Native 0.74+ (TypeScript).

Bridge: react-native-executorch (Software Mansion).

UI Library: Custom minimal components based on MobileExecution.tsx.

Hardware Target: Snapdragon 8 Elite Gen 5 (specifically targeting the Hexagon NPU).

4. MVP Scope (Phase 1)

We are not building a generalized orchestrator like AgentBay yet. Phase 1 is strictly about proving the silicon bridge works.

Feature 1: The AOT Pipeline: A Python script on the 4080 that successfully ingests a Hugging Face model (e.g., Llama 3.2 1B or SmolLM), applies the QnnPartitioner, and exports a valid aegis_s26_ultra.pte file.

Feature 2: Native Execution UI: A minimal, dark-themed React Native chat interface (MobileExecution.tsx) that loads the .pte file from the local /data/local/tmp/ directory via ADB.

Feature 3: Hardware Verification Logging: The app must explicitly verify whether the inference was routed to the Hexagon NPU or if it suffered a fallback to the XNNPACK (CPU) delegate.

Feature 4: Streaming Tensors: (Stretch for MVP) Implement token streaming in the UI rather than waiting for the entire block to generate, proving latency viability.

5. Hard Technical Requirements

Strict Offline Enforcement: The React Native app must not contain any network fetch requests for model weights in Phase 1. All weights are side-loaded via ADB.

JSI Memory Safety: The Software Mansion bridge uses JSI to pass strings to the C++ runtime. The UI must cleanly handle unmounting and runtime destruction to prevent memory leaks in the NDK layer.

Target Device Lockdown: V1 is explicitly locked to the S26 Ultra / Snapdragon Gen 5 architecture. Attempting to generalize for Google Tensor edge TPUs will fracture the compilation pipeline and stall momentum.

6. Soft Requirements & UX

Transparency: The UI must visually indicate hardware status (e.g., "🟢 Hexagon NPU Ready" vs "🟠 CPU Fallback").

Deterministic Failure: If the QNN delegate throws a segmentation fault because of an unsupported matrix operation, the app should catch the error gracefully in the UI rather than hard-crashing to the Android home screen.

The Aesthetic: Terminal-adjacent. Monospace fonts, high contrast (Cyan/Teal on true Black). It should look like an internal diagnostic tool, not a consumer SaaS app.

7. Known Bottlenecks & Strategic Risks

QNN SDK Hostility: The Qualcomm SDK is notoriously brutal to configure on Linux. Environment variables ($QNN_SDK_ROOT) and CMake flags will likely fail on the first 10 compilation attempts.

Quantization Degradation: We need to crush models to INT4 to fit in mobile RAM bandwidth, but the Gen 5 NPU might reject specific asymmetric quantization schemes. If we fall back to the CPU, we lose the wedge.

Context Window Limits: Mobile RAM cannot handle 128K context windows. We must artificially truncate the input KV cache in the Python export script to prevent Out-Of-Memory (OOM) crashes on the device.
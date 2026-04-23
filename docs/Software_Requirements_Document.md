# Software Requirements Document (SRD)
**Project:** Aegis (Edge Inference Engine)
**Version:** 1.0.0
**Target Model:** Gemma 4 E4B (INT4)

## 1. Introduction
Project Aegis is a localized, hardware-accelerated LLM pipeline targeting the Snapdragon 8 Elite Gen 5 Hexagon NPU on the Samsung Galaxy S26 Ultra. 
The goal is to provide zero-latency, offline, bare-metal AI execution using ExecuTorch.

## 2. Functional Requirements
- **FR1:** The system MUST execute the Gemma 4 E4B model entirely offline.
- **FR2:** The system MUST NOT rely on cloud inference or exfiltrate any data over the network.
- **FR3:** The React Native Mobile UI MUST load pre-compiled `.pte` ExecuTorch binaries from local storage.
- **FR4:** The UI MUST display dynamic feedback indicating whether inference is running on the Hexagon NPU or falling back to the XNNPACK CPU.
- **FR5:** The system MUST gracefully capture out-of-memory (OOM) or C++ segmentation faults and present them cleanly in the UI instead of a hard crash.

## 3. Non-Functional Requirements
- **NFR1 (Performance):** The memory footprint of the active model and KV cache MUST NOT exceed 7.5 GB to prevent Android Out-Of-Memory (OOM) termination on a 12 GB RAM device.
- **NFR2 (Latency):** Time-to-first-token (TTFT) should be under 500ms utilizing the NPU acceleration.
- **NFR3 (Aesthetic):** The UI must utilize a dark-mode, terminal-adjacent aesthetic specifically designed for power-user diagnostics (high contrast cyan/teal on true black).
- **NFR4 (Portability):** The compilation pipeline (The Forge) MUST operate on Ubuntu/WSL2, independent of the Android development host.

## 4. Hardware Requirements
- **Target Device:** Samsung Galaxy S26 Ultra.
- **Target SoC:** Snapdragon 8 Elite Gen 5.
- **RAM Constraints:** Minimum 12 GB LPDDR5x.

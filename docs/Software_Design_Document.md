# Software Design Document (SDD)
**Project:** Aegis (Edge Inference Engine)
**Target Model:** Gemma 4 E4B (INT4)

## 1. System Architecture Overview
Aegis is divided into two distinct engineering zones:
1. **The Forge:** An AOT (Ahead-Of-Time) Python compilation pipeline utilizing PyTorch and ExecuTorch.
2. **The Shield:** A React Native Android client bridging ExecuTorch into the QNN Hexagon NPU.

## 2. Data Flow
1. **Ingestion:** A prompt is captured in `MobileExecution.tsx`.
2. **JSI Bridge:** React Native passes the string synchronously via JSI to the `react-native-executorch` C++ bindings.
3. **Execution:** The ExecuTorch runtime processes the prompt through the loaded `aegis_gemma4.pte` computational graph.
4. **Delegation:** The QNN Backend handles tensor matrix multiplication directly on the Hexagon NPU.
5. **Yield:** Generated tokens are yielded back across the JSI bridge and streamed securely into the React Native state.

## 3. Component Design
### 3.1 The Forge (export.py)
A Python script responsible for applying `torchao` INT4 quantization to Gemma 4 E4B, partitioning the graph using `QnnPartitioner`, and emitting the ExecuTorch flatbuffer `.pte`.

### 3.2 The Shield (Android NDK Layer)
To enable the Hexagon NPU, custom logic must be injected into the `react-native-executorch` Android bindings:
- Modify `CMakeLists.txt` to properly link `libQnnSystem.so` and `libQnnHtp.so`.
- Instantiate the QNN backend delegate inside the JSI `loadModel` C++ scope instead of defaulting to XNNPACK.

### 3.3 The Shield (React Native UI)
A React Native (0.74+ TypeScript) application maintaining terminal UI components. It will handle the orchestration of the chat array and manage standard LLM generation states (idle, generating, error).

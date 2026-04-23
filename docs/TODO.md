# Project Aegis TODO List

## Phase 1: Mobile UI Architecture (COMPLETED)
We are building the mobile UI first to ensure the rendering loop and JSI bridge are ready before tackling compilation hurdles.
- [x] Initialize React Native (0.85+ TypeScript) application.
- [x] Create base UI: Terminal-adjacent aesthetic (Monospace, Cyan on Black).
- [x] Build `MobileExecution.tsx` with real-time response streams.
- [x] Implement Markdown rendering for structured AI output.
- [x] Add real-time performance metrics (TPS/Generation status).

## Phase 1.5: GGUF Validation (COMPLETED)
Used as a baseline for performance testing on device silicon before moving to ExecuTorch.
- [x] Integrate `llama.rn` for local GGUF inference.
- [x] Implement `modelManager` for in-app GGUF downloads (Gemma 4 E4B Q4_K_M).
- [x] Enable Vulkan GPU offload support for mobile hardware.
- [x] Optimize chat interface for high-frequency token updates.

## Phase 2: The Forge (Model Compilation)
- [/] Provision WSL2/Ubuntu environment.
- [ ] Install ExecuTorch source and PyTorch Nightly.
- [ ] Download and configure Qualcomm AI Engine Direct SDK (QNN SDK).
- [ ] Write Python export script to ingest **Gemma 4 E4B**.
- [ ] Apply INT4 PT2E quantization (`torchao`).
- [ ] Generate the serialized `aegis_gemma4.pte` file using `QnnPartitioner`.

## Phase 3: Hardware Integration & Polish
- [ ] Implement `react-native-executorch` into the project.
- [ ] Write the Android C++ / JNI wrapper to explicitly register the QNN Delegate for Hexagon.
- [ ] Side-load the `.pte` file onto the S26 Ultra via `adb`.
- [ ] Connect the `react-native-executorch` backend to the local React UI.
- [ ] Validate NPU execution via Android Logcat (Ensure no CPU fallback to XNNPACK).
- [ ] Handle graceful segmentation fault or memory exits.

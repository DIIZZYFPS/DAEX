# Project Aegis TODO List

## Phase 1: Mobile UI Architecture (Priority)
We are building the mobile UI first to ensure the rendering loop and JSI bridge are ready before tackling compilation hurdles.
- [ ] Initialize React Native (0.74+ TypeScript) application.
- [ ] Create base UI: Terminal-adjacent aesthetic (Monospace, Cyan on Black).
- [ ] Build `MobileExecution.tsx` with dummy response streams.
- [ ] Implement `react-native-executorch` into the project.
- [ ] Write the Android C++ / JNI wrapper to explicitly register the QNN Delegate for Hexagon.

## Phase 2: The Forge (Model Compilation)
- [ ] Provision WSL2/Ubuntu environment.
- [ ] Install ExecuTorch source and PyTorch Nightly.
- [ ] Download and configure Qualcomm AI Engine Direct SDK (QNN SDK).
- [ ] Write Python export script to ingest **Gemma 4 E4B**.
- [ ] Apply INT4 PT2E quantization (`torchao`).
- [ ] Generate the serialized `aegis_gemma4.pte` file using `QnnPartitioner`.

## Phase 3: Hardware Integration & Polish
- [ ] Side-load the `.pte` file onto the S26 Ultra via `adb`.
- [ ] Connect the `react-native-executorch` backend to the local React UI.
- [ ] Validate NPU execution via Android Logcat (Ensure no CPU fallback to XNNPACK).
- [ ] Handle graceful segmentation fault or memory exits.

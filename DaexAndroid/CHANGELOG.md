# Changelog

## [0.3.5](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.3.4...DaexAndroid-v0.3.5) (2026-06-25)


### Bug Fixes

* upgraded fts4 to fts5 ([#26](https://github.com/DIIZZYFPS/DAEX/issues/26)) ([6d85c11](https://github.com/DIIZZYFPS/DAEX/commit/6d85c1141924527bf988d1b391b34026476a4d9e))

## [0.3.4](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.3.3...DaexAndroid-v0.3.4) (2026-06-17)

### Embedding Engine (new)
- Replaced stub `DaexEmbedder` with production MediaPipe LiteRT TextEmbedder (384-dim BERT).
- Added `com.google.mediapipe:tasks-text` dependency for on-device model loading.

### Hybrid Retrieval (new)
- Introduced `DaexFtsDatabaseHelper` with FTS4/BM25 full-text search alongside ObjectBox vector store.
- `DaexRag` now performs hybrid retrieval combining semantic similarity with BM25 keyword scoring.

### Document Library UI (new)
- Added `DocumentLibraryModal.kt` bottom-sheet for browsing, attaching, and deleting documents per conversation.
- Extended `ConversationEntity` with `attachedFileNames`; persisted in ObjectBox schema.

### Inference Improvements
- Speculative decoding toggle guard -- prevents unsafe state changes while engine is busy.
- Improved token-per-second estimation logic in `DaexService`.
- Fixed context passing: `MainActivity` now correctly forwards application context to `DaexRagImpl`.

### Bug Fixes
- Updated HNSW index dimensions from 768 to 384 across all entities to match new embedder output.

---

## [0.3.3](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.3.2...DaexAndroid-v0.3.3) (2026-06-11)

### Stability Fixes
- Added `Mutex` serialization to `saveMessage()` in `DaexMemory` -- prevents race conditions and data corruption during concurrent database writes.
- Refactored streaming parser to support multiple `think` / `<|channel|>` blocks within a single model response.
- Locked `MainActivity` to portrait orientation -- eliminates UI/layout crashes on rotation and split-screen.

---

## [0.3.2](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.3.1...DaexAndroid-v0.3.2) (2026-06-11)

### Hardware Compatibility
- Fixed Google Tensor G5 TPU model loading and first-prompt inference hangs on Pixel 10 Pro Fold.
- Updated LiteRT dispatch library (`libLiteRtDispatch_GoogleTensor.so`) and NDK packaging flags (`useLegacyPackaging`, `extractNativeLibs`).
- Restored dynamic preference-based reasoning toggle. *(contributed by @sskarz)*

---

## [0.3.1](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.3.0...DaexAndroid-v0.3.1) (2026-06-11)

### UI Overhaul
- Replaced modal-based `SettingsModal` with full-screen `SettingsScreen` (tuning / theme / system tabs).
- Added voice input UX, haptic feedback toggle, and AURA reactive background effects.
- Introduced `SpeechManager` for speech recognition with amplitude and state callbacks.
- Converted hardcoded suggested prompts to configurable, dynamically generated parameters.
- Added "thinking" animation (breathing dot) to message rendering.
- Max-tokens slider (128-4096) with live value formatting.

### Backend
- Added `maxTokens` parameter handling to `DaexService`.
- Model delete support in `ModelSelectorModal`.
- Added `VIBRATE` and `RECORD_AUDIO` permissions; speech recognition query in manifest.

---

## [0.3.0](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.2.1...DaexAndroid-v0.3.0) (2026-06-09)

### Agent Runtime Overhaul
- Token-pressure-based context compaction -- manages long conversations by automatically summarizing and compressing history.
- Debounced global memory curation with validation rules (checks for required headers before overwriting).
- New message states: `isPinned`, `isCompacted`; compaction summaries persisted in ObjectBox.

### Modular Skills System
- `DaexSkillManager` introduced -- catalogs and loads skills from assets or internal storage (`SKILL.md` format).
- Asynchronous permission/status channel integrated into chat UI with in-chat approval flow.
- New action models: `PermissionRequest`, `ToolProgress`.

### Contextual Timestamps
- Relative time formatting (`just now`, `5m ago`, `2h ago`) injected into conversation history for LiteRT context.
- System log messages rendered inline in chat stream.

---

## [0.2.1](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.2.0...DaexAndroid-v0.2.1) (2026-06-04)

### Build Fixes
- Removed merge conflict artifacts and stray build files from `main`.

---

## [0.2.0](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.3...DaexAndroid-v0.2.0) (2026-06-04)

### Onboarding Wizard Overhaul
- Complete 6-slide setup pager (`LandingScreen`) guiding configuration flow: welcome, philosophy, diagnostics, Icarus showcase, engine select, tutorial.
- Dynamic chipset scanning recommends CPU/GPU/NPU models based on device hardware.

### Developer Settings & Tuning Panel
- Granular inference parameter sliders: temperature, top-k, top-p.
- Custom system prompt overrides.
- Hardware diagnostics view: SoC, board, total memory, Vulkan/NPU support status.

### Offline Knowledge Base (RAG)
- Document ingestion for PDFs (iText) and plain text with vector indexing.
- `KNOWLEDGE BASE` manager in Settings listing all offline documents.
- Dynamic document deletion -- purges ingested chunks on file removal.

### Sandboxed Tool Calling
- On-device utilities exposed to the generative engine: battery state, disk space, time, app launching.
- Execution framework runs tools under sandbox controls.

### UI & Aesthetics Polish
- Re-anchored input overlay containers to scale dynamically with glass backing layouts.
- Fixed attachment "+" button color contrast visibility.
- Removed emojis from chat windows, sidebars, and onboarding -- enforced dark cybernetic aesthetic.
- Renamed Llama service references to `DaexService`; cleaned deprecated workaround code.

---

## [0.1.3](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.2...DaexAndroid-v0.1.3) (2026-06-03)

### Build & Runtime
- Added NPU libraries for Tensor G5 build support.
- Removed redundant LiteRT native library workarounds -- SDK v0.12.0 handles loading automatically. *(contributed by @sskarz)*

---

## [0.1.2](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.1...DaexAndroid-v0.1.2) (2026-06-02)

### Developer Tools
- Added Log Share feature for debugging and user support -- exports conversation logs and device diagnostics.

---

## [0.1.1](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.0...DaexAndroid-v0.1.1) (2026-06-02)

### Infrastructure
- CI/CD pipeline overhaul: upgraded `release-please-action` to v5, configured release automation for `DaexAndroid` module.
- Added Google TPU-compatible Gemma model support.
- LiteRT engine overhaul: cleaned up native library loading, improved model initialization pipeline.

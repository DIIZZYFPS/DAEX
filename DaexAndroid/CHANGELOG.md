# Changelog

## [0.3.4](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.3.3...DaexAndroid-v0.3.4) (2026-06-17)

### Engine Migration

- **Replaced llama.cpp inference with LiteRT-LM** — migrated to Google's native Android LLM runtime (`Engine`/`Conversation` API) for tighter integration with the LiteRT ecosystem and better hardware backend support
- **Speculative decoding enabled** — experimental MTP drafting for ~17% token speedup on Gemma 4 E2B/E4B models
- **Multi-backend selection** — CPU, GPU, and NPU backends with automatic dispatch library detection; graceful fallback when NPU libraries are absent

### Architecture Cleanup

- Removed legacy `DaexService`, `DeviceTools`, `SpeechManager`, and `SkillManager` (6,800+ lines consolidated)
- Replaced separate Settings screen with consolidated `SettingsModal`
- Streamlined RAG file deletion API to use document IDs instead of filenames
- Rewrote core memory persistence layer
- Removed asset-based changelog and QR code skill (unused)

### UI Simplification

- Reduced ExecutionScreen by ~900 lines through structural cleanup
- Simplified LandingScreen and GalleryScreen layouts
- Retained all existing functionality with cleaner code paths

---

## [0.3.3](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.3.2...DaexAndroid-v0.3.3) (2026-06-11)

### Bug Fixes

- **Fixed multi-think block rendering** — extraction now joins multiple thought blocks with blank lines instead of concatenating them, producing coherent reasoning output in the chat
- **Thread-safe message persistence** — added `Mutex` (`saveMutex`) to `DaexMemory` to prevent concurrent write races during message saves
- **Removed system logs from model context** — debug/system logs no longer leak into inference prompts
- **Locked app to portrait mode** — set `screenOrientation="portrait"` in `AndroidManifest` to prevent layout breaks

---

## [0.3.2](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.3.1...DaexAndroid-v0.3.2) (2026-06-11)

### Bug Fixes

- **Fixed Google Tensor G5 TPU hangs** — disabled speculative decoding on Tensor G5 where it caused model loading and first-prompt inference to hang; simplified NPU dispatch library handling with `prepareDispatchLibrary()` helper
- **Restored dynamic preference-based model autoloading** — reverted reasoning default to `true` and brought back user-preference-driven model selection
- **Updated Google Tensor dispatch library** — bumped `libLiteRtDispatch_GoogleTensor.so` for compatibility with Tensor G5 TPU

---

## [0.3.1](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.3.0...DaexAndroid-v0.3.1) (2026-06-10)

### Features

- **Haptics & AURA** — added haptic feedback on interactions and AURA visual effects (animated glow/border) to the chat interface
- **Light mode support** — fixed theme colors for light mode users; updated animation states and header formatting
- **Audio features** — integrated voice/TTS capabilities ("audio sneak") with `VoiceState` tracking and amplitude visualization
- **Personalized suggestions** — context-aware suggested prompts based on conversation state and downloaded models

### Bug Fixes

- **Engine safeguard / message edits** — added safeguards to prevent engine crashes from malformed messages; fixed message editing flow
- **ASR session cleanup** — properly cleaned up Automatic Speech Recognition sessions to prevent resource leaks

---

## [0.3.0](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.2.1...DaexAndroid-v0.3.0) (2026-06-09)

### Features

- **Improved agent loop** — upgraded the tool calling pipeline with UI-visible tool blockers that show pending tool calls and their status to the user
- **Context-aware memory compaction** — replaced fixed-size compaction with dynamic scaling triggered by context window usage; keeps the model's context relevant to the conversation length
- **Skill loading system** — added `load_skill` and `list_skills` tools for dynamically loading domain-specific instructions from Markdown files, optimizing the system prompt
- **System prompt safeguards** — added validation to prevent prompt injection and context pollution
- **Contextual timestamps** — messages now display time-relative timestamps (e.g., "2m ago") that update in real-time

---

## [0.2.1](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.2.0...DaexAndroid-v0.2.1) (2026-06-04)

### Bug Fixes

- Removed build artifacts accidentally included in merge conflict resolution

---

## [0.2.0](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.3...DaexAndroid-v0.2.0) (2026-06-04)

### Features

- **Onboarding Wizard Overhaul** — complete 6-slide setup pager (`LandingScreen`) guiding configuration flow with visual preview slides and dynamic chipset scanning diagnostics to recommend CPU/GPU/NPU models; swiping gated until required options are confirmed
- **Developer Settings & Tuning Parameters** — granular inference parameter sliders (temperature, top-k, top-p), custom system prompt overrides, and hardware diagnostics view showing model specs, SoC, board, total memory, and Vulkan/NPU support status
- **Offline Knowledge Base Manager (RAG)** — document ingestion (PDFs via iText and text files) with vector indexing; `KNOWLEDGE BASE` list in Settings modal for managing offline documents; dynamic deletion of ingested chunk segments
- **Sandboxed Tool Calling** — on-device information utilities exposed directly to the generative engine: battery state, disk space, time, and app launching under sandbox controls
- **UI & Aesthetics Polish** — re-anchored input overlay containers to scale dynamically with glass backing layouts; fixed attachment "+" button color contrast; removed emojis from chat windows, sidebars, and onboarding screens for a dark cybernetic aesthetic; renamed Llama service references to `DaexService`

---

## [0.1.3](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.2...DaexAndroid-v0.1.3) (2026-06-03)

### Bug Fixes

- Added NPU libraries for build (`libLiteRtDispatch_*.so`)

---

## [0.1.2](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.1...DaexAndroid-v0.1.2) (2026-06-02)

### Bug Fixes

- Added Log Share functionality

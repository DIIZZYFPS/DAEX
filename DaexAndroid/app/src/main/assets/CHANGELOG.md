# Changelog

## [0.2.0](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.3...DaexAndroid-v0.2.0) (2026-06-04)

### Features

* **Onboarding Wizard Overhaul**:
  - Implemented a complete 6-slide setup pager (`LandingScreen`) to guide configuration flow.
  - Added visual preview slides for the local assistant (Icarus engine).
  - Integrated dynamic chipset scanning diagnostics to recommend CPU/GPU/NPU models.
  - Swiping is gated until required options are confirmed.

* **Developer settings and Tuning Parameters**:
  - Expanded Settings modal with granular developer options.
  - Added inference parameter configuration sliders (Inference Temperature, Top-K, Top-P).
  - Added support for custom system prompt overrides.
  - Integrated a hardware diagnostics view rendering model specs, SoC, board, total memory, and Vulkan/NPU support status.

* **Offline Knowledge Base Manager (RAG)**:
  - Added document ingestion (PDFs via iText and text files) vector indexing.
  - Added a `KNOWLEDGE BASE` manager list to the Settings modal displaying all offline documents.
  - Integrated dynamic document deletion callbacks (`deleteFileByName`) to purge ingested chunk segments.

* **Sandboxed Tool Calling**:
  - Exposed on-device information utilities directly to the generative engine.
  - Created execution framework for retrieving battery state, disk space, time, and launch apps under sandbox controls.

* **UI & Aesthetics Polish**:
  - Re-anchored input overlay containers to scale dynamically with glass backing layouts.
  - Fixed attachment "+" button color contrast visibility.
  - Completely cleaned up emojis from chat windows, sidebars, and onboarding screens to enforce a dark cybernetic aesthetic.
  - Renamed Llama service references to `DaexService` and cleaned up deprecated workaround code.

---

## [0.1.3](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.2...DaexAndroid-v0.1.3) (2026-06-03)

### Bug Fixes

* added NPU libraries for build ([469dcd3](https://github.com/DIIZZYFPS/DAEX/commit/469dcd358fdee4258cb220ed259529bcc79b0564))

## [0.1.2](https://github.com/DIIZZYFPS/DAEX/compare/DaexAndroid-v0.1.1...DaexAndroid-v0.1.2) (2026-06-02)

### Bug Fixes

* Add Log Share ([074dfd5](https://github.com/DIIZZYFPS/DAEX/commit/074dfd5355cfd60695999c5af19977ecc50c5577))

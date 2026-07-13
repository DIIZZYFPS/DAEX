# DAEX Implementation Roadmap

This document outlines the strategic phases for evolving DAEX from a simple LLM wrapper into an autonomous on-device AI engine.

## Phase 0: Public Beta Hardening (2026-07-11 review)
**Goal:** Fix the reliability gaps that will cost first-impression users, before feature work resumes. Ranked by impact.

### P0 — must fix before beta
- [ ] **ObjectBox re-entry crash**: `BoxStore` is rebuilt in `MainActivity.onCreate` every time the activity is (re)created; reopening the app while the previous store is still open throws `DbException: Another BoxStore is still open`. Move to a process-lifetime singleton (`Application` subclass or lazily-initialized object built once per process). Confirmed reproducible via adb (2026-07-10 field logs). *(spawned as a separate task — see chip)*
- [x] **Model download resilience** (`ModelManager.kt`): no HTTP Range resume, no `.part`-then-rename, and the "≥90% of expected size" check accepts truncated/corrupt files. A first-run is a 2.6–3.7GB download — any interruption currently means starting over, and a corrupt-but-"valid-enough" file fails opaquely at engine load. Add resume support, temp-file-then-atomic-rename, and exact-size or checksum validation.
- [x] **Foreground service for downloads** (`dataSync` type minimum): screen-off or app-switch during the mandatory first-run download currently kills it. Voice-session foreground service (`microphone` type) is a stretch goal for the same reason but more forgivable at beta.
- [x] **Crash visibility**: no crash reporting anywhere (reasonable given the privacy-first positioning), but there's also no local capture — wire an uncaught-exception handler into the existing `LogShareHelper.kt` so the app can offer "share crash log" on next launch. Otherwise beta feedback is unactionable ("it crashed").
- [x] **Storage check before download**: `ModelManager.checkSpecSupport` explicitly ignores free storage (`supported = hasEnoughRAM`) — a 3.7GB download onto a nearly-full device fails mid-write. Gate the download button on `hasEnoughStorage` too.
- [x] **Release build hygiene**: `versionCode` is hardcoded to `1`; decide the Play internal-testing-track vs. sideload-APK distribution path before beta (the former gets staged rollout + crash/ANR vitals for free — covers part of the crash-visibility gap above). Sideload via GitHub Releases confirmed as the distribution path.

### P1 — should fix early in beta, not necessarily before
- [ ] Voice UI latency feedback: no visible state during the ~4-5s first-sentence TTS synthesis or long prefill — same latency reads as "broken" without a "thinking…"/"voicing…" indicator.
- [x] Per-token O(n²) re-parsing in the streaming callback (`DaexInferenceViewModel.kt`) — re-scans full accumulated text and copies the full message list on every token; fine for short voice replies, will jank on long text generations on mid-range hardware. Fixed by throttling the parse+publish to ~30fps (StringBuilder accumulation, existing parser untouched) with a guaranteed-correct final flush; also fixed a pre-existing bug where the live-voice end-of-stream TTS flush indexed into the raw (tag-included) text using an index tracked against the tag-stripped text.
- [x] Full conversation/context rebuild every turn (`DaexService.kt`) — re-prefills entire history each turn; a meaningful chunk of voice-loop latency. Confirmed via direct inspection of the `litertlm-android` library's compiled classes that `Conversation.sendMessage*` supports continuing an existing session without resupplying history. Implemented conversation reuse scoped to live voice turns (text chat has more per-turn-varying state — RAG context, relative timestamps — left on the always-recreate path for now), gated on conversation id + sampler/reasoning/tool settings all matching, with an automatic one-time fallback to a fresh conversation if reuse fails before any token arrives. Verified live: turns 2+ in a voice session correctly reuse the conversation (confirmed via logs) and stay contextually coherent.
- [ ] Onboarding length: six slides plus a mandatory multi-GB download before first real use. Consider a skippable tutorial and starting the download in the background during it.
- [ ] Denied `RECORD_AUDIO` silently no-ops the voice button — needs a visible explanation/retry path.
- [ ] Accessibility pass: the custom `BasicText`-everywhere design system likely doesn't respect font scaling or TalkBack. One pass before beta widens the audience.
- [ ] README/ROADMAP drift: README still describes Room (app actually uses ObjectBox + FTS5/BM25 hybrid retrieval); Phases 2 and 4 below are substantially implemented in code but shown unchecked. Keep these docs in sync going forward — they're a first impression for technical users browsing the repo.
- [ ] Zero automated tests in the repo. Not a beta blocker, but the most fragile code (WAV writer, VAD gate state machine, download validation) is exactly where a regression would be silent and costly. Even minimal unit coverage on those three would pay for itself.

### Post-beta arcs (not urgent, tracked for later)
- **Voice v2**: clause-level splitting for faster first-sentence audio, thermal/battery monitoring during long sessions (LLM + Kokoro concurrently will heat mid-range phones), and interruption/barge-in done via proper software echo cancellation using the TTS reference signal already available in `KokoroTtsService` (makes barge-in tractable instead of threshold-tuning against device-specific echo).
- **Agentic depth** (Phases 3-4 below): tool registry already exists; intent router and structured-output constraints are the missing pieces.
- **Distribution & licensing**: verify Gemma weight redistribution terms from the `litert-community` HuggingFace mirror hold up at beta scale; confirm ObjectBox's AGPL terms are compatible with any future commercial path.
- **Full background execution & default-assistant role**: promote the whole inference pipeline (not just downloads) to survive backgrounding — prompt the model, leave the app, and it keeps generating; response delivery via notification (progress while generating, then either the completed response or a "response ready" notification), rather than today's silent orphaning of an Activity-scoped `DaexInferenceViewModel`/coroutine when the Activity is destroyed. Paired ambition: register DAEX for Android's Assist/Voice Interaction role (`VoiceInteractionService`) so it can be set as the device's default assistant instead of only being a standalone app. Substantially bigger than the download service above (a real background execution + notification-response architecture, plus an OS integration surface) — its own multi-phase arc, not a single foreground-service change.

---

## Phase 1: Persistence & Session Management (The Foundation)
**Goal:** Ensure the AI doesn't "forget" and can manage multiple distinct conversations.
- [x] **Room Database Integration:** Implement `Conversation` and `Message` entities.
- [x] **DaexMemory Service:** Create a repository-pattern service to handle DB operations.
- [x] **Chat Sessions:** UI updates to the Sidebar to support creating, switching, and deleting threads.
- [x] **Context Windowing:** Implement a sliding window logic to manage the 2048-token limit of the engine.

## Phase 2: RAG - Retrieval Augmented Generation (The Knowledge)
**Goal:** Allow the AI to access local data and long-term history via vector search.
- [ ] **Embedding Engine:** Set up a secondary `llama.cpp` context for text-to-vector transformation.
- [ ] **Vector Store:** Implement a local vector search (ObjectBox or custom float-array index).
- [ ] **Semantic Search:** Enable the AI to "search" through its own past conversations and local device data.

## Phase 3: Intent Routing & Decomposition (The Logic)
**Goal:** Make the app smart enough to plan its own execution.
- [ ] **BNF Grammar Support:** Force LLM output into structured JSON for reliability.
- [ ] **Intent Router:** A fast classification step to decide if a query is a chat, a tool-request, or a multi-part task.
- [ ] **Task Decomposition:** Logic to break "Check my battery and write a poem" into a sequence of sub-tasks.

## Phase 4: Agentic Loops (The Action)
**Goal:** Give the AI the ability to interact with the Android OS.
- [ ] **Tool Registry:** Define a standard interface for Kotlin functions the AI can trigger.
- [ ] **ReAct Loop:** The "Think-Act-Observe" cycle within the `DaexInferenceViewModel`.
- [ ] **Hardware Tools:** Implement tools for Battery, Storage, Connectivity, and System Settings.

## Phase 5: Optimization & Polish (The OS)
**Goal:** Performance at scale.
- [ ] **SLM Routing:** Use a <0.8B model for high-speed intent routing to save battery.
- [ ] **Foreground Service:** Allow the AI to complete long-running tasks in the background.
- [ ] **Hardware Acceleration:** Further optimization of Vulkan/GPU layers for modern mobile SoCs.

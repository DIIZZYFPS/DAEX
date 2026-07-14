# DAEX Agent Overhaul Roadmap

## IMPORTANT: Do First
- [ ] **Audio & VAD Simplification**
  - [ ] Remove experimental barge-in code, `smoothedTtsRms` envelope follower, and dynamic echo thresholds from `AudioRecorder.kt` and `DaexInferenceViewModel.kt`.
  - [ ] Revert VAD to use static `speechThreshold` and `silenceThreshold` to fix model deafness and improve baseline listening sensitivity.
- [ ] **Architecture Restructuring & ViewModel Modularization**
  - [ ] Reorganize app file structure to establish clean MVVM/Clean Architecture boundaries (`data/`, `domain/`, `framework/`, `ui/` packages).
  - [ ] Relocate [DaexInferenceViewModel.kt](file:///c:/Users/dgods/Documents/stuff/DAEX/DaexAndroid/app/src/main/java/com/daex/android/services/DaexInferenceViewModel.kt) out of the `services/` directory into `ui/viewmodels/`.
  - [ ] Decouple `DaexInferenceViewModel` into focused view models: `ChatViewModel`, `AudioSessionViewModel`, `SettingsViewModel`, `TtsViewModel`, and `OnboardingViewModel`.

## Phase 1: Agent Overhaul (Skills & Dynamic Compaction)

Revamp the tool execution pipeline to support interactive workflows, dynamic skill loading, and token-constrained context compaction.
- [ ] **Asynchronous UI Action Channel**
  - [x] Implement Kotlin `Channel<AgentAction>` to stream live tool execution progress (e.g. "Pruning...", "Executing...") to the chat interface.
  - [x] Implement interactive permission dialogs in the UI for sensitive tool calls (e.g. launching applications) that suspend model generation until approved/denied.
  - [ ] Add support for dynamic API key requests during execution instead of storing secrets globally.
- [x] **Token-Based Context Compaction & Pressure Tracking**
  - [x] Calculate real-time token pressure based on model context length (e.g., triggering compaction when history consumes >50% of the active context window).
  - [x] Implement a cheap local pre-pass to prune redundant queries, deduplicate identical tool outputs, and strip heavy multimodal payloads.
  - [x] Implement Head/Tail Protection: pin system instructions + initial exchange (Head) and keep the most recent messages/tokens (Tail) intact.
  - [x] Setup middle-turn summarization: compress intermediate turns using a fast local or remote summarizer model into a single `[CONTEXT COMPACTION]:` block with structured handoff formats and dynamic token targeting.
  - [x] Add local deterministic fallback compiler to generate text-based history summaries if the LLM summarization pass fails.
- [ ] **Modular Skills & Generic MCP Router**
  - [x] Implement `load_skill` and `list_skills` tools to dynamically list and inject domain-specific instructions from Markdown files on demand, optimizing the system prompt.
  - [ ] Implement a generic `runMcpTool(toolName, input)` router to connect the local LiteRT client to external Model Context Protocol (MCP) servers.
  - [ ] Ensure automatic redaction of sensitive credentials and tokens before prompt submission.



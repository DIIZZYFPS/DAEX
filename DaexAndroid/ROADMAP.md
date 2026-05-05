# DAEX Implementation Roadmap

This document outlines the strategic phases for evolving DAEX from a simple LLM wrapper into an autonomous on-device AI engine.

## Phase 1: Persistence & Session Management (The Foundation)
**Goal:** Ensure the AI doesn't "forget" and can manage multiple distinct conversations.
- [ ] **Room Database Integration:** Implement `Conversation` and `Message` entities.
- [ ] **DaexMemory Service:** Create a repository-pattern service to handle DB operations.
- [ ] **Chat Sessions:** UI updates to the Sidebar to support creating, switching, and deleting threads.
- [ ] **Context Windowing:** Implement a sliding window logic to manage the 2048-token limit of the engine.

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

# DAEX Agent Overhaul Roadmap

## Phase 1: Agent Overhaul (Skills & Dynamic Compaction)

Revamp the tool execution pipeline to support interactive workflows, dynamic skill loading, and token-constrained context compaction.
- [ ] **Asynchronous UI Action Channel**
  - [x] Implement Kotlin `Channel<AgentAction>` to stream live tool execution progress (e.g. "Pruning...", "Executing...") to the chat interface.
  - [x] Implement interactive permission dialogs in the UI for sensitive tool calls (e.g. launching applications) that suspend model generation until approved/denied.
  - [ ] Add support for dynamic API key requests during execution instead of storing secrets globally.
- [ ] **Token-Based Context Compaction & Pressure Tracking**
  - [ ] Calculate real-time token pressure based on model context length (e.g., triggering compaction when history consumes >50% of the active context window).
  - [ ] Implement a cheap local pre-pass to prune redundant queries, deduplicate identical tool outputs, and strip heavy multimodal payloads.
  - [ ] Implement Head/Tail Protection: pin system instructions + initial exchange (Head) and keep the most recent messages/tokens (Tail) intact.
  - [ ] Setup middle-turn summarization: compress intermediate turns using a fast local or remote summarizer model into a single `[CONTEXT SUMMARY]:` block.
  - [ ] Add local deterministic fallback compiler to generate text-based history summaries if the LLM summarization pass fails.
- [ ] **Modular Skills & Generic MCP Router**
  - [ ] Implement `load_skill` tool to dynamically inject domain-specific instructions from Markdown files on demand, optimizing the system prompt.
  - [ ] Implement a generic `runMcpTool(toolName, input)` router to connect the local LiteRT client to external Model Context Protocol (MCP) servers.
  - [ ] Ensure automatic redaction of sensitive credentials and tokens before prompt submission.


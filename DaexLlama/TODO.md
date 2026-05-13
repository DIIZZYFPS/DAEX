# DaexLlama — Implementation TODO

## Status Legend
- ✅ Done
- 🔧 In Progress
- ⏳ Pending
- ⏸ Deferred

---

## P1 — Crash Prevention & Stability

### 1. Fix JNI function names to match actual Java interface ✅
- JNI names use `Java_com_daex_llama_internal_DaexLlamaEngineImpl_*`
- Matches `DaexLlamaEngineImpl.kt` package path
- **Status:** ✅ Done

### 2. Add `llama_backend_init()` / `llama_backend_free()` ✅
- `llama_backend_init()` called in `nativeInit()` (line 164)
- `llama_backend_free()` called in `nativeShutdown()` (line 653)
- **Status:** ✅ Done

### 3. Add error handling for model load, context init, sampler init ✅
- Model load: checks `!ctx->model`, returns -1 (line 215)
- Context init: checks `!ctx->ctx`, returns -1 (line 251)
- Sampler init: checks `!ctx->sampler`, returns -1 (NEW — line 265)
- Chat templates init: checks `!ctx->chat_templates`, warns & continues (NEW — line 258)
- **Status:** ✅ Done

### 4. Fix cleanup — add `llama_batch_free()` and `common_sampler_free()` ✅
- Destructor calls `common_sampler_free(sampler)`, `llama_batch_free(batch)`, `llama_free(ctx)`, `llama_model_free(model)`
- `chat_templates.reset()` also called
- **Status:** ✅ Done

---

## P2 — API Correctness

### 5. Update deprecated APIs 🔧
- [ ] `llama_model_load_from_file()` → `llama_model_init_from_file()` (lines 212, 564)
- [ ] `llama_n_ctx()` → `llama_context_n_ctx()` (no longer in code — already replaced with `llama_init_from_model`)
- [ ] `llama_token_is_eog()` → `llama_vocab_is_eog()` (already updated — line 455)
- **Status:** 🔧 Partial — 2 of 3 remaining

### 6. Align `llama_batch_init` capacity with context
- [ ] `llama_batch_init(ctx->n_batch, 0, 1)` uses batch size (512), not full context (8192)
- [ ] Current code handles this via `decode_tokens_batched()` loop — works correctly
- [ ] Consider increasing to `std::max(ctx->n_batch, 1024)` for fewer loop iterations on long prompts
- **Status:** ⏳ Pending

---

## P3 — Conversation Quality

### 7. Add system prompt position tracking ✅
- `system_prompt_position` field exists (line 47)
- `nativeProcessSystemPrompt()` sets it (line 379)
- `decode_tokens_batched()` context shift preserves it (lines 300-306)
- **Status:** ✅ Done

### 8. Implement proper context shifting
- [ ] Current approach: removes half of non-system tokens, shifts remaining KV positions
- [ ] Limitation: does NOT re-compute logits after shift — degraded quality for very long contexts
- [ ] Option A: Document as known limitation (quick)
- [ ] Option B: Implement NTK-aware context scaling (medium effort)
- [ ] Option C: Implement sliding window attention (high effort)
- **Status:** ⏳ Pending — recommend Option A for now, B later

---

## P4 — Features

### 9. Add model info JNI methods
- [ ] `nativeGetModelInfo()` — expose `n_params`, `n_ctx_train`, `n_embd`, `description` (via `llama_model_desc()`)
- [ ] Return as JSON string or individual getters
- [ ] Useful for UI display before/during model load
- **Status:** ⏳ Pending

### 10. Implement structured output (Grammar/JSON mode)
- [ ] Add `nativeSetGrammar(ctx_id, jgrammar)` JNI method
- [ ] Call `common_sampler_set_grammar()` after sampler init
- [ ] Add `nativeClearGrammar(ctx_id)` to disable
- [ ] Grammar format: llama.cpp EBNF grammar strings
- **Status:** ⏳ Pending

### 11. Implement `benchModel()` method
- [ ] Port from official `ai_chat.cpp`
- [ ] Benchmark: token generation speed (tok/s), KV cache speed
- [ ] Returns timing results as JSON string
- **Status:** ⏳ Pending

### 12. Add NTK-aware context scaling
- [ ] When context overflows, apply temperature scaling to KV positions instead of discarding tokens
- [ ] Reference: llama.cpp's `llama_kv_cache_scale_context()` or similar
- [ ] Improves quality over simple token discard
- **Status:** ⏸ Deferred

---

## P5 — Testing & CI

### 13. C++ unit tests for native bridge
- [ ] Write tests for: model load error handling, context creation/destruction, tokenization, generation loop
- [ ] Use Google Test framework or Catch2
- [ ] Cross-compile via CMake for x86_64 host testing
- **Status:** ⏳ Pending

### 14. Gradle test pipeline ✅
- [x] Unit tests for `DaexLlamaEngine` state machine
- [x] Test: init → create context → load model → prepare → process prompt → generate → cancel
- [x] Test: error paths (null model, invalid context)
- [x] CI workflow at `.github/workflows/build.yml`
- **Status:** ✅ Done

### 15. Verify OpenMP runtime on Android
- [ ] Confirm `GGML_OPENMP` builds and links correctly with Android NDK r25+
- [ ] Test on device — OpenMP runtime may not be bundled in all NDK versions
- [ ] If issues, switch to `GGML_NATIVE ON` or remove OpenMP
- **Status:** ⏳ Pending

---

## P6 — Lower Priority

### 16. Expose model metadata JNI methods
- [ ] `nativeGetModelParamCount()`, `nativeGetModelContextTrain()`, `nativeGetModelEmbeddingDim()`
- [ ] Complement to #9 — individual getters vs JSON object
- **Status:** ⏸ Deferred (subsumed by #9)

### 17. Make sampling params configurable from Java
- [ ] `nativeSetSamplingParams()` — top_k, top_p, min_p, repeat_penalty, frequency_penalty, presence_penalty
- [ ] Re-init sampler when params change
- **Status:** ⏸ Deferred (current hardcoded params work for MVP)

### 18. Thread safety audit
- [ ] All state protected by `g_contexts_mutex`
- [ ] Each context is isolated — no cross-context races
- [ ] Generation is single-threaded per context (coroutine-based from Kotlin)
- [ ] Document single-thread-per-context constraint in Kotlin layer
- **Status:** ✅ Done — mutex + per-context isolation

### 19. UTF-8 validation improvement
- [ ] Current `is_valid_utf8()` is strict — returns false for any invalid byte
- [ ] Consider fallback: replace invalid bytes with U+FFFD rather than dropping tokens
- **Status:** ⏸ Deferred (current behavior is acceptable)

### 20. Magic numbers → constexpr/configurable
- [ ] `n_ctx = 8192` → default constant
- [ ] `n_batch = 512` → default constant
- [ ] `n_predict = 1024` → already configurable via `nativeSetConfig()`
- [ ] `sampler_temp = 0.3f` → already configurable
- **Status:** ⏳ Partial — some configurable, defaults could be constexpr

---

## Summary

| Priority | Total | Done | In Progress | Pending | Deferred |
|----------|-------|------|-------------|---------|----------|
| P1 Stability | 4 | 4 | 0 | 0 | 0 |
| P2 API Correctness | 2 | 1 | 1 | 0 | 0 |
| P3 Conversation Quality | 2 | 1 | 0 | 1 | 0 |
| P4 Features | 4 | 0 | 0 | 4 | 1 |
| P5 Testing & CI | 3 | 1 | 0 | 2 | 0 |
| P6 Lower Priority | 5 | 1 | 0 | 0 | 4 |
| **Total** | **20** | **8** | **1** | **7** | **5** |

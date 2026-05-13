# DaexLlama JNI Bridge — API Surface Map

> For T1 (JNI Safety Audit). All signatures verified against `llama.cpp` headers at:
> `/home/diizzy/Projects/DAEX/DaexLlama/src/main/cpp/external/llama.cpp/`

---

## 1. Core llama.h API (llama-cpp public API)

### Initialization / Lifecycle
| Function | Signature | Used By |
|---|---|---|
| `llama_backend_init` | `void llama_backend_init(void)` | `nativeInit()` |
| `llama_backend_free` | `void llama_backend_free(void)` | `nativeShutdown()` |

### Model Loading
| Function | Signature | Used By |
|---|---|---|
| `llama_model_default_params` | `struct llama_model_params llama_model_default_params(void)` | `nativeLoadModel()`, `nativeLoadEmbeddingModel()` |
| `llama_model_load_from_file` | `struct llama_model * llama_model_load_from_file(const char * path_model, struct llama_model_params params)` | `nativeLoadModel()`, `nativeLoadEmbeddingModel()` |
| `llama_model_free` | `void llama_model_free(struct llama_model * model)` | `LlamaContext` destructor |
| `llama_model_size` | `uint64_t llama_model_size(const struct llama_model * model)` | `nativeLoadModel()` (logging) |
| `llama_model_n_params` | `uint64_t llama_model_n_params(const struct llama_model * model)` | `nativeLoadModel()` (logging) |
| `llama_model_n_ctx_train` | `int32_t llama_model_n_ctx_train(const struct llama_model * model)` | `nativePrepareContext()`, `nativeLoadEmbeddingModel()` |
| `llama_model_n_embd` | `int32_t llama_model_n_embd(const struct llama_model * model)` | `nativeGetEmbedding()` |
| `llama_model_get_vocab` | `const struct llama_vocab * llama_model_get_vocab(const struct llama_model * model)` | `nativeGenerateNextToken()` |

### Context Creation
| Function | Signature | Used By |
|---|---|---|
| `llama_context_default_params` | `struct llama_context_params llama_context_default_params(void)` | `nativePrepareContext()`, `nativeLoadEmbeddingModel()` |
| `llama_init_from_model` | `struct llama_context * llama_init_from_model(struct llama_model * model, struct llama_context_params params)` | `nativePrepareContext()`, `nativeLoadEmbeddingModel()` |
| `llama_free` | `void llama_free(struct llama_context * ctx)` | `LlamaContext` destructor |

### Context Query
| Function | Signature | Used By |
|---|---|---|
| `llama_get_memory` | `llama_memory_t llama_get_memory(const struct llama_context * ctx)` | `nativeProcessSystemPrompt()`, `nativeResetConversation()` |
| `llama_pooling_type` | `enum llama_pooling_type llama_pooling_type(const struct llama_context * ctx)` | (declared, not used by DaexLlama) |

### KV Cache / Memory Management
| Function | Signature | Used By |
|---|---|---|
| `llama_memory_clear` | `void llama_memory_clear(llama_memory_t mem, bool clear_data)` | `nativeProcessSystemPrompt()`, `nativeResetConversation()` |
| `llama_memory_seq_rm` | `bool llama_memory_seq_rm(llama_memory_t mem, llama_seq_id seq_id, llama_pos im0, llama_pos im1)` | `decode_tokens_batched()` (context shifting) |
| `llama_memory_seq_add` | `void llama_memory_seq_add(llama_memory_t mem, llama_seq_id seq_id, llama_pos im0, llama_pos im1, float delta)` | `decode_tokens_batched()` (context shifting) |

### Batch API
| Function | Signature | Used By |
|---|---|---|
| `llama_batch_init` | `struct llama_batch llama_batch_init(int32_t n_tokens, int32_t n_embd, int32_t n_seq_max)` | `nativePrepareContext()`, `nativeLoadEmbeddingModel()` |
| `llama_batch_free` | `void llama_batch_free(struct llama_batch batch)` | `LlamaContext` destructor |

### Decoding
| Function | Signature | Used By |
|---|---|---|
| `llama_decode` | `int32_t llama_decode(struct llama_context * ctx, struct llama_batch batch)` | `decode_tokens_batched()`, `nativeGenerateNextToken()`, `nativeGetEmbedding()` |

### Vocabulary / Tokenization
| Function | Signature | Used By |
|---|---|---|
| `llama_vocab_is_eog` | `bool llama_vocab_is_eog(const struct llama_vocab * vocab, llama_token token)` | `nativeGenerateNextToken()` |

### Embeddings
| Function | Signature | Used By |
|---|---|---|
| `llama_get_embeddings_seq` | `float * llama_get_embeddings_seq(struct llama_context * ctx, llama_seq_id seq_id)` | `nativeGetEmbedding()` |

### System Info
| Function | Signature | Used By |
|---|---|---|
| `llama_print_system_info` | `const char * llama_print_system_info(void)` | `nativeSystemInfo()` |

### Logging
| Function | Signature | Used By |
|---|---|---|
| `llama_log_set` | `void llama_log_set(ggml_log_callback log_callback, void * user_data)` | `nativeInit()` |

---

## 2. common.h API (llama.cpp common library)

### Batch Helpers
| Function | Signature | Used By |
|---|---|---|
| `common_batch_clear` | `void common_batch_clear(struct llama_batch & batch)` | `decode_tokens_batched()`, `nativeGenerateNextToken()`, `nativeGetEmbedding()` |
| `common_batch_add` | `void common_batch_add(struct llama_batch & batch, llama_token token, llama_pos pos, const std::vector<llama_seq_id> & seq_ids, bool logits)` | `decode_tokens_batched()`, `nativeGenerateNextToken()` |

### Tokenization
| Function | Signature | Used By |
|---|---|---|
| `common_tokenize` | `std::vector<llama_token> common_tokenize(const struct llama_context * ctx, const std::string & text, bool add_bos, bool special)` | `nativeProcessSystemPrompt()`, `nativeProcessUserPrompt()`, `nativeGetEmbedding()` |

### Token Decoding
| Function | Signature | Used By |
|---|---|---|
| `common_token_to_piece` | `std::string common_token_to_piece(const struct llama_context * ctx, llama_token token, bool special = false)` | `nativeGenerateNextToken()` |

### Sampling
| Function | Signature | Used By |
|---|---|---|
| `common_sampler_init` | `struct common_sampler * common_sampler_init(const struct llama_model * model, struct common_params_sampling & params)` | `nativePrepareContext()` |
| `common_sampler_free` | `void common_sampler_free(struct common_sampler * gsmpl)` | `LlamaContext` destructor |
| `common_sampler_sample` | `llama_token common_sampler_sample(struct common_sampler * gsmpl, struct llama_context * ctx, int idx, bool grammar_first = false)` | `nativeGenerateNextToken()` |
| `common_sampler_accept` | `void common_sampler_accept(struct common_sampler * gsmpl, llama_token token, bool is_generated)` | `nativeGenerateNextToken()` |

### Chat Templates
| Function | Signature | Used By |
|---|---|---|
| `common_chat_templates_init` | `common_chat_templates_ptr common_chat_templates_init(const struct llama_model * model, const std::string & path)` | `nativePrepareContext()` |
| `common_chat_format_single` | `std::string common_chat_format_single(const struct common_chat_templates * tmpls, const std::vector<common_chat_msg> & history, const common_chat_msg & new_msg, bool add_ass, bool add_user_trailing_newline)` | `chat_add_and_format()` |
| `common_chat_templates_was_explicit` | `bool common_chat_templates_was_explicit(const struct common_chat_templates * tmpls)` | `nativeProcessSystemPrompt()`, `nativeProcessUserPrompt()` |

### Sampling Params
| Struct | Fields Used | Used By |
|---|---|---|
| `common_params_sampling` | `temp` | `nativePrepareContext()` (passed to `common_sampler_init`) |

---

## 3. ggml-backend.h API (backend auto-detection)

| Function | Signature | Used By |
|---|---|---|
| `ggml_backend_load_all_from_path` | `void ggml_backend_load_all_from_path(const char * dir_path)` | `nativeInit()` |
| `ggml_backend_reg_count` | `size_t ggml_backend_reg_count(void)` | `get_active_backends()` |
| `ggml_backend_reg_get` | `ggml_backend_reg_t ggml_backend_reg_get(size_t index)` | `get_active_backends()` |
| `ggml_backend_reg_name` | `const char * ggml_backend_reg_name(ggml_backend_reg_t reg)` | `get_active_backends()` |

---

## 4. API Version Concerns

### Deprecated APIs (still used correctly)
- `llama_init_from_model` — replaces deprecated `llama_new_context_with_model` (correct usage)
- `llama_model_load_from_file` — replaces deprecated `llama_load_model_from_file` (correct usage)
- `llama_model_free` — replaces deprecated `llama_free_model` (correct usage)
- `llama_model_n_ctx_train` — replaces deprecated `llama_n_ctx_train` (correct usage)
- `llama_model_n_embd` — replaces deprecated `llama_n_embd` (correct usage)
- `llama_vocab_is_eog` — replaces deprecated `llama_token_is_eog` (correct usage)

### Potential API Drift Risks
1. **`llama_memory_seq_rm` returns `bool`** — DaexLlama ignores the return value. The function returns `false` if the range is invalid. Should check and log.
2. **`llama_memory_seq_add`** — takes a `float delta` parameter. DaexLlama passes `-n_discard` which is correct for shifting positions.
3. **`llama_batch` is passed by value** to `llama_decode` and `llama_batch_free` — DaexLlama stores it as a member and passes it correctly.
4. **`common_batch_add` takes `const std::vector<llama_seq_id>&`** — DaexLlama passes `{0}` (initializer list) which works but allocates a temporary vector each call.

---

## 5. JNI Contract Summary

| JNI Method | Native Signature | Kotlin Signature | Return Type |
|---|---|---|---|
| `nativeInit` | `(Ljava/lang/String;)V` | `fun nativeInit(libDir: String)` | void |
| `nativeCreateContext` | `()I` | `fun nativeCreateContext(): Int` | context ID |
| `nativeDestroyContext` | `(I)V` | `fun nativeDestroyContext(ctxId: Int)` | void |
| `nativeLoadModel` | `(ILjava/lang/String;)I` | `fun nativeLoadModel(ctxId: Int, modelPath: String): Int` | 0=ok, -1=err |
| `nativePrepareContext` | `(I)I` | `fun nativePrepareContext(ctxId: Int): Int` | 0=ok, -1=err |
| `nativeSetConfig` | `(IIIF)V` | `fun nativeSetConfig(ctxId: Int, nCtx: Int, nBatch: Int, temp: Float, nPredict: Int)` | void |
| `nativeProcessSystemPrompt` | `(ILjava/lang/String;)I` | `fun nativeProcessSystemPrompt(ctxId: Int, prompt: String): Int` | 0=ok, 1=err |
| `nativeProcessUserPrompt` | `(ILjava/lang/String;)I` | `fun nativeProcessUserPrompt(ctxId: Int, prompt: String): Int` | 0=ok, 1=err |
| `nativeGenerateNextToken` | `(I)Ljava/lang/String;` | `fun nativeGenerateNextToken(ctxId: Int): String?` | token or null |
| `nativeCancelGeneration` | `(I)V` | `fun nativeCancelGeneration(ctxId: Int)` | void |
| `nativeResetConversation` | `(I)V` | `fun nativeResetConversation(ctxId: Int)` | void |
| `nativeSystemInfo` | `()Ljava/lang/String;` | `fun nativeSystemInfo(): String` | system info string |
| `nativeActiveBackends` | `()Ljava/lang/String;` | `fun nativeActiveBackends(): String` | backend names |
| `nativeUnloadModel` | `(I)V` | `fun nativeUnloadModel(ctxId: Int)` | void |
| `nativeLoadEmbeddingModel` | `(ILjava/lang/String;)I` | `fun nativeLoadEmbeddingModel(ctxId: Int, modelPath: String): Int` | 0=ok, -1=err |
| `nativeGetEmbedding` | `(ILjava/lang/String;)[F` | `fun nativeGetEmbedding(ctxId: Int, text: String): FloatArray?` | embedding array |
| `nativeShutdown` | `()V` | `fun nativeShutdown()` | void |

---

## 6. Header Includes

```cpp
#include "llama.h"          // Core llama-cpp API
#include "ggml.h"           // GGML types (ggml_log_level, ggml_log_callback)
#include "ggml-backend.h"   // Backend loading (ggml_backend_load_all_from_path)
#include "common.h"         // common_batch_*, common_tokenize, common_sampler_*
#include "sampling.h"       // common_params_sampling
#include "chat.h"           // common_chat_templates_*, common_chat_msg
```

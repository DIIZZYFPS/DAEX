# QNN SDK & Genie GenAI Library — Comprehensive Research Report

> **Version:** QNN SDK 2.46.0.260424 (Quokka/Qualla platform)
> **Target:** Snapdragon NPU/HTP inference for LLMs
> **Scope:** Core QNN API → Genie GenAI Library → LLM tutorials (KV-sharing, LoRA, SPD, SSD-Q1)

---

## Table of Contents

1. [QNN Core API](#1-qnn-core-api)
2. [Genie GenAI Library](#2-genie-genai-library)
3. [LLM Inference Tutorials](#3-llm-inference-tutorials)
4. [JNI Bridge Integration Strategy](#4-jni-bridge-integration-strategy)
5. [Key Findings & Recommendations](#5-key-findings--recommendations)

---

## 1. QNN Core API

### 1.1 Architecture Overview

Qualcomm QNN (Qualcomm Neural Network) is a cross-platform inference runtime that targets Qualcomm NPUs (Hexagon) and HTP (Hexagon Tensor Processor) on Snapdragon SoCs. It follows a provider-based architecture:

```
Application
    ↓
QNN Host API (C/C++)
    ↓
QNN Backend Provider (HTP, CPU, GPU, AIP)
    ↓
Hexagon DSP / HTP Hardware
```

The SDK is distributed at:
```
/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/
├── include/          # Public C/C++ headers
├── lib/              # Shared libraries (libqnn*.so)
├── examples/         # Reference implementations
│   └── Genie/        # GenAI library (Genie)
│       └── Genie/    # Full LLM inference framework
└── tools/            # Conversion tools (onnx2qnn, etc.)
```

### 1.2 Core QNN Types (qnn/types.h)

The QNN runtime uses a unified tensor and graph representation:

**Tensor Types (QNN_TENSOR_DATA_TYPE_*)**

| QNN Type | C/C++ Type | Range | Use Case |
|----------|-----------|-------|----------|
| FLOAT32 | float | 32-bit IEEE 754 | Base model weights |
| FLOAT16 | uint16_t | 16-bit IEEE 754 | Quantized weights |
| BFLOAT16 | uint16_t | 16-bit brain float | Mixed precision |
| UINT8 | uint8_t | 0–255 | Q8 quantized tensors |
| INT8 | int8_t | -128–127 | Q8 quantized tensors |
| INT32 | int32_t | 32-bit signed | Indices, counts |
| BOOL | uint8_t | 0/1 | Masks, flags |

**Tensor Quantization:** QNN supports per-channel and per-tensor quantization with scale/offset parameters. The quantization formula is: `dequantized = (quantized - offset) * scale`.

**Backend Provider Types (QNN_BACKEND_DEVICE_*)**

| Device | Backend ID | Use Case |
|--------|-----------|----------|
| CPU | QNN_BACKEND_DEVICE_CPU | Fallback, reference |
| GPU | QNN_BACKEND_DEVICE_GPU | Graphics + compute |
| AIP (HTP) | QNN_BACKEND_DEVICE_AIP | Neural network acceleration |

**Context Priority (QNN_CONTEXT_PRIORITY_*)**

| Priority | Use Case |
|----------|----------|
| REALTIME | Streaming/interactive (lowest latency) |
| BALANCED | General purpose |
| BULK | Offline/batch processing |

**Context Attribute (QNN_CONTEXT_ATTRIBUTE_*)**

Key attributes for LLM inference:
- `QNN_CONTEXT_ATTRIBUTE_AIP_HTP_USE_FAST_FPMATH` — Enable fast FP math on HTP
- `QNN_CONTEXT_ATTRIBUTE_AIP_HTP_PERF_MODE` — Performance mode selection
- `QNN_CONTEXT_ATTRIBUTE_AIP_HTP_POWER_OPT_MODE` — Power optimization
- `QNN_CONTEXT_ATTRIBUTE_AIP_HTP_MEMORY_MODE` — Memory allocation strategy

**Graph Attributes (QNN_GRAPH_ATTRIBUTE_*)**

- `QNN_GRAPH_ATTRIBUTE_AIP_HTP_MEMORY_MODE` — HTP memory mode
- `QNN_GRAPH_ATTRIBUTE_AIP_HTP_PERF_MODE` — HTP performance mode
- `QNN_GRAPH_ATTRIBUTE_AIP_HTP_ENABLE_CACHE` — Enable HTP cache

**Op Attributes (QNN_OP_ATTRIBUTE_*)**

Key ops for LLMs:
- `QNN_OP_ATTRIBUTE_REDUCE_AVG_INCLUDE_ZERO` — Attention mask handling
- `QNN_OP_ATTRIBUTE_REDUCE_KEEP_DIMS` — Shape preservation
- `QNN_OP_ATTRIBUTE_PAD_MODE` — Padding strategy
- `QNN_OP_ATTRIBUTE_PAD_VALUE` — Padding value
- `QNN_OP_ATTRIBUTE_SOFTMAX_CHANNEL` — Softmax axis
- `QNN_OP_ATTRIBUTE_GATHER_AXIS` — Embedding lookup axis
- `QNN_OP_ATTRIBUTE_CONV_STRIDE` — Convolution stride
- `QNN_OP_ATTRIBUTE_CONV_DILATION` — Convolution dilation

### 1.3 HTP Backend Configuration

The HTP (Hexagon Tensor Processor) is the primary accelerator for LLM inference:

**HTP Performance Modes:**
- `LOW_POWER` — Battery-friendly, lower throughput
- `BALANCED` — Default balance
- `HIGH_PERFORMANCE` — Maximum throughput
- `SUSTAINED_HIGH_PERFORMANCE` — Sustained max performance

**HTP Memory Modes:**
- `DEFAULT` — Standard memory allocation
- `LOW_POWER` — Reduced memory footprint
- `HIGH_PERFORMANCE` — Max memory bandwidth

**HTP Power Opt Modes:**
- `DEFAULT` — Standard power management
- `LOW_POWER` — Power saving
- `HIGH_PERFORMANCE` — Max power budget

### 1.4 Model Conversion (onnx2qnn)

Models are converted from ONNX to QNN DLC (Qualcomm DLC format) using the `onnx2qnn` tool:

```bash
onnx2qnn \
  --config config.yaml \
  --input onnx_model.onnx \
  --output output.dlc \
  --target qnn
```

**Key conversion parameters:**
- Quantization scheme (INT8, FP16, BF16)
- Input/output tensor specifications
- Backend target (HTP, CPU, GPU)
- Performance mode settings

### 1.5 QNN Runtime Lifecycle

```
1. QNN_Init()                      — Initialize runtime
2. QNN_GetProviderInterface()      — Get provider interface
3. qnn_provider_create()           — Create backend provider
4. qnn_context_create()            — Create execution context
5. qnn_graph_create()              — Create computation graph
6. qnn_graph_add_op()              — Add operations
7. qnn_graph_bind_tensor()         — Bind tensors to ops
8. qnn_graph_finalize()            — Finalize graph
9. qnn_context_apply()             — Apply graph to context
10. qnn_context_execute()          — Execute (sync or async)
11. qnn_context_sync()             — Sync for async execution
12. Cleanup: destroy graph/context/provider
```

---

## 2. Genie GenAI Library

### 2.1 Architecture Overview

Genie is Qualcomm's high-level GenAI inference framework built on top of QNN. It provides an end-to-end LLM inference pipeline:

```
┌─────────────────────────────────────────────────┐
│                 Application Layer                │
│         (Chat UI, CLI, API Server)              │
├─────────────────────────────────────────────────┤
│              Genie Middleware                    │
│         (Session management, callbacks)         │
├─────────────────────────────────────────────────┤
│              Dialog Layer                        │
│   (BasicDialog, SpecDecDialog, KVShareDialog)   │
├─────────────────────────────────────────────────┤
│              Engine Layer                        │
│   (QNN Engine, CPU Engine, HTP Engine)          │
├─────────────────────────────────────────────────┤
│              Sampler Layer                       │
│   (Temperature, Top-K, Top-P, Penalty)          │
├─────────────────────────────────────────────────┤
│              Tokenizer Layer                     │
│   (SentencePiece, TikToken, Custom)             │
├─────────────────────────────────────────────────┤
│              QNN Runtime                         │
│         (HTP Backend, CPU Backend)              │
└─────────────────────────────────────────────────┘
```

### 2.2 Core Components

#### 2.2.1 Environment (Env)

The `Env` class is the root context for all Genie operations:

```cpp
class Env : public std::enable_shared_from_this<Env> {
    std::shared_ptr<Config> _config;        // Runtime configuration
    std::shared_ptr<ResourceManager> _rm;   // Resource management
    std::shared_ptr<Path> _path;            // File paths (models, cache)
    std::shared_ptr<Trace> _trace;          // Debug tracing
    std::shared_ptr<Log> _log;              // Logging
    // ...
};
```

**Key methods:**
- `Env::create(config)` — Create environment from config
- `Env::getResourceManager()` — Access DLC/file resource manager
- `Env::getPath()` — Access file system paths

#### 2.2.2 Context (Context)

The `Context` class represents the loaded model with its metadata:

```cpp
class Context {
    uint32_t _n_vocab;       // Vocabulary size
    uint32_t _n_embd;        // Embedding dimension
    uint32_t _n_layer;       // Number of transformer layers
    uint32_t _n_head;        // Number of attention heads
    uint32_t _n_kv_head;     // Number of KV heads (GQA)
    uint32_t _size;          // Context window size
    std::string _model_name; // Model identifier
    // ...
};
```

**Key methods:**
- `Context::n_vocab()` — Get vocabulary size
- `Context::n_layer()` — Get number of layers
- `Context::n_embd()` — Get embedding dimension
- `Context::size()` — Get context window size
- `Context::is_eos(token)` — Check if token is end-of-sequence
- `Context::get_spec()` — Get model specification

#### 2.2.3 Engine (Engine)

The `Engine` class wraps a QNN backend provider and handles inference execution:

```cpp
class Engine {
    std::shared_ptr<Context> _ctx;      // Model context
    std::shared_ptr<Config> _config;    // Engine config
    std::unique_ptr<Backend> _backend;  // QNN backend provider
    std::unique_ptr<Graph> _graph;      // Computation graph
    std::unique_ptr<Context> _qnn_ctx;  // QNN execution context
    TensorMap _inputs;                   // Input tensors
    TensorMap _outputs;                  // Output tensors
    TensorMap _kv_cache;                 // KV cache tensors
    // ...
};
```

**Engine Feature Flags (Engine::Feature::Flags):**
- `DYNAMIC_LOAD` — Support dynamic model loading
- `KV_CACHE` — Support KV cache management
- `ATTENTION_MASK` — Support attention masks
- `PROMPT_LOOKUP` — Support prompt lookup decoding
- `EMBEDDING_INPUT` — Support embedding-based input
- `MULTI_TOKEN` — Support multi-token processing
- `ALL_LOGITS` — Support all-logits output mode

**Engine Methods:**

```cpp
// Model loading
bool load();                          // Load model into QNN runtime
bool unload();                        // Unload model from runtime

// Inference
size_t process(std::vector<int32_t>& tokens, Tensor& logits, bool all_logits);
size_t process(std::vector<int::vector<int32_t>>& tokens,
               std::vector<int32_t>& attention_map,
               Tensor& logits,
               bool all_logits);
size_t process(std::vector<uint8_t>& embeddings,
               std::vector<int32_t>& attention_map,
               Tensor& logits,
               bool all_logits);

// KV Cache Management
bool updateKV(size_t n_past);
bool updateKV(size_t n_past, const std::vector<bool>& selected);
void updateTokenCheckpoint(uint32_t token, size_t position);
size_t restore(const std::string& name, bool is_kv_prefix = false);
size_t restore(const std::string& name);
bool save(const std::string& name);

// Configuration
void set(const std::map<std::string, Value>& config);
bool supports(Feature::Flags feature);

// Tensor access
bool getKVHead(CacheFileSpec& spec, uint32_t layer, uint32_t head,
               void* data, double* scales);
bool setKVHead(CacheFileSpec& spec, uint32_t layer, uint32_t head,
               const void* data, const double* scales);
void getCacheSpec(CacheFileSpec& spec);
bool getRopePermute();
bool isKVQuantized();
uint32_t getEmbeddingBufferSize();
bool cacheEosEmbedding(const std::vector<uint8_t>& embedding);
```

**Engine Pipeline Stages (Engine::Stage):**

| Stage | Value | Description |
|-------|-------|-------------|
| PRE_FILL | 0 | Prompt processing (full attention) |
| DECODE | 1 | Token-by-token generation (causal attention) |
| PREFILL_DECODE | 2 | Combined prefill + decode |
| KV_RESTORE | 3 | KV cache restoration |
| KV_SAVE | 4 | KV cache save |

**Engine Pipeline Node Types (Engine::NodeType):**

| Node Type | Description |
|-----------|-------------|
| ATTENTION | Attention computation |
| MLP | Feed-forward network |
| EMBEDDING | Token embedding lookup |
| LM_HEAD | Language model head |
| NORM | Normalization layer |
| ROPE | Rotary position embeddings |

**Engine KV Cache Spec (CacheFileSpec):**

```cpp
struct CacheFileSpec {
    uint32_t magic;       // Magic number (0xC0DE)
    uint32_t num_tensors; // Total KV tensors (2 * n_layers * n_heads)
    DataType dtype;       // Data type (FP32, INT8, etc.)
    uint32_t n_heads;     // Number of attention heads
    uint32_t embed_dim;   // KV dimension per head
    uint32_t update_size; // Number of tokens in cache
};
```

#### 2.2.4 Dialog (Dialog)

The `Dialog` class defines the high-level inference loop. It's the main abstraction for different inference strategies:

**Base Dialog:**
```cpp
class Dialog {
    std::map<std::string, std::shared_ptr<Engine>> _engine;  // Named engines
    std::map<std::string, std::shared_ptr<Sampler>> _sampler; // Named samplers
    std::shared_ptr<Tokenizer> _tokenizer;                    // Tokenizer
    std::shared_ptr<Context> _ctx;                            // Model context
    std::shared_ptr<Env> _env;                                // Environment

    size_t _n_past;       // Total tokens processed
    size_t _n_prompt;     // Prompt tokens
    size_t _n_generated;  // Generated tokens
    size_t _n_decode;     // Decode tokens
    int32_t _last_tok;    // Last generated token

    KPIs _kpis;           // Performance metrics
    State _state;         // Dialog state machine

    virtual bool process(std::vector<int32_t>& tokens, Callback callback) = 0;
};
```

**Callback Interface:**
```cpp
class Callback {
    enum Sentence { BEGIN, CONTINUE, END };
    virtual bool callBack(const int32_t* tokens, size_t n_tokens,
                          Sentence sentence, Tokenizer* tokenizer) = 0;
};
```

**Available Dialog Types:**

| Dialog | Purpose | Engines | Key Feature |
|--------|---------|---------|-------------|
| BasicDialog | Standard autoregressive | 1 (primary) | Simple prompt + generate |
| SpecDecDialog | Speculative decoding | 2 (primary + secondary) | Draft + target models |
| KvShareDialog | KV cache sharing | 2 (primary + secondary) | Prompt/generate engine split |
| MultiStreamDialog | Multi-output generation | 1 (primary) | Top-K parallel streams |
| SelfSpecDecDialog | Self-speculative (SSD-Q1) | 1 (primary) | Branch-based draft tree |

#### 2.2.5 Sampler (Sampler)

The `Sampler` class handles token selection from logits:

```cpp
class Sampler {
    // Sampling strategies
    bool _greedy;         // Always pick max probability
    float _temperature;   // Softmax temperature
    float _top_k;         // Top-K filtering
    float _top_p;         // Nucleus sampling threshold
    float _repetition_penalty;  // Repetition penalty
    float _presence_penalty;    // Presence penalty
    float _frequency_penalty;   // Frequency penalty

    // Advanced
    bool _gumbel;         // Gumbel-max trick for sampling
    std::mt19937 _rng;    // Random number generator

    // Methods
    int32_t process(Tensor& logits);
    int32_t process(Tensor& logits, std::vector<float>& probs, bool softmax);
    void updatePenalty(const Sampler& other);
    void updateSampledTokenHistory(int32_t token);
    void updateSampledTokenHistory(const std::vector<int32_t>& tokens, int32_t streamIdx);
};
```

**Sampling Methods:**

| Method | Description | Use Case |
|--------|-------------|----------|
| Greedy | Always pick argmax | Deterministic output |
| Temperature | Scale logits before softmax | Controlled randomness |
| Top-K | Sample from top-K probabilities | Limit output space |
| Top-P (Nucleus) | Sample from cumulative probability mass | Adaptive output space |
| Gumbel-Max | Gumbel noise + argmax | Reparameterizable sampling |

**Penalty Application:**
```cpp
template<typename T>
void applyPenalty(Tensor& tensor, const Penalty& penalty, int32_t streamIdx);
```
Penalties are applied per-token to the logits before sampling. Supports repetition, presence, and frequency penalties.

#### 2.2.6 Tokenizer (Tokenizer)

The `Tokenizer` class handles text ↔ token conversion:

```cpp
class Tokenizer {
    std::string _tokenizer_type;  // "sentencepiece", "tiktoken", "custom"
    std::shared_ptr<void> _impl;  // Backend-specific implementation

    std::vector<int32_t> encode(const std::string& text);
    std::string decode(const std::vector<int32_t>& tokens);
    std::string decode(const int32_t* tokens, size_t n_tokens);
};
```

**Supported Tokenizer Types:**
- **SentencePiece** — Standard for most LLMs (LLaMA, Mistral, etc.)
- **TikToken** — OpenAI's tokenizer (GPT models)
- **Custom** — User-defined tokenizer via callback

#### 2.2.7 Pipeline (Pipeline)

The `Pipeline` class orchestrates the full inference workflow:

```cpp
class Pipeline {
    std::shared_ptr<Env> _env;
    std::shared_ptr<Context> _context;
    std::shared_ptr<Dialog> _dialog;
    std::shared_ptr<Tokenizer> _tokenizer;
    std::shared_ptr<Sampler> _sampler;

    // Lifecycle
    bool init();
    bool run(std::string prompt);
    void reset();
    void shutdown();

    // Streaming
    void setOutputCallback(OutputCallback cb);
};
```

**Pipeline Flow:**
1. `Pipeline::init()` — Load model, create engine, dialog, sampler
2. `Pipeline::run(prompt)` — Process prompt, generate tokens
3. Output callback receives tokens as they're generated
4. `Pipeline::reset()` — Clear KV cache, reset state
5. `Pipeline::shutdown()` — Unload model, cleanup

---

## 3. LLM Inference Tutorials

### 3.1 Basic Dialog (Standard Autoregressive)

**File:** `src/qualla/dialogs/basic.cpp`

The BasicDialog implements standard prompt-then-generate inference:

```
1. Process prompt tokens → get logits
2. Sample first token from logits
3. Loop:
   a. Process single token → get next logits
   b. Sample next token
   c. Update KV cache with new token
   d. If EOS → stop
   e. Output token via callback
```

**Key characteristics:**
- Single engine (primary)
- Sequential token generation
- Standard causal attention
- Full KV cache management
- Supports both token and embedding input

**Code flow:**
```cpp
bool BasicDialog::process(std::vector<int32_t>& tokens, Callback callback) {
    // 1. Process prompt
    engine->process(tokens, logits, false);  // all_logits=false
    n_past += tokens.size();
    engine->updateKV(n_past);

    // 2. Sample first token
    last_tok = sampler->process(logits);
    callback(decode(last_tok), Sentence::BEGIN);

    // 3. Generate loop
    while (!eos) {
        tokens[0] = last_tok;
        engine->process(tokens, logits, false);
        last_tok = sampler->process(logits);
        n_past++;
        engine->updateKV(n_past);
        callback(decode(last_tok), Sentence::CONTINUE);
    }
}
```

### 3.2 KV Cache Sharing (Prompt/Generate Split)

**File:** `src/qualla/dialogs/kv-share.cpp`

The KvShareDialog splits inference across two engines:
- **Primary engine:** Handles prompt processing (prefill)
- **Secondary engine:** Handles token-by-token generation (decode)

**Why two engines?** On Android, the HTP can be configured differently for prompt processing vs. generation. The prompt phase processes many tokens at once (batch size = context length), while generation processes one token at a time (batch size = 1). Different HTP configurations optimize each phase differently.

**Two KV sharing modes:**

1. **File-based (legacy):** Save primary KV cache to disk, convert format, load into secondary engine
   - Used when `kv-share.enable-in-memory-kv-share = false`
   - Involves: save → convert (dequant + transpose) → restore
   - Supports cross-backend (HTP → CPU)

2. **In-memory (modern):** Direct KV cache transfer between engines
   - Used when `kv-share.enable-in-memory-kv-share = true`
   - Much faster, no disk I/O
   - Requires same backend for both engines

**KV Cache Conversion (file-based):**

The conversion handles:
1. **Dequantization:** Convert Q8 (uint8, range 0-255, midpoint 128) to float32
   - Formula: `float_value = (uint8_value - 128.0) * scale`
2. **Transposition:** QNN-HTP layout `[head, dim, token]` → QNN-CPU layout `[head, token, dim]`
3. **RoPE permutation:** Interleave even/odd indices for rotary embeddings
   - QNN-HTP: `[0, 2, 4, ..., 126, 1, 3, 5, ..., 127]`
   - QNN-CPU: `[0, 1, 2, ..., 63, 64, 65, ..., 127]`
4. **Re-quantization:** Convert float32 back to Q8_32 for secondary engine (if quantized)

**KV Cache Conversion (in-memory):**

Uses `Engine::getKVHead()` and `Engine::setKVHead()` for direct tensor access:
```cpp
for each layer:
  for each head:
    p_engine->getKVHead(spec, layer, head, buffer, scales);
    s_engine->setKVHead(spec, layer, head, buffer, scales);
```
Parallelized across threads (hardware_concurrency * 2 / 3 threads).

**Use cases:**
- Prompt processing on HTP, generation on CPU (fallback)
- Different HTP performance modes for each phase
- Memory optimization (free prompt weights after prefill)

### 3.3 Speculative Decoding (SpecDecDialog)

**File:** `src/qualla/dialogs/spec-dec.cpp`

Speculative decoding uses a small "draft" model to predict tokens, then verifies them with the larger "target" model:

```
Standard:  [PROMPT] → gen1 → gen2 → gen3 → gen4 → gen5
SpecDec:   [PROMPT] → draft: d1 d2 d3 → target verifies [d1✓ d2✓ d3✗] → gen5
                                                        ↑ 3 tokens in 2 forward passes
```

**Algorithm:**

```
1. Process prompt on BOTH target and draft models (optionally in parallel)
2. Sample first token from target model
3. For each generation step:
   a. Draft model generates `draft_len` tokens sequentially
   b. Target model processes all draft tokens in ONE forward pass (batched)
   c. For each draft token:
      - Compute acceptance probability = prob_target(token) / prob_draft(token)
      - Accept with that probability
      - If rejected, resample from modified distribution:
        modified_prob(x) = max(prob_target(x) - prob_draft(x), 0) / sum
   d. Commit accepted tokens to KV cache
   e. If all tokens accepted, draft model continues from last token
   f. If rejected, draft model restarts from last accepted token
```

**Key implementation details:**

- **Parallel prompt processing:** When `spec-dec.parallel = true`, both engines process the prompt in separate threads
- **Batched target inference:** `engine.process(tokens_to_target, attention_map, logits, true)` — processes all draft tokens at once
- **Attention map:** Tracks which tokens the target should attend to (supports sparse attention)
- **Gumbel sampling:** Uses Gumbel-max trick for correct sampling from modified distributions
- **Acceptance tracking:** `_accepted_counts[n_accepted]` tracks how often 0, 1, 2, ... tokens are accepted

**Configuration:**
```json
{
    "spec-dec": {
        "draft-len": 3,    // Number of draft tokens to generate
        "parallel": false  // Process prompt on both engines in parallel
    }
}
```

**Performance:** Achieves 2-3x speedup when draft model acceptance rate is >50%.

### 3.4 Multi-Stream Generation (MultiStreamDialog)

**File:** `src/qualla/dialogs/multistream.cpp`

Generates multiple independent text outputs from a single prompt:

```
Prompt: "Continue the story:"
  → Stream 1: "The cat sat on the mat..."
  → Stream 2: "The dog ran through the park..."
  → Stream 3: "The bird flew over the mountain..."
```

**Algorithm:**

1. Process prompt on primary engine
2. Get top-K tokens from logits (using `getTopK()`)
3. For each top-K token, start a new stream
4. Process all streams in parallel (batched):
   - Each stream sends its last token
   - Engine processes all tokens in one forward pass
   - Each stream gets its own logits slice
5. For each stream, sample independently from its logits
6. Remove streams that hit EOS
7. Repeat until all streams complete

**Configuration:**
```json
{
    "multi-stream": {
        "n-streams": 5,        // Number of outputs to generate
        "p-threshold": 0.0     // Probability threshold for stream selection
    }
}
```

**Use cases:**
- Creative writing (multiple story continuations)
- Question answering (multiple candidate answers)
- A/B testing different model outputs

### 3.5 Self-Speculative Decoding (SelfSpecDecDialog / SSD-Q1)

**File:** `src/qualla/dialogs/ssd-q1.cpp`

Self-speculative decoding (SSD) is a novel approach that eliminates the need for a separate draft model. Instead, it uses a **draft tree** with forecast tokens:

```
Standard:  [PROMPT] → gen1 → gen2 → gen3 → gen4 → gen5
SSD:       [PROMPT][FORECAST_0][FORECAST_1]... → build tree → verify → accept
```

**Key innovation:** Uses special "forecast" tokens (vocab_index, vocab_index+1, ...) as draft tokens, then verifies them against the actual model output.

**Architecture:**

```
Draft Tree (example with branches=[3, 2]):

                    root
                   / | \  \
                  A  B  C  D   (level 1: 4 nodes)
                 /|\ /|\       (level 2: 6 nodes)
                A B C A B C

Each node has _draft forecast tokens attached.
Total draft nodes = sum of all tree nodes.
```

**Algorithm:**

```
1. Load pre-computed KV prefix (forecast prefix)
2. Process prompt
3. Sample root token from logits
4. Build draft tree:
   a. For each level of the tree, sample tokens using top-K
   b. Skip repeating tokens (back-to-back same token)
   c. Attach forecast tokens to each node
5. Verify draft tree:
   a. For each node (in tree order):
      - Check if parent was accepted AND draft token matches verified token
      - If yes, accept this node
      - If no, stop this branch
6. Commit accepted tokens to KV cache
7. Build new draft tree from last accepted token
8. Repeat
```

**Branch Configuration:**
```json
{
    "ssd": {
        "ssd-version": 0,
        "branches": [[3], [2]],     // Branching factor per level
        "forecast-prefix": 100,     // Number of forecast tokens
        "forecast-token-offset": 32000,  // Starting token ID for forecasts
        "prefix-quant": 0,          // Quantized prefix tokens
        "n-streams": 1,             // Number of parallel streams
        "p-threshold": 0.0          // Probability threshold
    }
}
```

**Multi-stream SSD:** When `n-streams > 1`, uses tiled attention masks to process all streams in a single forward pass:

```
tiled_attention_mask =
┌──────────────────────────────────┐
│ Stream 1: [prompt][tree1][forecast]│
│ Stream 2: [prompt][tree2][forecast]│
│ Stream 3: [prompt][tree3][forecast]│
└──────────────────────────────────┘
```

**Key features:**
- No separate draft model needed (saves memory)
- Branch-based tree structure for better coverage
- Forecast tokens as speculative candidates
- Supports both token and embedding input
- Supports pausing/resuming generation
- Supports multi-stream parallel generation

### 3.6 LoRA (Low-Rank Adaptation)

**File:** `src/qualla/LoraConfig.cpp`

LoRA support in QNN allows running adapter weights without retraining the base model:

**LoRA Versions:**

| Version | Type | Description |
|---------|------|-------------|
| 0 | DISABLE | LoRA disabled |
| 1 | INPUT_WEIGHT_ENABLE | Runtime LoRA weight injection (QNN v1) |
| 2 | ADAPTER_WEIGHT_ENABLE | DLC-based LoRA adapters (QNN v2) |

**LoRA Adapter Configuration:**
```json
{
    "lora": [
        {
            "adapter-name": "my-adapter",
            "alpha-tensor-name": "lora_alpha",
            "alpha-tensor-value": [0.5],
            "alphas": ["alpha_1", "alpha_2"],
            "bin-sections": ["record://lora_adapter.bin"],
            "metadata-dlc": "metadata.dlc"
        }
    ],
    "group": [
        {
            "name": "my-group",
            "quant-bin-sections": ["record://quant_bin.bin"],
            "members": ["adapter1", "adapter2"]
        }
    ]
}
```

**Key features:**
- Multiple adapters per model
- Alpha scaling for adapter strength
- Grouped LoRA for quantized adapters
- DLC record support (`record://` scheme)
- ResourceManager-based file resolution
- Per-adapter alpha values
- Lazy LoRA loading (load only when needed)

**LoRA Application Flow:**
1. Configure adapters in Genie config
2. QNN runtime loads base model + LoRA adapters
3. At runtime, call `LoraConfig::updateAppliedAdapterName(name)` to switch adapters
4. QNN runtime applies LoRA weights to the appropriate layers
5. Inference proceeds with the active adapter

### 3.7 Injective Connector (Tensor Sequencing)

**File:** `src/qualla/adaptors/InjectiveConnector.cpp`

The Injective Connector handles tensor sequencing between producer and consumer nodes in a pipeline:

```
Producer Node → TensorSequence → Consumer Node
     (outputs)      (buffers &      (inputs)
                   requantizes)
```

**Key components:**

**TemporalTensor:**
```cpp
struct TemporalTensor {
    Tensor tensor;   // The actual tensor data
    uint32_t startIndex;  // Start index in sequence
    uint32_t endIndex;    // End index (exclusive)
};
```

**TensorSequence:**
- Queue of TemporalTensor segments
- Consumes segments in order
- Handles zero-padding for gaps
- Handles requantization between producer/consumer

**InjectiveProducer:**
- Collects output tensors from a node
- Adds them to TensorSequence with timestamps
- Tracks sequence index

**InjectiveConsumer:**
- Consumes tensors from TensorSequence
- Handles zero-padding for missing segments
- Handles requantization (different quantization params)
- Synchronizes with producer via sequence tracker

**Use cases:**
- Connecting auto-regressive nodes (output of layer N → input of layer N+1)
- Handling variable-length sequences
- Cross-backend tensor transfer (HTP → CPU)

---

## 4. JNI Bridge Integration Strategy

### 4.1 Mapping QNN/Genie to DaexLlama

| QNN/Genie Concept | DaexLlama Equivalent | JNI Mapping |
|-------------------|---------------------|-------------|
| `Env` | `DaexLlama` instance | `DaexLlama::init()` |
| `Context` | Model metadata | `DaexLlama::loadModel()` |
| `Engine` | llama.cpp context | `DaexLlama::createEngine()` |
| `Dialog` | Inference mode | `DaexLlama::setDialogType()` |
| `Sampler` | Sampling config | `DaexLlama::setSampler()` |
| `Tokenizer` | llama.cpp tokenizer | `DaexLlama::setTokenizer()` |
| `KV Cache` | KV cache tensors | `DaexLlama::updateKVCache()` |
| `QNN Runtime` | HTP backend | `DaexLlama::initBackend()` |

### 4.2 JNI Function Signatures

Based on the existing DaexLlama interface, the QNN bridge would expose:

```java
// Backend initialization
public native boolean initQnnBackend(String device, String perfMode);

// Model loading
public native long loadQnnModel(String modelPath, String configPath);

// Prompt processing
public native int[] processQnnPrompt(long ctx, int[] tokens);

// Token generation
public native int generateQnnToken(long ctx, int token);

// KV cache management
public native boolean saveQnnKV(long ctx, String path);
public native boolean loadQnnKV(long ctx, String path);

// Speculative decoding
public native boolean enableSpeculativeDecoding(long ctx, int draftLen);

// LoRA adapters
public native boolean applyQnnLora(long ctx, String adapterName);
```

### 4.3 Engine Selection Logic

The QNN SDK auto-detects available backends. The bridge should implement:

```
1. Check if QNN SDK is available (library exists)
2. Check if HTP is available on device
3. If HTP available → use QNN HTP backend
4. If HTP unavailable → fall back to QNN CPU backend
5. If QNN unavailable → fall back to llama.cpp CPU/GPU
```

**Auto-detection logic:**
```cpp
Engine::create("qnn-htp")  // Try HTP first
    ↓ (fails)
Engine::create("qnn-cpu")  // Fall back to CPU
    ↓ (fails)
Engine::create("llama")    // Fall back to llama.cpp
```

### 4.4 Performance Considerations

**HTP vs CPU vs GPU on Android:**

| Backend | Throughput | Latency | Memory | Power |
|---------|-----------|---------|--------|-------|
| HTP | Highest | Lowest | Moderate | Best |
| CPU | Low | High | High | Worst |
| GPU | Medium | Medium | High | Medium |

**HTP advantages for LLMs:**
- Dedicated matrix multiply units
- Optimized for transformer workloads
- Low power consumption
- Supports INT8/FP16 quantization

**HTP limitations:**
- Requires model conversion (ONNX → DLC)
- Limited to supported operations
- Model size constraints
- First-run conversion overhead

### 4.5 Integration with ObjectBox

The existing ObjectBox persistence layer can store:
- Model metadata (context, vocab, architecture)
- KV cache snapshots (for checkpointing)
- LoRA adapter configurations
- Inference session state

**ObjectBox entity mapping:**
```kotlin
@Entity
class QnnModelConfig {
    var id: Long = 0
    var modelName: String = ""
    var modelPath: String = ""
    var htpEnabled: Boolean = false
    var quantization: String = "Q8_0"
    var contextSize: Int = 2048
    var loraAdapters: String = "[]"  // JSON array
}

@Entity
class KvCacheSnapshot {
    var id: Long = 0
    var sessionId: String = ""
    var tokenCount: Int = 0
    var dataPath: String = ""
    var timestamp: Long = 0
}
```

---

## 5. Key Findings & Recommendations

### 5.1 Critical Findings

1. **Genie is a complete inference framework** — Not just a wrapper around QNN, but a full LLM inference pipeline with dialogs, samplers, tokenizers, and KV cache management.

2. **Two-engine architecture is fundamental** — KV-sharing, speculative decoding, and multi-stream all rely on having multiple engines (primary/secondary) with different roles.

3. **KV cache format is custom** — Uses a binary format with magic number 0xC0DE, Q8 quantization, and requires dequantization/transposition for cross-backend transfer.

4. **Self-speculative decoding (SSD-Q1) eliminates draft models** — Uses forecast tokens and a branch-based tree structure instead of a separate small model. This is a significant advantage for mobile where memory is constrained.

5. **LoRA v2 uses DLC records** — LoRA adapters are stored as DLC (Qualcomm DLC) records, not raw binary files. The ResourceManager resolves `record://` paths.

6. **Injective Connector handles tensor sequencing** — Critical for multi-node pipelines where output of one node feeds input of another with different quantization or timing.

### 5.2 Recommendations for DaexLlama QNN Bridge

1. **Start with BasicDialog** — Implement standard autoregressive inference first, then add speculative decoding and KV-sharing.

2. **Use in-memory KV sharing** — Avoid file-based KV transfer (slow, complex). Use `Engine::getKVHead()`/`Engine::setKVHead()` for direct tensor access.

3. **Prioritize SSD-Q1 over SpecDecDialog** — Self-speculative decoding eliminates the need for a separate draft model, saving significant memory on mobile devices.

4. **Implement auto-detection** — Try QNN HTP → QNN CPU → llama.cpp fallback, matching the existing backend auto-detection in DaexLlama.

5. **Leverage existing samplers** — The QNN SDK samplers support temperature, top-K, top-P, repetition penalty, and Gumbel sampling — all features DaexLlama already supports.

6. **Use ObjectBox for KV snapshots** — Store KV cache snapshots for session resumption, using the existing ObjectBox persistence layer.

7. **Handle RoPE permutation** — When converting KV cache between backends, the rotary embedding permutation must be handled (interleaving even/odd indices).

8. **Support embedding input** — Both BasicDialog and SSD-Q1 support embedding-based input via `T2ECallback`, which can be used for custom token-to-embedding conversion.

### 5.3 Build Configuration

The Genie library is built with CMake:
```cmake
find_package(QNN REQUIRED CONFIG)
target_link_libraries(genie PRIVATE QNN::QNN)
target_include_directories(genie PRIVATE ${QNN_INCLUDE_DIRS})
```

Required QNN libraries:
- `libqnn.so` — QNN host API
- `libQnnHtp.so` — HTP backend
- `libQnnCpu.so` — CPU backend
- `libgenai.so` — GenAI library (Genie)

---

## Appendix A: File Reference Map

| File | Purpose |
|------|---------|
| `include/qnn/types.h` | Core QNN types (tensors, backends, attributes) |
| `include/qnn/backend/Provider.h` | Backend provider interface |
| `include/qnn/context/Context.h` | Context API |
| `include/qnn/graph/Graph.h` | Graph API |
| `include/qnn/ops/ops.h` | Operation definitions |
| `GenieGenAI/include/GenAI/Tensor.hpp` | Tensor class |
| `GenieGenAI/include/GenAI/TensorSequence.hpp` | Tensor sequence |
| `GenieGenAI/include/GenAI/Engine.hpp` | Engine class |
| `GenieGenAI/include/GenAI/Dialog.hpp` | Dialog base class |
| `GenieGenAI/include/GenAI/Sampler.hpp` | Sampler class |
| `GenieGenAI/include/GenAI/Tokenizer.hpp` | Tokenizer class |
| `GenieGenAI/include/GenAI/Pipeline.hpp` | Pipeline class |
| `Genie/src/qualla/dialogs/basic.cpp` | Basic autoregressive dialog |
| `Genie/src/qualla/dialogs/kv-share.cpp` | KV cache sharing dialog |
| `Genie/src/qualla/dialogs/spec-dec.cpp` | Speculative decoding dialog |
| `Genie/src/qualla/dialogs/multistream.cpp` | Multi-stream dialog |
| `Genie/src/qualla/dialogs/ssd-q1.cpp` | Self-speculative decoding dialog |
| `Genie/src/qualla/LoraConfig.cpp` | LoRA adapter configuration |
| `Genie/src/qualla/adaptors/InjectiveConnector.cpp` | Tensor sequencing connector |
| `llama-2-7b/config.yaml` | Llama-2-7b example config |
| `llama-3-3b/config.yaml` | Llama-3-3b example config |

## Appendix B: QNN SDK Version Information

- **SDK Version:** 2.46.0.260424
- **Platform:** Quokka/Qualla (Snapdragon 8 Gen 2/3)
- **HTP Version:** Hexagon Tensor Processor v75+
- **Quantization:** Q8_0 (8-bit unsigned, midpoint 128), Q8_32 (8-bit with 32-element blocks)
- **Supported Models:** LLaMA, LLaMA-2, LLaMA-3, Mistral, Phi, and custom models
- **Conversion Tool:** onnx2qnn (ONNX → QNN DLC)

/**
 * DaexLlama JNI Bridge
 * Fixed per JNI safety audit (2026-05-12)
 */

#include <android/log.h>
#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>
#include <map>
#include <memory>
#include <mutex>
#include <atomic>
#include <sstream>
#include <algorithm>
#include <cmath>
#include <unistd.h>

#include "llama.h"
#include "ggml.h"
#include "ggml-backend.h"
#include "common.h"
#include "sampling.h"
#include "chat.h"

#define LOG_TAG "DaexLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

// --------------------------------------------------------------------------
// Named constants (replaces magic numbers — Issue 11)
// --------------------------------------------------------------------------
static constexpr int DEFAULT_N_CTX = 8192;
static constexpr int DEFAULT_N_BATCH = 512;
static constexpr int DEFAULT_N_PREDICT = 1024;
static constexpr int CONTEXT_OVERFLOW_SAFETY = 4;

// --------------------------------------------------------------------------
// Context struct
// --------------------------------------------------------------------------

struct LlamaContext {
    int id;
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    llama_batch batch;
    common_chat_templates_ptr chat_templates;
    common_sampler* sampler = nullptr;

    // Conversation state
    std::vector<common_chat_msg> chat_msgs;
    llama_pos system_prompt_position = 0;
    llama_pos current_position = 0;
    llama_pos stop_generation_position = 0;

    // Generation state
    llama_pos gen_stop_position = 0;
    std::string cached_token_chars;
    std::ostringstream assistant_ss;
    std::atomic<bool> cancel_generation{false};  // Issue 6: atomic for thread safety

    // Config
    int n_ctx = DEFAULT_N_CTX;
    int n_batch = DEFAULT_N_BATCH;
    int n_threads = 4;
    float sampler_temp = 0.3f;
    int n_predict = DEFAULT_N_PREDICT;

    LlamaContext(int ctx_id) : id(ctx_id) {
        batch = {};
    }

    ~LlamaContext() {
        if (sampler) common_sampler_free(sampler);
        if (chat_templates) chat_templates.reset();
        // Issue 10: Guard batch.n_tokens > 0 to avoid freeing uninitialized batch
        if (batch.n_tokens > 0) llama_batch_free(batch);
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
    }
};

// --------------------------------------------------------------------------
// Global state
// --------------------------------------------------------------------------

static std::map<int, std::unique_ptr<LlamaContext>> g_contexts;
static std::mutex g_contexts_mutex;
static int g_next_context_id = 1;

// --------------------------------------------------------------------------
// Helpers
// --------------------------------------------------------------------------

static std::string join_vec(const std::vector<std::string>& values, const std::string& delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) str << delim;
    }
    return str.str();
}

static bool is_valid_utf8(const char* string) {
    if (!string) return true;
    const auto* bytes = (const unsigned char*)string;
    while (*bytes != 0x00) {
        int num;
        if ((*bytes & 0x80) == 0x00) num = 1;
        else if ((*bytes & 0xE0) == 0xC0) num = 2;
        else if ((*bytes & 0xF0) == 0xE0) num = 3;
        else if ((*bytes & 0xF8) == 0xF0) num = 4;
        else return false;

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes += 1;
        }
    }
    return true;
}

static LlamaContext* get_context(JNIEnv* env, jint ctx_id) {
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    auto it = g_contexts.find(ctx_id);
    if (it == g_contexts.end()) {
        LOGE("Context %d not found", ctx_id);
        return nullptr;
    }
    return it->second.get();
}

static std::string get_active_backends() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto* reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(name);
        }
    }
    return backends.empty() ? "CPU" : join_vec(backends, ",");
}

// --------------------------------------------------------------------------
// NPU Configuration helpers
// --------------------------------------------------------------------------

static void set_env_var(const char* name, const char* value) {
    setenv(name, value, 1);
    LOGI("NPU config: %s=%s", name, value);
}

// --------------------------------------------------------------------------
// JNI: nativeConfigureNPU()
// Configure Hexagon NPU backend parameters before init.
// Must be called before nativeInit().
// @param nDevices Number of NPU sessions (1 for <4B, 2 for 8B, 4 for 20B)
// @param nHvxThreads Number of HVX hardware threads (0 = all)
// @param verbose Verbosity level (0=off, 1=on)
// @return 0 on success, -1 if not configured
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jint JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeConfigureNPU(
        JNIEnv* env, jobject /*this*/, jint nDevices, jint nHvxThreads, jint verbose) {
    // Set Hexagon environment variables for llama.cpp backend
    char buf[16];
    snprintf(buf, sizeof(buf), "%d", nDevices);
    set_env_var("GGML_HEXAGON_NDEV", buf);

    snprintf(buf, sizeof(buf), "%d", nHvxThreads);
    set_env_var("GGML_HEXAGON_NHVX", buf);

    snprintf(buf, sizeof(buf), "%d", verbose);
    set_env_var("GGML_HEXAGON_VERBOSE", buf);

    // Also set hostbuf and profile to safe defaults
    set_env_var("GGML_HEXAGON_HOSTBUF", "1");
    set_env_var("GGML_HEXAGON_PROFILE", "0");

    LOGI("NPU configured: devices=%d, hvx_threads=%d, verbose=%d",
         nDevices, nHvxThreads, verbose);
    return 0;
}

// --------------------------------------------------------------------------
// JNI: nativeIsNpuAvailable()
// Check if any non-CPU backend (Hexagon/HTP, OpenCL, etc.) is registered.
// @return 1 if NPU/GPU backend available, 0 otherwise
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jint JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeIsNpuAvailable(
        JNIEnv* env, jobject /*this*/) {
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto* reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        // Check for known NPU/GPU backends
        if (name.find("Hexagon") != std::string::npos ||
            name.find("HTP") != std::string::npos ||
            name.find("OpenCL") != std::string::npos ||
            name.find("GPU") != std::string::npos) {
            LOGI("NPU/GPU backend detected: %s", name.c_str());
            return 1;
        }
    }
    LOGD("No NPU/GPU backend registered");
    return 0;
}

// --------------------------------------------------------------------------
// JNI: nativeInit()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeInit(
        JNIEnv* env, jobject /*this*/, jstring nativeLibDir) {
    llama_log_set([](enum ggml_log_level level, const char* text, void* /*user_data*/) {
        if (level == GGML_LOG_LEVEL_ERROR)
            LOGE("%s", text);
        else if (level == GGML_LOG_LEVEL_WARN)
            LOGW("%s", text);
        else if (level == GGML_LOG_LEVEL_INFO)
            LOGI("%s", text);
        else
            LOGD("%s", text);
    }, nullptr);

    const auto* path = env->GetStringUTFChars(nativeLibDir, nullptr);
    LOGI("Loading backends from: %s", path);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(nativeLibDir, path);

    llama_backend_init();
    LOGI("Backend init complete. Active backends: %s", get_active_backends().c_str());
}

// --------------------------------------------------------------------------
// JNI: nativeCreateContext()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jint JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeCreateContext(JNIEnv* env, jobject /*this*/) {
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    int id = g_next_context_id++;
    g_contexts[id] = std::make_unique<LlamaContext>(id);
    LOGI("Created context %d", id);
    return id;
}

// --------------------------------------------------------------------------
// JNI: nativeDestroyContext()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeDestroyContext(JNIEnv* env, jobject /*this*/, jint ctx_id) {
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    auto it = g_contexts.find(ctx_id);
    if (it != g_contexts.end()) {
        LOGI("Destroying context %d", ctx_id);
        g_contexts.erase(it);
    }
}

// --------------------------------------------------------------------------
// JNI: nativeLoadModel()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jint JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeLoadModel(
        JNIEnv* env, jobject /*this*/, jint ctx_id, jstring jmodel_path) {
    auto* ctx = get_context(env, ctx_id);
    if (!ctx) return -1;

    // Issue 12: Copy to std::string first to avoid use-after-release
    const auto* model_path_raw = env->GetStringUTFChars(jmodel_path, nullptr);
    std::string model_path(model_path_raw);
    env->ReleaseStringUTFChars(jmodel_path, model_path_raw);

    LOGI("Loading model for context %d: %s", ctx_id, model_path.c_str());

    llama_model_params mparams = llama_model_default_params();
    ctx->model = llama_model_load_from_file(model_path.c_str(), mparams);

    if (!ctx->model) {
        LOGE("Failed to load model from: %s", model_path.c_str());
        return -1;
    }

    LOGI("Model loaded: (%zu MB, %.1fB params)",
         llama_model_size(ctx->model) / 1024 / 1024,
         llama_model_n_params(ctx->model) / 1e9);
    return 0;
}

// --------------------------------------------------------------------------
// JNI: nativePrepareContext()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jint JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativePrepareContext(
        JNIEnv* env, jobject /*this*/, jint ctx_id) {
    auto* ctx = get_context(env, ctx_id);
    if (!ctx || !ctx->model) return -1;

    int n_threads = std::max(2, (int)sysconf(_SC_NPROCESSORS_ONLN) - 2);
    ctx->n_threads = n_threads;
    LOGI("Context %d: using %d threads", ctx_id, n_threads);

    llama_context_params cparams = llama_context_default_params();
    int trained_ctx = llama_model_n_ctx_train(ctx->model);
    ctx->n_ctx = std::min(ctx->n_ctx, trained_ctx > 0 ? trained_ctx : DEFAULT_N_CTX);
    cparams.n_ctx = ctx->n_ctx;
    cparams.n_batch = ctx->n_batch;
    cparams.n_ubatch = std::min(ctx->n_batch, ctx->n_ctx);
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;

    ctx->ctx = llama_init_from_model(ctx->model, cparams);
    if (!ctx->ctx) {
        LOGE("Failed to create llama_context for context %d", ctx_id);
        return -1;
    }

    // Issue 8: Free existing batch before re-init to prevent memory leak
    if (ctx->batch.n_tokens > 0) {
        llama_batch_free(ctx->batch);
    }
    ctx->batch = llama_batch_init(ctx->n_batch, 0, 1);
    ctx->chat_templates = common_chat_templates_init(ctx->model, "");
    if (!ctx->chat_templates) {
        LOGW("Context %d: chat templates init failed (continuing without)", ctx_id);
    }

    common_params_sampling sparams;
    sparams.temp = ctx->sampler_temp;
    ctx->sampler = common_sampler_init(ctx->model, sparams);
    if (!ctx->sampler) {
        LOGE("Context %d: failed to initialize sampler (invalid model vocab?)", ctx_id);
        return -1;
    }

    LOGI("Context %d prepared (ctx=%d, batch=%d, threads=%d)",
         ctx_id, ctx->n_ctx, ctx->n_batch, ctx->n_threads);
    return 0;
}

// --------------------------------------------------------------------------
// JNI: nativeSetConfig()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeSetConfig(
        JNIEnv* env, jobject /*this*/, jint ctx_id,
        jint n_ctx, jint n_batch, jfloat temp, jint n_predict) {
    auto* ctx = get_context(env, ctx_id);
    if (!ctx) return;
    ctx->n_ctx = n_ctx;
    ctx->n_batch = n_batch;
    ctx->sampler_temp = temp;
    ctx->n_predict = n_predict;
}

static int decode_tokens_batched(LlamaContext* ctx, const llama_tokens& tokens,
                                  llama_pos start_pos, bool compute_last_logit = false) {
    for (int i = 0; i < (int)tokens.size(); i += ctx->n_batch) {
        int cur_batch_size = std::min((int)tokens.size() - i, ctx->n_batch);

        if (start_pos + i + cur_batch_size >= ctx->n_ctx - CONTEXT_OVERFLOW_SAFETY) {
            LOGW("Context %d: shifting context (overflow at %d)", ctx->id,
                 (int)(start_pos + i + cur_batch_size));
            int n_discard = (ctx->current_position - ctx->system_prompt_position) / 2;
            if (n_discard > 0) {
                llama_memory_seq_rm(llama_get_memory(ctx->ctx), 0, ctx->system_prompt_position,
                                    ctx->system_prompt_position + n_discard);
                llama_memory_seq_add(llama_get_memory(ctx->ctx), 0, ctx->system_prompt_position + n_discard,
                                     ctx->current_position, -n_discard);
                ctx->current_position -= n_discard;
            }
        }

        common_batch_clear(ctx->batch);
        for (int j = 0; j < cur_batch_size; j++) {
            llama_token tid = tokens[i + j];
            llama_pos pos = start_pos + i + j;
            bool want_logit = compute_last_logit && (i + j == (int)tokens.size() - 1);
            common_batch_add(ctx->batch, tid, pos, {0}, want_logit);
        }

        if (llama_decode(ctx->ctx, ctx->batch) != 0) {
            LOGE("Context %d: llama_decode failed", ctx->id);
            return 1;
        }
    }
    return 0;
}

// --------------------------------------------------------------------------
// chat_add_and_format helper
// --------------------------------------------------------------------------

static std::string chat_add_and_format(LlamaContext* ctx,
                                        const std::string& role,
                                        const std::string& content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;

    // Issue 4: Null check for chat_templates
    bool has_explicit = ctx->chat_templates ? common_chat_templates_was_explicit(ctx->chat_templates.get()) : false;
    // add_ass=false: don't add assistant turn — we only format the new message
    auto formatted = common_chat_format_single(
        ctx->chat_templates.get(), ctx->chat_msgs, new_msg, false, false);

    ctx->chat_msgs.push_back(new_msg);
    LOGI("Context %d: added %s message", ctx->id, role.c_str());
    return formatted;
}

// --------------------------------------------------------------------------
// JNI: nativeProcessSystemPrompt()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jint JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeProcessSystemPrompt(
        JNIEnv* env, jobject /*this*/, jint ctx_id, jstring jsystem_prompt) {
    auto* ctx = get_context(env, ctx_id);
    if (!ctx) return -1;

    ctx->chat_msgs.clear();
    ctx->system_prompt_position = 0;
    ctx->current_position = 0;
    ctx->cached_token_chars.clear();
    ctx->assistant_ss.str("");
    ctx->cancel_generation.store(false);  // Issue 6: atomic store
    // Issue 3: Null check for ctx->ctx before calling llama_get_memory
    if (ctx->ctx) {
        llama_memory_clear(llama_get_memory(ctx->ctx), false);
    }

    // Issue 12: Copy to std::string first to avoid use-after-release
    const auto* sys_prompt_raw = env->GetStringUTFChars(jsystem_prompt, nullptr);
    std::string sys_prompt(sys_prompt_raw);
    env->ReleaseStringUTFChars(jsystem_prompt, sys_prompt_raw);

    // Issue 4: Null check for chat_templates in nativeProcessSystemPrompt too
    bool has_explicit = ctx->chat_templates ? common_chat_templates_was_explicit(ctx->chat_templates.get()) : false;
    if (has_explicit) {
        sys_prompt = chat_add_and_format(ctx, "system", sys_prompt);
    }

    // Tokenize (formatted output already has BOS/EOS from chat template)
    auto sys_tokens = common_tokenize(ctx->ctx, sys_prompt, false, false);

    if (decode_tokens_batched(ctx, sys_tokens, ctx->current_position) != 0) {
        LOGE("Context %d: system prompt decode failed", ctx->id);
        return 1;
    }

    ctx->system_prompt_position = ctx->current_position = (llama_pos)sys_tokens.size();
    LOGI("Context %d: system prompt processed (%zu tokens)", ctx->id, sys_tokens.size());
    return 0;
}

// --------------------------------------------------------------------------
// JNI: nativeProcessUserPrompt()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jint JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeProcessUserPrompt(
        JNIEnv* env, jobject /*this*/, jint ctx_id, jstring juser_prompt) {
    auto* ctx = get_context(env, ctx_id);
    if (!ctx) return -1;

    ctx->cached_token_chars.clear();
    ctx->assistant_ss.str("");
    ctx->cancel_generation.store(false);  // Issue 6: atomic store

    // Issue 12: Copy to std::string first to avoid use-after-release
    const auto* user_prompt_raw = env->GetStringUTFChars(juser_prompt, nullptr);
    std::string user_prompt(user_prompt_raw);
    env->ReleaseStringUTFChars(juser_prompt, user_prompt_raw);

    // Issue 4: Null check for chat_templates
    bool has_explicit = ctx->chat_templates ? common_chat_templates_was_explicit(ctx->chat_templates.get()) : false;
    if (has_explicit) {
        user_prompt = chat_add_and_format(ctx, "user", user_prompt);
    }

    auto user_tokens = common_tokenize(ctx->ctx, user_prompt, false, false);

    int max_tokens = ctx->n_ctx - CONTEXT_OVERFLOW_SAFETY;
    if ((int)user_tokens.size() > max_tokens) {
        user_tokens.resize(max_tokens);
        LOGW("Context %d: truncated user prompt to %d tokens", ctx->id, max_tokens);
    }

    if (decode_tokens_batched(ctx, user_tokens, ctx->current_position, true) != 0) {
        LOGE("Context %d: user prompt decode failed", ctx->id);
        return 1;
    }

    ctx->current_position += (llama_pos)user_tokens.size();
    ctx->gen_stop_position = ctx->current_position + (llama_pos)ctx->n_predict;
    LOGI("Context %d: user prompt processed (%zu tokens), generating up to %d tokens",
         ctx->id, user_tokens.size(), ctx->n_predict);
    return 0;
}

// --------------------------------------------------------------------------
// JNI: nativeGenerateNextToken()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeGenerateNextToken(
        JNIEnv* env, jobject /*this*/, jint ctx_id) {
    auto* ctx = get_context(env, ctx_id);
    if (!ctx) return nullptr;

    // Issue 7: Guard sampler null check
    if (!ctx->sampler) {
        LOGE("Context %d: sampler is null, cannot generate", ctx->id);
        return nullptr;
    }

    // Issue 6: atomic load
    if (ctx->cancel_generation.load() || ctx->current_position >= ctx->gen_stop_position) {
        LOGI("Context %d: generation stopped", ctx->id);
        return nullptr;
    }

    llama_token new_token = common_sampler_sample(ctx->sampler, ctx->ctx, -1);
    common_sampler_accept(ctx->sampler, new_token, true);

    common_batch_clear(ctx->batch);
    common_batch_add(ctx->batch, new_token, ctx->current_position, {0}, true);
    if (llama_decode(ctx->ctx, ctx->batch) != 0) {
        LOGE("Context %d: decode failed for generated token", ctx->id);
        return nullptr;
    }
    ctx->current_position++;

    if (llama_vocab_is_eog(llama_model_get_vocab(ctx->model), new_token)) {
        LOGI("Context %d: EOG token %d", ctx->id, new_token);
        common_chat_msg msg;
        msg.role = "assistant";
        msg.content = ctx->assistant_ss.str();
        ctx->chat_msgs.push_back(msg);
        return nullptr;
    }

    auto token_str = common_token_to_piece(ctx->ctx, new_token);
    ctx->cached_token_chars += token_str;

    jstring result = nullptr;
    if (is_valid_utf8(ctx->cached_token_chars.c_str())) {
        result = env->NewStringUTF(ctx->cached_token_chars.c_str());
        // Issue 13: Check for OOM exception after NewStringUTF
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("Context %d: OOM creating Java string", ctx->id);
            return nullptr;
        }
        ctx->assistant_ss << ctx->cached_token_chars;
        ctx->cached_token_chars.clear();
    } else {
        result = env->NewStringUTF("");
        // Issue 13: Check for OOM exception after NewStringUTF
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("Context %d: OOM creating empty Java string", ctx->id);
            return nullptr;
        }
    }
    return result;
}

// --------------------------------------------------------------------------
// JNI: nativeCancelGeneration()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeCancelGeneration(
        JNIEnv* env, jobject /*this*/, jint ctx_id) {
    auto* ctx = get_context(env, ctx_id);
    if (ctx) ctx->cancel_generation.store(true);  // Issue 6: atomic store
}

// --------------------------------------------------------------------------
// JNI: nativeResetConversation()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeResetConversation(
        JNIEnv* env, jobject /*this*/, jint ctx_id) {
    auto* ctx = get_context(env, ctx_id);
    if (!ctx) return;

    // Issue 3: Guard ctx->ctx null check — may not be initialized yet
    if (!ctx->ctx) {
        LOGW("Context %d: reset called before context prepared, skipping memory clear", ctx->id);
        // Still reset conversation state even if ctx is null
        ctx->chat_msgs.clear();
        ctx->system_prompt_position = 0;
        ctx->current_position = 0;
        ctx->cached_token_chars.clear();
        ctx->assistant_ss.str("");
        ctx->cancel_generation.store(false);
        return;
    }

    ctx->chat_msgs.clear();
    ctx->system_prompt_position = 0;
    ctx->current_position = 0;
    ctx->cached_token_chars.clear();
    ctx->assistant_ss.str("");
    ctx->cancel_generation.store(false);  // Issue 6: atomic store
    llama_memory_clear(llama_get_memory(ctx->ctx), false);
    LOGI("Context %d: conversation reset", ctx->id);
}

// --------------------------------------------------------------------------
// JNI: nativeSystemInfo()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeSystemInfo(
        JNIEnv* env, jobject /*this*/) {
    // Issue 13: Check for OOM exception after NewStringUTF
    const char* info = llama_print_system_info();
    jstring result = env->NewStringUTF(info);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("OOM creating system info Java string");
        return nullptr;
    }
    return result;
}

// --------------------------------------------------------------------------
// JNI: nativeActiveBackends()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeActiveBackends(
        JNIEnv* env, jobject /*this*/) {
    // Issue 13: Check for OOM exception after NewStringUTF
    std::string backends = get_active_backends();
    jstring result = env->NewStringUTF(backends.c_str());
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("OOM creating backends Java string");
        return nullptr;
    }
    return result;
}

// --------------------------------------------------------------------------
// JNI: nativeUnloadModel()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeUnloadModel(
        JNIEnv* env, jobject /*this*/, jint ctx_id) {
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    auto it = g_contexts.find(ctx_id);
    if (it != g_contexts.end()) {
        LOGI("Unloading model from context %d", ctx_id);
        // Issue 1: erase from map instead of reset() to prevent double-free
        // when nativeDestroyContext is called afterward
        g_contexts.erase(it);
    }
}

// --------------------------------------------------------------------------
// JNI: nativeLoadEmbeddingModel()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jint JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeLoadEmbeddingModel(
        JNIEnv* env, jobject /*this*/, jint ctx_id, jstring jmodel_path) {
    auto* ctx = get_context(env, ctx_id);
    if (!ctx) return -1;

    // Issue 12: Copy to std::string first to avoid use-after-release
    const auto* model_path_raw = env->GetStringUTFChars(jmodel_path, nullptr);
    std::string model_path(model_path_raw);
    env->ReleaseStringUTFChars(jmodel_path, model_path_raw);

    LOGI("Loading embedding model for context %d: %s", ctx_id, model_path.c_str());

    llama_model_params mparams = llama_model_default_params();
    ctx->model = llama_model_load_from_file(model_path.c_str(), mparams);

    if (!ctx->model) {
        LOGE("Failed to load embedding model from: %s", model_path.c_str());
        return -1;
    }

    llama_context_params cparams = llama_context_default_params();
    int trained_ctx = llama_model_n_ctx_train(ctx->model);
    ctx->n_ctx = std::min(ctx->n_ctx, trained_ctx > 0 ? trained_ctx : 2048);
    cparams.n_ctx = ctx->n_ctx;
    cparams.n_batch = ctx->n_batch;
    cparams.n_ubatch = std::min(ctx->n_batch, ctx->n_ctx);
    cparams.n_threads = std::max(2, (int)sysconf(_SC_NPROCESSORS_ONLN) - 2);
    cparams.n_threads_batch = cparams.n_threads;
    cparams.embeddings = true;

    ctx->ctx = llama_init_from_model(ctx->model, cparams);
    if (!ctx->ctx) {
        LOGE("Failed to create embedding context for context %d", ctx_id);
        return -1;
    }

    // Issue 8: Free existing batch before re-init
    if (ctx->batch.n_tokens > 0) {
        llama_batch_free(ctx->batch);
    }
    ctx->batch = llama_batch_init(ctx->n_batch, 0, 1);
    LOGI("Context %d: embedding model loaded (ctx=%d)", ctx_id, ctx->n_ctx);
    return 0;
}

// --------------------------------------------------------------------------
// JNI: nativeGetEmbedding()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeGetEmbedding(
        JNIEnv* env, jobject /*this*/, jint ctx_id, jstring jtext) {
    // Issue 5: Add ctx->model to null check
    auto* ctx = get_context(env, ctx_id);
    if (!ctx || !ctx->ctx || !ctx->model) return nullptr;

    const auto* text = env->GetStringUTFChars(jtext, nullptr);
    auto tokens = common_tokenize(ctx->ctx, std::string(text), false, false);
    if (tokens.empty()) {
        env->ReleaseStringUTFChars(jtext, text);
        return env->NewFloatArray(0);
    }

    if ((int)tokens.size() > ctx->n_batch) {
        tokens.resize(ctx->n_batch);
    }

    common_batch_clear(ctx->batch);
    for (int i = 0; i < (int)tokens.size(); i++) {
        common_batch_add(ctx->batch, tokens[i], (llama_pos)i, {0}, false);
    }

    if (llama_decode(ctx->ctx, ctx->batch) != 0) {
        LOGE("Context %d: embedding decode failed", ctx_id);
        env->ReleaseStringUTFChars(jtext, text);
        return nullptr;
    }

    const float* embedding = llama_get_embeddings_seq(ctx->ctx, 0);
    if (!embedding) {
        LOGE("Context %d: no embeddings available", ctx_id);
        env->ReleaseStringUTFChars(jtext, text);
        return nullptr;
    }

    int n_embd = llama_model_n_embd(ctx->model);
    jfloatArray result = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(result, 0, n_embd, embedding);

    env->ReleaseStringUTFChars(jtext, text);
    return result;
}

// --------------------------------------------------------------------------
// JNI: nativeShutdown()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeShutdown(
        JNIEnv* env, jobject /*this*/) {
    {
        std::lock_guard<std::mutex> lock(g_contexts_mutex);
        g_contexts.clear();
    }
    llama_backend_free();
    LOGI("DaexLlama shutdown complete");
}

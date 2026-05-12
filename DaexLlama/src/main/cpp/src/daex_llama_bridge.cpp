/**
 * DaexLlama JNI Bridge
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
    bool cancel_generation = false;

    // Config
    int n_ctx = 8192;
    int n_batch = 512;
    int n_threads = 4;
    float sampler_temp = 0.3f;
    int n_predict = 1024;

    LlamaContext(int ctx_id) : id(ctx_id) {
        batch = {};
    }

    ~LlamaContext() {
        if (sampler) common_sampler_free(sampler);
        if (chat_templates) chat_templates.reset();
        llama_batch_free(batch);
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

    const auto* model_path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGI("Loading model for context %d: %s", ctx_id, model_path);

    llama_model_params mparams = llama_model_default_params();
    ctx->model = llama_model_load_from_file(model_path, mparams);
    env->ReleaseStringUTFChars(jmodel_path, model_path);

    if (!ctx->model) {
        LOGE("Failed to load model from: %s", model_path);
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
    ctx->n_ctx = std::min(ctx->n_ctx, trained_ctx > 0 ? trained_ctx : 8192);
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

    ctx->batch = llama_batch_init(ctx->n_batch, 0, 1);
    ctx->chat_templates = common_chat_templates_init(ctx->model, "");

    common_params_sampling sparams;
    sparams.temp = ctx->sampler_temp;
    ctx->sampler = common_sampler_init(ctx->model, sparams);

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

        if (start_pos + i + cur_batch_size >= ctx->n_ctx - 4) {
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

static std::string chat_add_and_format(LlamaContext* ctx,
                                        const std::string& role,
                                        const std::string& content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;

    auto formatted = common_chat_format_single(
        ctx->chat_templates.get(), ctx->chat_msgs, new_msg, role == "user", true);

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
    ctx->cancel_generation = false;
    llama_memory_clear(llama_get_memory(ctx->ctx), false);

    const auto* sys_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    std::string formatted = std::string(sys_prompt);

    bool has_explicit = common_chat_templates_was_explicit(ctx->chat_templates.get());
    if (has_explicit) {
        formatted = chat_add_and_format(ctx, "system", sys_prompt);
    }
    env->ReleaseStringUTFChars(jsystem_prompt, sys_prompt);

    auto sys_tokens = common_tokenize(ctx->ctx, formatted, has_explicit, has_explicit);

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
    ctx->cancel_generation = false;

    const auto* user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    std::string formatted = std::string(user_prompt);

    bool has_explicit = common_chat_templates_was_explicit(ctx->chat_templates.get());
    if (has_explicit) {
        formatted = chat_add_and_format(ctx, "user", user_prompt);
    }
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    auto user_tokens = common_tokenize(ctx->ctx, formatted, has_explicit, has_explicit);

    int max_tokens = ctx->n_ctx - 4;
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

    if (ctx->cancel_generation || ctx->current_position >= ctx->gen_stop_position) {
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
        ctx->assistant_ss << ctx->cached_token_chars;
        ctx->cached_token_chars.clear();
    } else {
        result = env->NewStringUTF("");
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
    if (ctx) ctx->cancel_generation = true;
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

    ctx->chat_msgs.clear();
    ctx->system_prompt_position = 0;
    ctx->current_position = 0;
    ctx->cached_token_chars.clear();
    ctx->assistant_ss.str("");
    ctx->cancel_generation = false;
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
    return env->NewStringUTF(llama_print_system_info());
}

// --------------------------------------------------------------------------
// JNI: nativeActiveBackends()
// --------------------------------------------------------------------------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_daex_llama_internal_DaexLlamaEngineImpl_nativeActiveBackends(
        JNIEnv* env, jobject /*this*/) {
    return env->NewStringUTF(get_active_backends().c_str());
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
        it->second.reset();
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

    const auto* model_path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGI("Loading embedding model for context %d: %s", ctx_id, model_path);

    llama_model_params mparams = llama_model_default_params();
    ctx->model = llama_model_load_from_file(model_path, mparams);
    env->ReleaseStringUTFChars(jmodel_path, model_path);

    if (!ctx->model) {
        LOGE("Failed to load embedding model from: %s", model_path);
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
    auto* ctx = get_context(env, ctx_id);
    if (!ctx || !ctx->ctx) return nullptr;

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

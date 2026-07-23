// ─────────────────────────────────────────────────────────────
// justsaid_llm_jni.cpp — llama.cpp JNI bridge. Llama symbols ONLY
// (AGENTS.md §3.1); whisper lives in justsaid_whisper_jni.cpp.
//
// Contract (AGENTS.md §3, Constitution N1–N5, G4):
//   nativeInit     — load model + context ONCE, return opaque handle (0 on failure).
//                    Hardware-aware: tries the requested n_gpu_layers offload first,
//                    falls back to CPU-only if that load fails.
//   nativeGenerate — reuse the context across the whole summary, low-temp
//                    sampling, stop on EOS ("" on error)
//   nativeFree     — null-safe, idempotent; free order context → model
// No abort()/exit(). No prompt/summary content logged in release (NDEBUG).
// ─────────────────────────────────────────────────────────────

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <mutex>
#include <new>
#include <string>
#include <unordered_set>
#include <vector>

#include "llama.h"

#define JS_TAG "justsaid_native"
#ifndef NDEBUG
#define JS_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, JS_TAG, __VA_ARGS__)
#else
#define JS_LOGD(...) ((void)0)
#endif
#define JS_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, JS_TAG, __VA_ARGS__)

namespace {

// Working buffers are allocated once here and reused for every generate call
// (N2: zero per-inference allocation of the big buffers).
struct JsLlmContext {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    std::vector<llama_token> tokens;  // sized n_ctx at init
    std::string out;                  // reused output buffer
    int n_ctx = 0;
    int n_batch = 0;
    std::mutex infer_mutex;  // one generation at a time per context
};

// Live-handle registry so nativeFree is idempotent even with a stale handle (N3).
std::mutex g_handles_mutex;
std::unordered_set<jlong> g_live_handles;

// 2048 (not 4096): mid/low-end phones like Galaxy A14 (~3.6 GB RAM) OOM-kill the
// process during generation when KV cache + the ~1.8 GB Q4 3B weights peak together.
// Summaries of short calls fit comfortably; long transcripts still truncate cleanly
// via the existing "prompt does not fit context" error path.
constexpr int JS_N_CTX = 2048;
constexpr int JS_N_BATCH = 256;
// Fixed seed: deterministic output helps the verbatim-proof guardrail (G1/G4).
constexpr uint32_t JS_SEED = 42;

// llama/ggml log hook. Release builds stay silent (P2: model load lines are fine,
// but llama can echo eval details; keep it all debug-only).
void js_llm_log_cb(ggml_log_level level, const char *text, void * /*user*/) {
#ifndef NDEBUG
    __android_log_print(level >= GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_DEBUG,
                        JS_TAG, "%s", text);
#else
    (void) level;
    (void) text;
#endif
}

void ensure_backend_once() {
    static std::once_flag flag;
    std::call_once(flag, [] {
        llama_log_set(js_llm_log_cb, nullptr);
        llama_backend_init();
    });
}

bool is_live_handle(jlong handle) {
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    return g_live_handles.find(handle) != g_live_handles.end();
}

// NewStringUTF requires (modified) UTF-8; a malformed byte sequence can abort the
// VM under CheckJNI. Keep valid sequences up to 3 bytes (BMP); replace anything
// else — including 4-byte supplementary sequences, which modified UTF-8 does not
// share with standard UTF-8 — with '?'.
void sanitize_for_jni(std::string &s) {
    std::string clean;
    clean.reserve(s.size());
    size_t i = 0;
    while (i < s.size()) {
        const auto c = static_cast<unsigned char>(s[i]);
        size_t len = 0;
        if (c < 0x80 && c != 0x00) len = 1;
        else if ((c & 0xE0) == 0xC0) len = 2;
        else if ((c & 0xF0) == 0xE0) len = 3;

        bool ok = len > 0 && i + len <= s.size();
        for (size_t k = 1; ok && k < len; ++k) {
            ok = (static_cast<unsigned char>(s[i + k]) & 0xC0) == 0x80;
        }
        if (ok) {
            clean.append(s, i, len);
            i += len;
        } else {
            clean += '?';
            i += 1;
        }
    }
    s.swap(clean);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_justsaid_app_llm_LlmEngine_nativeInit(
        JNIEnv *env, jobject /*thiz*/, jstring model_path, jint threads, jint n_gpu_layers) {
    if (model_path == nullptr) return 0;

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;  // OOM; pending exception surfaces in Kotlin

    ensure_backend_once();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;

    // Hardware-aware init: attempt the requested offload, then retry CPU-only.
    // With GGML_VULKAN=OFF this compiles to a plain CPU load either way.
    llama_model *model = llama_model_load_from_file(path, mparams);
    if (model == nullptr && n_gpu_layers != 0) {
        JS_LOGD("nativeInit: gpu-offload load failed, retrying CPU-only");
        mparams.n_gpu_layers = 0;
        model = llama_model_load_from_file(path, mparams);
    }
    env->ReleaseStringUTFChars(model_path, path);

    if (model == nullptr) {
        JS_LOGE("nativeInit: llm model load failed");
        return 0;
    }

    const int n_threads = threads > 0 ? threads : 4;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = JS_N_CTX;
    cparams.n_batch = JS_N_BATCH;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;

    llama_context *ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        JS_LOGE("nativeInit: llm context init failed");
        llama_model_free(model);
        return 0;
    }

    auto *js = new(std::nothrow) JsLlmContext();
    if (js == nullptr) {
        llama_free(ctx);
        llama_model_free(model);
        return 0;
    }
    js->model = model;
    js->ctx = ctx;
    js->n_ctx = static_cast<int>(llama_n_ctx(ctx));
    js->n_batch = static_cast<int>(llama_n_batch(ctx));
    js->tokens.resize(static_cast<size_t>(js->n_ctx));  // N2: sized once
    js->out.reserve(16 * 1024);

    const jlong handle = reinterpret_cast<jlong>(js);
    {
        std::lock_guard<std::mutex> lock(g_handles_mutex);
        g_live_handles.insert(handle);
    }
    JS_LOGD("nativeInit(llm): ok, threads=%d n_ctx=%d gpu_layers=%d (backend=%s)",
            n_threads, js->n_ctx, mparams.n_gpu_layers,
            mparams.n_gpu_layers != 0 && llama_supports_gpu_offload() ? "gpu" : "cpu");
    return handle;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_justsaid_app_llm_LlmEngine_nativeGenerate(
        JNIEnv *env, jobject /*thiz*/, jlong handle, jstring prompt,
        jint max_tokens, jfloat temp) {
    if (handle == 0 || prompt == nullptr || max_tokens <= 0) return env->NewStringUTF("");
    if (!is_live_handle(handle)) {
        JS_LOGE("nativeGenerate: unknown handle");
        return env->NewStringUTF("");
    }
    auto *js = reinterpret_cast<JsLlmContext *>(handle);
    std::lock_guard<std::mutex> infer_lock(js->infer_mutex);

    const char *prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    if (prompt_chars == nullptr) return env->NewStringUTF("");

    const llama_vocab *vocab = llama_model_get_vocab(js->model);

    // Tokenize into the preallocated buffer. parse_special=true so the chat
    // template's control tokens (<|start_header_id|>, ...) tokenize as specials.
    const int n_prompt = llama_tokenize(
            vocab, prompt_chars, static_cast<int32_t>(strlen(prompt_chars)),
            js->tokens.data(), static_cast<int32_t>(js->tokens.size()),
            /*add_special=*/true, /*parse_special=*/true);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    if (n_prompt <= 0 || n_prompt >= js->n_ctx - 1) {
        JS_LOGE("nativeGenerate: prompt does not fit context (n_prompt=%d, n_ctx=%d)",
                n_prompt, js->n_ctx);
        return env->NewStringUTF("");
    }

    // Fresh sequence per summary; the context itself is reused (N1).
    llama_memory_clear(llama_get_memory(js->ctx), true);

    // Prompt eval in n_batch slices.
    for (int i = 0; i < n_prompt; i += js->n_batch) {
        const int n = std::min(js->n_batch, n_prompt - i);
        if (llama_decode(js->ctx, llama_batch_get_one(js->tokens.data() + i, n)) != 0) {
            JS_LOGE("nativeGenerate: prompt decode failed at %d", i);
            return env->NewStringUTF("");
        }
    }

    // G4: temp <= 0.2 upstream; near-zero collapses to greedy for determinism.
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (smpl == nullptr) return env->NewStringUTF("");
    if (temp <= 0.05f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(JS_SEED));
    }

    std::string &out = js->out;
    out.clear();

    const int budget = std::min(static_cast<int>(max_tokens), js->n_ctx - n_prompt - 1);
    char piece[256];
    for (int i = 0; i < budget; ++i) {
        const llama_token tok = llama_sampler_sample(smpl, js->ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        const int n_piece = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, /*special=*/false);
        if (n_piece > 0) out.append(piece, static_cast<size_t>(n_piece));

        llama_token next = tok;
        if (llama_decode(js->ctx, llama_batch_get_one(&next, 1)) != 0) {
            JS_LOGE("nativeGenerate: token decode failed at step %d", i);
            llama_sampler_free(smpl);
            return env->NewStringUTF("");
        }
    }
    llama_sampler_free(smpl);

    sanitize_for_jni(out);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_justsaid_app_llm_LlmEngine_nativeFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return;
    {
        std::lock_guard<std::mutex> lock(g_handles_mutex);
        if (g_live_handles.erase(handle) == 0) {
            return;  // already freed (or never valid) — idempotent by contract (N3)
        }
    }
    auto *js = reinterpret_cast<JsLlmContext *>(handle);
    // N3 free order: context (owns per-sequence state) → model. The process-wide
    // ggml backend stays up because whisper shares it.
    llama_free(js->ctx);
    llama_model_free(js->model);
    delete js;
    JS_LOGD("nativeFree(llm): ok");
}

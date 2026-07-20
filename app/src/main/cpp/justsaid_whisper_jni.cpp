// ─────────────────────────────────────────────────────────────
// justsaid_whisper_jni.cpp — whisper.cpp JNI bridge. Whisper symbols ONLY
// (AGENTS.md §3.1); llama gets its own file in Phase 4.
//
// Contract (AGENTS.md §3, Constitution N1–N5):
//   nativeInit       — load model ONCE, return opaque handle (0 on failure)
//   nativeTranscribe — reuse the context, no model realloc, JSON out ("" on error)
//   nativeFree       — null-safe, idempotent
// No abort()/exit(). No transcript content logged in release (NDEBUG).
// ─────────────────────────────────────────────────────────────

#include <jni.h>
#include <android/log.h>

#include <cinttypes>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_set>

#include "whisper.h"

#define JS_TAG "justsaid_native"
#ifndef NDEBUG
#define JS_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, JS_TAG, __VA_ARGS__)
#else
#define JS_LOGD(...) ((void)0)
#endif
#define JS_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, JS_TAG, __VA_ARGS__)

namespace {

// One per loaded model. Buffers that would otherwise be allocated per chunk
// (params, language string, JSON output) live here and are reused (N2).
struct JsWhisperContext {
    whisper_context *ctx = nullptr;
    whisper_full_params params{};
    char lang[8] = "auto";
    std::string json;  // reused output buffer; capacity survives across chunks
};

// Live-handle registry so nativeFree is idempotent even if called twice with a
// stale handle (N3): a raw double whisper_free would be UB.
std::mutex g_handles_mutex;
std::unordered_set<jlong> g_live_handles;

// whisper/ggml log hook. Release builds stay silent (P2: no content in logs;
// whisper progress lines can include decoded text).
void js_log_cb(ggml_log_level level, const char *text, void * /*user*/) {
#ifndef NDEBUG
    __android_log_print(level >= GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_DEBUG,
                        JS_TAG, "%s", text);
#else
    (void) level;
    (void) text;
#endif
}

void append_json_escaped(std::string &out, const char *text) {
    for (const char *p = text; *p != '\0'; ++p) {
        const unsigned char c = static_cast<unsigned char>(*p);
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_justsaid_app_stt_WhisperEngine_nativeInit(
        JNIEnv *env, jobject /*thiz*/, jstring model_path, jint threads) {
    if (model_path == nullptr) return 0;

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;  // OOM; pending exception surfaces in Kotlin

    whisper_log_set(js_log_cb, nullptr);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // Vulkan stays off until the device matrix run

    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        JS_LOGE("nativeInit: model load failed");
        return 0;
    }

    auto *js = new(std::nothrow) JsWhisperContext();
    if (js == nullptr) {
        whisper_free(ctx);
        return 0;
    }
    js->ctx = ctx;
    js->json.reserve(16 * 1024);

    // Params allocated once here, reused every chunk (N2). Greedy + low temperature:
    // deterministic output for the guardrail substring checks downstream.
    js->params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    js->params.n_threads        = threads > 0 ? threads : 4;
    js->params.print_realtime   = false;
    js->params.print_progress   = false;
    js->params.print_timestamps = false;
    js->params.print_special    = false;
    js->params.no_timestamps    = false;
    js->params.suppress_blank   = true;
    js->params.temperature      = 0.0f;
    js->params.language         = js->lang;

    const jlong handle = reinterpret_cast<jlong>(js);
    {
        std::lock_guard<std::mutex> lock(g_handles_mutex);
        g_live_handles.insert(handle);
    }
    JS_LOGD("nativeInit: ok, threads=%d", js->params.n_threads);
    return handle;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_justsaid_app_stt_WhisperEngine_nativeTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong handle, jfloatArray pcm,
        jstring lang, jboolean translate) {
    if (handle == 0 || pcm == nullptr) return env->NewStringUTF("");
    {
        std::lock_guard<std::mutex> lock(g_handles_mutex);
        if (g_live_handles.find(handle) == g_live_handles.end()) {
            JS_LOGE("nativeTranscribe: unknown handle");
            return env->NewStringUTF("");
        }
    }
    auto *js = reinterpret_cast<JsWhisperContext *>(handle);

    // Copy the language hint into the context-owned buffer the params point at.
    js->lang[0] = '\0';
    if (lang != nullptr) {
        const char *lang_chars = env->GetStringUTFChars(lang, nullptr);
        if (lang_chars != nullptr) {
            strncpy(js->lang, lang_chars, sizeof(js->lang) - 1);
            js->lang[sizeof(js->lang) - 1] = '\0';
            env->ReleaseStringUTFChars(lang, lang_chars);
        }
    }
    if (js->lang[0] == '\0') {
        strncpy(js->lang, "auto", sizeof(js->lang));
    }
    js->params.translate = (translate == JNI_TRUE);

    const jsize n_samples = env->GetArrayLength(pcm);
    if (n_samples <= 0) return env->NewStringUTF("");

    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    if (samples == nullptr) return env->NewStringUTF("");

    const int rc = whisper_full(js->ctx, js->params, samples, static_cast<int>(n_samples));

    // Input only — JNI_ABORT skips the copy-back. Single release point covers
    // every path below (AGENTS.md §3.5).
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);

    if (rc != 0) {
        JS_LOGE("nativeTranscribe: whisper_full failed rc=%d", rc);
        return env->NewStringUTF("");
    }

    std::string &json = js->json;
    json.clear();
    json += "{\"segments\":[";
    const int n_segments = whisper_full_n_segments(js->ctx);
    for (int i = 0; i < n_segments; ++i) {
        // whisper timestamps are in centiseconds; the app speaks milliseconds.
        const int64_t t0_ms = whisper_full_get_segment_t0(js->ctx, i) * 10;
        const int64_t t1_ms = whisper_full_get_segment_t1(js->ctx, i) * 10;
        const char *text = whisper_full_get_segment_text(js->ctx, i);

        if (i > 0) json += ',';
        char head[80];
        snprintf(head, sizeof(head), "{\"t0\":%" PRId64 ",\"t1\":%" PRId64 ",\"text\":\"", t0_ms, t1_ms);
        json += head;
        append_json_escaped(json, text != nullptr ? text : "");
        json += "\"}";
    }
    json += "]}";

    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_justsaid_app_stt_WhisperEngine_nativeFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return;
    {
        std::lock_guard<std::mutex> lock(g_handles_mutex);
        if (g_live_handles.erase(handle) == 0) {
            return;  // already freed (or never valid) — idempotent by contract (N3)
        }
    }
    auto *js = reinterpret_cast<JsWhisperContext *>(handle);
    whisper_free(js->ctx);  // frees state then context internally
    delete js;
    JS_LOGD("nativeFree: ok");
}

# Phase 3 — NDK whisper.cpp Integration (On-Device STT)

> **Companion pivot override [CRITICAL]:** `docs/06-companion-backend-migration.md`
> task 3 supersedes every legacy reference in this document to `RecordedCall`,
> capture tiers, stereo channels, `LOCAL`/`REMOTE` speakers, or call lifecycle.
> Phase 3 consumes `RecordedSession`, decodes bounded mono microphone windows,
> and emits `Speaker.UNKNOWN` segments only.

> Prerequisite reading: `AGENTS.md` (§3 JNI rules), `docs/00-CONSTITUTION.md`
> (Native Memory, Speaker Attribution), Phase 2 (`RecordedCall`, `CallPipeline`).

---

## Phase Goal

Turn a captured `.wav` into a `Transcript` entirely on-device using whisper.cpp
over JNI:
1. Vendor whisper.cpp, build it with CMake for `arm64-v8a`/`armeabi-v7a`.
2. Expose a minimal, memory-safe JNI bridge (`init/infer/free`).
3. Kotlin `WhisperEngine` wrapper that chunks audio, applies VAD, reuses the
   native state across chunks, and (for Tier 1 stereo) transcribes L and R
   separately to attribute speakers.
4. Produce the shared `Transcript` type for Phase 4.

## Architecture & Targeted Dependencies

- **Owned:** `app/src/main/cpp/CMakeLists.txt`,
  `cpp/justsaid_whisper_jni.cpp`, `cpp/whisper.cpp/` (vendored),
  `stt/WhisperEngine.kt`, `stt/WhisperParams.kt`, `stt/VadGate.kt`,
  `stt/AudioDecoder.kt` (wav→float PCM, resample to 16k, channel split).
- **NDK/CMake:** compile whisper as a static lib, link into
  `libjustsaid_native.so`. Enable GGML threading; leave Vulkan OFF by default
  (toggle per device matrix later — flag in CMake). Use `-O3`, `-ffast-math`
  cautiously (test WER), NEON on.
- **Model:** path from `ModelPaths.sttModelFile()` (Phase 1). Language: from
  `SettingsRepo.sttLanguageLock` — `AUTO` (multilingual base) or `EN`
  (english-small model + `language="en"`).

### JNI contract (follow `AGENTS.md` §3 exactly)

```cpp
// justsaid_whisper_jni.cpp — symbols only for whisper
Java_..._WhisperEngine_nativeInit(env, obj, jstring modelPath, jint threads) -> jlong handle
Java_..._WhisperEngine_nativeTranscribe(env, obj, jlong handle, jfloatArray pcm16k,
        jstring lang, jboolean translate) -> jstring json   // segments w/ timestamps
Java_..._WhisperEngine_nativeFree(env, obj, jlong handle)
```
- `nativeInit`: `whisper_init_from_file_with_params` once; store
  `whisper_context*` as `jlong`. Preallocate reusable `whisper_full_params`.
  Threads = physical big cores (passed from Kotlin).
- `nativeTranscribe`: `GetFloatArrayElements` (release on ALL paths), run
  `whisper_full`, collect segments+timestamps into a small JSON string. **No
  model realloc.** Reuse buffers across calls where whisper allows.
- `nativeFree`: null-safe, idempotent; `whisper_free`.
- No transcript logging in release. No `abort()`. Return `0`/empty on error.

### Kotlin `WhisperEngine`

```kotlin
class WhisperEngine @Inject constructor(
    private val modelPaths: ModelPaths,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    external fun nativeInit(modelPath: String, threads: Int): Long
    external fun nativeTranscribe(handle: Long, pcm: FloatArray, lang: String, translate: Boolean): String
    external fun nativeFree(handle: Long)

    suspend fun transcribe(call: RecordedCall, language: String): JustSaidResult<Transcript>
    // - decode wav -> float PCM @16k (AudioDecoder)
    // - if call.tier==STEREO: split L/R, transcribe each, tag Speaker.LOCAL/REMOTE
    //   else: transcribe mono, tag Speaker.UNKNOWN
    // - VAD (VadGate) to drop silence; chunk ~30s with ~1s overlap
    // - merge segments ordered by startMs
    companion object { init { System.loadLibrary("justsaid_native") } }
}
```

- **VAD:** use whisper's built-in VAD params or a simple energy-based `VadGate`
  to skip silent windows (reduces inference time, prevents hallucination).
- **Chunking:** ~30s windows, ~1s overlap, dedupe overlap text. Keep a single
  native context for the whole call (N1).
- **Threading:** query `Runtime.getRuntime().availableProcessors()` and cap to
  physical big cores heuristically; run on `@DefaultDispatcher`.

## Data Handoff Boundaries

**Consumes:** `RecordedCall` (Phase 2), `ModelPaths` + `SettingsRepo`
(Phase 1).

**Produces:** `core` shared `Transcript` / `TranscriptSegment` / `Speaker`
(defined in Constitution). This phase should implement `CallPipeline` partially:
create `stt/SttPipeline.kt` that transcribes then forwards to Phase 4's
summarizer interface. For standalone testing before Phase 4, forward to a
`LlmSummarizer` interface with a fake that just returns the transcript, and
**still delete the wav in `finally`** (A1) — but note the canonical wav deletion
lives at the end of the STT→LLM pipeline (Phase 4 owns the final `process`).
To avoid double-ownership: **Phase 3 defines `SttPipeline` but does NOT delete
the wav**; the full `CallPipeline.process` (delete-in-finally) is assembled in
Phase 4. Phase 3's tests use a temp file they clean up themselves.

**Must NOT do:** LLM, DB, UI beyond none. Do not delete the wav (Phase 4 owns
the delete-in-finally to guarantee single deletion point).

## Acceptance / Tests

- `WhisperEngineFakeTest` (JVM, no `.so`): wrap the `external` calls behind a
  `NativeWhisperBridge` interface so a fake returns canned JSON; assert chunking,
  overlap dedupe, stereo speaker tagging, VAD skip.
- `AudioDecoderTest`: wav→float, resample to 16k, L/R split correctness.
- `WhisperJniSmokeTest` (androidTest, real `.so` + tiny model in fixtures):
  3-sec fixture → non-empty transcript; run `nativeFree` twice (idempotent).
- CLI bench per `TESTING.md` §A before wiring.

---

## 🤖 Worker-Agent Implementation Prompt (copy/paste)

**Recommended model: Claude Opus 4.8** (C++/JNI memory-safety is the highest-risk
work in the project; use the strongest model). Fallback: Opus 4.7.

> You are implementing **Phase 3** of the JustSaid Android app: on-device
> speech-to-text via **whisper.cpp over JNI**. Read `AGENTS.md` (especially §3
> JNI conventions), `docs/00-CONSTITUTION.md` (Native Memory N1–N5, Speaker
> Attribution S1–S2), and `docs/03-whisper-ndk.md`; obey them as hard rules.
> Phase 1 provides `ModelPaths`, `SettingsRepo`, dispatchers, `JustSaidResult`.
> Phase 2 provides `RecordedCall` (with `CaptureTier`) and the `CallPipeline`
> interface. The shared `Transcript`/`TranscriptSegment`/`Speaker` types are
> defined in the Constitution — implement them in `core/`.
>
> **Goal:** Convert `RecordedCall.wavFile` into a `Transcript` fully offline.
>
> **Native:** Vendor whisper.cpp under `app/src/main/cpp/whisper.cpp/` and write
> `app/src/main/cpp/CMakeLists.txt` producing `libjustsaid_native.so` for
> `arm64-v8a` and `armeabi-v7a` (NEON on, `-O3`, GGML threads; Vulkan OFF behind
> a CMake flag). Write `cpp/justsaid_whisper_jni.cpp` exposing EXACTLY three JNI
> functions matching `WhisperEngine`'s `external fun`s: `nativeInit(modelPath,
> threads)->long`, `nativeTranscribe(handle, float[] pcm16k, lang, translate)
> ->String(JSON segments+timestamps)`, `nativeFree(handle)`. Init the model ONCE
> and reuse the context for all chunks (N1); no per-chunk model realloc (N2);
> release every JNI array/string on all paths including errors; `nativeFree` is
> null/zero-safe and idempotent; no `abort()`; no transcript logging in release.
>
> **Kotlin:** Implement `stt/WhisperEngine.kt` (loads `justsaid_native`,
> declares the three `external fun`s, runs on `@DefaultDispatcher`). For
> testability, put the three natives behind a `NativeWhisperBridge` interface
> with a real impl delegating to the `external fun`s, so JVM tests can inject a
> fake. Implement `stt/AudioDecoder.kt` (WAV→FloatArray, resample to 16 kHz, and
> for `tier==STEREO` split L/R), `stt/VadGate.kt` (energy-based silence skip),
> and `stt/WhisperParams.kt`. In `WhisperEngine.transcribe`: chunk audio into
> ~30 s windows with ~1 s overlap (dedupe overlap text), keep ONE native context
> for the whole call, apply VAD, and tag speakers: STEREO → LOCAL (L) / REMOTE
> (R) transcribed separately; otherwise UNKNOWN. Read language from
> `SettingsRepo.sttLanguageLock` (AUTO→multilingual, EN→english model +
> `language="en"`). Thread count = physical big-core estimate.
>
> **Handoff:** Create `stt/SttPipeline.kt` that transcribes and forwards the
> `Transcript` to a `llm/LlmSummarizer` interface (define the interface stub here
> if Phase 4 not present). **Do NOT delete the wav** — Phase 4 owns the single
> delete-in-finally point. Return `JustSaidResult<Transcript>`.
>
> **Constraints:** No network, no DB, no UI. Follow all JNI memory rules.
> Provide tests: `WhisperEngineFakeTest` (JVM via `NativeWhisperBridge` fake:
> chunking, overlap dedupe, stereo tagging, VAD skip), `AudioDecoderTest`, and
> `WhisperJniSmokeTest` (androidTest with real `.so` + tiny fixture model:
> non-empty transcript; double `nativeFree` is safe). Document the CLI bench
> commands you used (see `TESTING.md` §A).
>
> **Done when:** `assembleDebug` builds the `.so`, `testDebugUnitTest` passes,
> and the smoke test transcribes a fixture on a device/emulator.

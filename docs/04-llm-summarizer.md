# Phase 4 — llama.cpp Summarizer & "Proof of Promise" Pipeline

> Prerequisite reading: `AGENTS.md` (§3), `docs/00-CONSTITUTION.md`
> (Summarization Guardrails G1–G4, Native Memory, Audio Lifecycle A1),
> Phase 3 (`Transcript`, `SttPipeline`, `NativeWhisperBridge` pattern).

---

## Phase Goal

Turn a `Transcript` into a validated `CallSummary` of promises/tasks, each with a
verbatim proof quote, using a 3B LLM via llama.cpp — and **own the single point
that deletes the raw `.wav`** (delete-in-finally). This phase assembles the full
`CallPipeline.process`.

## Why llama.cpp (not MediaPipe LLM Inference)

Both are viable, but llama.cpp shares the **ggml runtime already vendored for
whisper in Phase 3** — one C++ toolchain, one `.so`, smaller APK, consistent
quantization (GGUF Q4_K_M), and more reliable constrained/structured decoding
(low temp, stop tokens, optional grammar) than MediaPipe's `.task` path. It also
supports NNAPI/Vulkan offload. If a future device matrix shows MediaPipe wins on
a specific NPU, it can be added behind the same `LlmSummarizer` interface.

## Architecture & Targeted Dependencies

- **Owned:** `cpp/justsaid_llm_jni.cpp`, extend `CMakeLists.txt` to also build
  `llama.cpp/` into `libjustsaid_native.so`; `llm/LlmEngine.kt`,
  `llm/LlmSummarizer.kt` (impl of the interface Phase 3 referenced),
  `llm/Prompts.kt` (hardcoded system prompt), `summary/PromiseParser.kt`,
  `summary/HallucinationGuard.kt`, and the full `pipeline/CallPipelineImpl.kt`.
- **Model:** `ModelPaths.llmModelFile()` → `llama-3.2-3b-instruct-q4_k_m.gguf`.
- **Hardware-aware inference:** at init, detect capability and choose backend:
  try Vulkan/NNAPI offload of some layers (`n_gpu_layers`), fall back to CPU
  with big-core thread count. Expose the chosen backend in logs (debug only).

### JNI contract (mirror whisper's discipline, separate file) [CRITICAL]

```cpp
// justsaid_llm_jni.cpp — llama symbols ONLY
Java_..._LlmEngine_nativeInit(env, obj, jstring modelPath, jint threads, jint nGpuLayers) -> jlong
Java_..._LlmEngine_nativeGenerate(env, obj, jlong handle, jstring prompt,
        jint maxTokens, jfloat temp) -> jstring
Java_..._LlmEngine_nativeFree(env, obj, jlong handle)
```
- Init `llama_model` + `llama_context` once; reuse across the whole summary.
- Greedy/low-temp sampling, stop on EOS or a stop marker; cap `maxTokens`.
- Release all JNI refs on all paths; `nativeFree` null-safe/idempotent; free
  order state→context→backend/model. No content logging in release.

### The hardcoded system prompt (in `llm/Prompts.kt`)

The prompt (paraphrased; agent writes the exact string) must instruct:
- Parse a dual-channel transcript labeled `You:` (local) and `<Name>:` (remote)
  or `SPEAKER_?:` when unknown.
- Output ONLY lines in the format:
  `Task/Item [Quantity] (Proof: "verbatim quote from the speaker who agreed")`.
- Quantity `[..]` only if explicitly stated, else omit.
- If a task cannot be supported by a verbatim quote, **do not output it**.
- If information is ambiguous or attribution unknown, label `Unconfirmed`.
- Neutral, concise, no creative filler, no preamble, no closing remarks.
- If nothing actionable, output the single token `NONE`.

### Code-enforced guardrails (do not trust the model)

`summary/PromiseParser.kt`:
1. Parse each output line into `{task, quantity?, proofQuote, attributedTo}`.
2. `HallucinationGuard`: normalize (lowercase, collapse whitespace, strip
   punctuation edges) both `proofQuote` and the full transcript; **drop any item
   whose quote is not a substring** of the transcript (G1). Fuzzy allowance:
   optional ≤2 char Levenshtein slack on token joins, but default strict.
3. Map the quoting speaker → `Speaker`/`attributedTo`; `UNKNOWN` → `confirmed=
   false` and rendered "Unconfirmed".
4. Return `List<PromiseItem>` → build `CallSummary` (no audio, transcript kept).

### The full pipeline (this phase assembles it)

```kotlin
// pipeline/CallPipelineImpl.kt  (implements Phase 2's CallPipeline)
class CallPipelineImpl @Inject constructor(
  private val stt: SttPipeline,          // Phase 3
  private val summarizer: LlmSummarizer,  // this phase
  private val repo: SummaryRepo,          // Phase 5 (inject interface; stub ok pre-P5)
  @DefaultDispatcher private val d: CoroutineDispatcher,
) : CallPipeline {
  override suspend fun process(call: RecordedCall) = withContext(d) {
    try {
      val transcript = stt.transcribe(call)          // JustSaidResult<Transcript>
      val summary = summarizer.summarize(transcript)  // parse + guardrails
      repo.save(summary)                              // Phase 5
      // emit to UI state for the summary screen (Phase 5 observes)
    } finally {
      call.wavFile.delete()   // [CRITICAL A1] single deletion point, success or fail
    }
  }
}
```

## Data Handoff Boundaries

**Consumes:** `Transcript` (Phase 3), `ModelPaths` (Phase 1), `RecordedCall`
(Phase 2). References `SummaryRepo` (Phase 5) via interface — provide a fake
binding so Phase 4 runs standalone.

**Produces:** `CallSummary` (Constitution type) emitted to a shared
`SummaryEvents`/state so Phase 5's summary screen can show it and offer
Save/Send. Also **owns wav deletion**.

**Must NOT do:** define Room schema (Phase 5), build history/settings UI.

## Acceptance / Tests

- `PromiseParserTest` (JVM): well-formed lines parse; `[Qty]` optional; `NONE`
  → empty list.
- `HallucinationGuardTest` (JVM) [CRITICAL]: item with a quote NOT in transcript
  is dropped; whitespace/case differences still match; unknown attribution →
  "Unconfirmed".
- `LlmEngineFakeTest` (JVM via a `NativeLlmBridge` fake): summarize flow without
  `.so`.
- `CallPipelineWavDeletionTest` (JVM): wav deleted on success AND when STT/LLM
  throws (finally path).
- `LlmJniSmokeTest` (androidTest, real `.so` + small GGUF): generates
  format-conformant output for a fixture transcript.
- CLI bench per `TESTING.md` §A.3.

---

## 🤖 Worker-Agent Implementation Prompt (copy/paste)

**Recommended model: Claude Opus 4.8** (native JNI + strict guardrail logic +
prompt engineering; strongest model). Fallback: Opus 4.7. The pure Kotlin
`PromiseParser`/`HallucinationGuard` could be done by Sonnet 4.5 if split out.

> You are implementing **Phase 4** of the JustSaid Android app: the on-device
> **llama.cpp summarizer** and the "proof of promise" pipeline. Read `AGENTS.md`
> (§3 JNI), `docs/00-CONSTITUTION.md` (Guardrails G1–G4, Native Memory N1–N5,
> Audio Lifecycle A1), and `docs/04-llm-summarizer.md`; obey as hard rules.
> Phase 3 provides `Transcript`, `SttPipeline`, and the `NativeWhisperBridge`
> testing pattern. `ModelPaths`, dispatchers, `JustSaidResult`, `CallSummary`/
> `PromiseItem` (Constitution) already exist.
>
> **Goal:** Convert a `Transcript` into a validated `CallSummary` of promises,
> each with a **verbatim proof quote**, and assemble the full `CallPipeline`
> that deletes the raw `.wav` at a single delete-in-finally point.
>
> **Native:** Vendor llama.cpp under `app/src/main/cpp/llama.cpp/` and extend
> the existing `CMakeLists.txt` so BOTH whisper and llama build into the single
> `libjustsaid_native.so`. Write `cpp/justsaid_llm_jni.cpp` (llama symbols ONLY;
> never mix with whisper) exposing exactly: `nativeInit(modelPath, threads,
> nGpuLayers)->long`, `nativeGenerate(handle, prompt, maxTokens, temp)->String`,
> `nativeFree(handle)`. Load model+context ONCE and reuse; low-temp sampling
> (≤0.2) with EOS/stop handling; release all JNI refs on all paths; `nativeFree`
> idempotent/null-safe; free order state→context→model/backend; no content
> logging in release; no `abort()`. Implement **hardware-aware init**: attempt
> Vulkan/NNAPI layer offload (`n_gpu_layers`), fall back to CPU with a physical
> big-core thread count.
>
> **Kotlin:** `llm/LlmEngine.kt` (loads `justsaid_native`, `external fun`s
> behind a `NativeLlmBridge` interface for JVM testing), `llm/Prompts.kt` with
> the HARDCODED system prompt that outputs ONLY
> `Task/Item [Quantity] (Proof: "verbatim quote")` lines, omits unsupported
> tasks, labels ambiguous/unknown-speaker items `Unconfirmed`, quantity only if
> explicit, neutral/concise/no filler, and emits `NONE` when nothing actionable.
> `llm/LlmSummarizer.kt` implements the interface Phase 3 referenced.
>
> **Code-enforced guardrails [CRITICAL]:** `summary/PromiseParser.kt` parses the
> model output; `summary/HallucinationGuard.kt` normalizes (lowercase, collapse
> whitespace) the proof quote and full transcript and **drops any item whose
> quote is not a substring of the transcript** (default strict). Unknown
> attribution → `confirmed=false` ("Unconfirmed"). Build `CallSummary` (text
> only; keep transcript, never audio).
>
> **Assemble pipeline:** `pipeline/CallPipelineImpl.kt` implements Phase 2's
> `CallPipeline`: `try { transcript = stt.transcribe(call); summary =
> summarizer.summarize(transcript); repo.save(summary) } finally {
> call.wavFile.delete() }`. Inject `SummaryRepo` as an interface with a fake
> Hilt binding so this phase runs before Phase 5. Emit the `CallSummary` to a
> shared state/event for the summary screen. Replace Phase 2's `NoOpCallPipeline`
> binding with `CallPipelineImpl`.
>
> **Constraints:** No network, no UI beyond emitting summary state. Provide
> tests: `PromiseParserTest`, `HallucinationGuardTest` (quote-not-in-transcript
> dropped; case/whitespace-insensitive match; unknown→Unconfirmed),
> `LlmEngineFakeTest`, `CallPipelineWavDeletionTest` (wav deleted on success AND
> exception), and androidTest `LlmJniSmokeTest`. Document CLI bench commands
> (`TESTING.md` §A.3).
>
> **Done when:** `assembleDebug` builds a single `.so` with both engines,
> `testDebugUnitTest` passes (guardrail + deletion tests green), and the smoke
> test yields format-conformant output on device.

# 00 — The Constitution (Non-Negotiable Engineering Standards)

Every phase inherits these rules. A violation is a **high-priority bug**. This
document is referenced by all worker-agent prompts. Read with `AGENTS.md`.

---

## Privacy & Network

- **P1 [CRITICAL]** No network access except `data/download/ModelDownloader`
  (OkHttp → Hugging Face). No analytics, crash reporting, ads, GMS, Firebase.
- **P2 [CRITICAL]** No content (audio, transcript, summary, session label) is
  ever logged in release builds. Debug logs gated behind `BuildConfig.DEBUG`
  and must never include full transcript text.
- **P3** The app must pass an "airplane-mode" test: with wifi/data off (after
  models are present), every feature except downloading works fully.

## Audio Lifecycle  {#audio-lifecycle}

- **A1 [CRITICAL]** The raw `.wav` buffer lives only in the app's private cache
  (`context.cacheDir`/private files). It is **deleted immediately** after the
  summary pipeline completes — on **both success and failure** paths (use a
  `try/finally` around the STT→LLM pipeline). A synchronous stale-buffer sweep
  runs before a new session and on app startup; deletion failure is surfaced and
  retried, never ignored.
- **A2** No raw audio in Room, SharedPreferences, MediaStore, or backups.
  `android:allowBackup="false"`, and exclude cache via backup rules.
- **A3** WAV format: mono, 16 kHz, 16-bit PCM. Input is `MIC` only. The app must
  never claim to capture carrier-call output, remote audio, or separate speakers.

## Text Lifecycle

- **T1** Transcripts + summaries are retained **indefinitely by default** in the
  encrypted DB.
- **T2** Optional auto-cleanup setting (**OFF by default**) deletes text older
  than 30 days. Implemented as a DAO query filtered by `createdAt`.
- **T3** "Clear History" (settings) wipes all rows after a confirmation dialog.
- **T4** Export to PDF/Text is available per-summary and for full history.

## Native Memory  {#native-memory}

- **N1 [CRITICAL]** Model context is created once (`nativeInit`) and reused for
  all chunks of one capture session. Never reload the model per chunk/segment.
- **N2** Per-chunk inference must not allocate the large working buffers; size
  them at init from max-chunk assumptions. This is the practical form of the
  "zero runtime allocation" requirement (zero *per-chunk* heap growth).
- **N3** Every native handle is freed exactly once via `nativeFree`, which is
  null/zero-safe and idempotent. Free order: state → context → backend.
- **N4** Inference runs off the main thread. Thread count = physical big-core
  count, queried at init (default 4 if unknown).
- **N5** Implement VAD to skip silence; chunk long audio (~30s windows with a
  small overlap) to keep buffers stable and prevent hallucination loops.

## Speaker Attribution

- **S1 [CRITICAL]** All capture-session audio is single-channel microphone
  input. Every transcript segment is `UNKNOWN`; the model must never infer
  "You", a remote party, or a contact from microphone audio.
- **S2** Every extracted promise from a capture session is rendered
  **Unconfirmed** unless a future, separately specified capture method provides
  trustworthy attribution.

## Summarization Guardrails (enforced in CODE, not just the prompt)

- **G1 [CRITICAL]** After the LLM returns, `summary/PromiseParser` validates
  every item's `proof` quote is a **literal substring** of the transcript
  (normalized: lowercase, collapse whitespace). Items failing this are DROPPED.
- **G2** Neutral, concise tone. No creative filler. Ambiguous → omit or
  "Unconfirmed". Enforced by prompt AND by G1.
- **G3** Quantities only when explicitly stated; otherwise omit the `[Qty]`.
- **G4** Temperature ≤ 0.2 for the summarizer.

## UX & Accessibility

- **U1** Single-activity Compose. Every primary action is one tap.
- **U2** WCAG AA: contrast ≥ 4.5:1, base font ≥ 18sp (scales with system),
  touch targets ≥ 48dp, full TalkBack content descriptions.
- **U3** No modal traps except the unskippable first-run legal screen and the
  processing state after a user ends a capture session.
- **U4** No technical error codes shown to users; friendly messages + a retry.

## Error Handling & Results

- **E1** Layer boundaries return `core.JustSaidResult<T>` (sealed
  `Success`/`Failure(reason)`); do not throw across boundaries.
- **E2** The pipeline is resilient: STT failure still deletes the wav and shows
  a friendly "couldn't understand this recording" state.

## Shared Types (defined in Phase boundaries; do not redefine)

```kotlin
// core/JustSaidResult.kt
sealed interface JustSaidResult<out T> {
    data class Success<T>(val value: T) : JustSaidResult<T>
    data class Failure(val reason: String, val cause: Throwable? = null) : JustSaidResult<Nothing>
}

// Produced by Phase 3 (STT), consumed by Phase 4 (LLM)
data class TranscriptSegment(
    val speaker: Speaker,        // LOCAL, REMOTE, UNKNOWN
    val text: String,
    val startMs: Long,
    val endMs: Long
)
enum class Speaker { LOCAL, REMOTE, UNKNOWN }
data class Transcript(val segments: List<TranscriptSegment>) {
    fun plainText(): String  // "You: ...\nName: ..." rendering for the LLM + substring checks
}

// Produced by Phase 4 (LLM/parser), consumed by Phase 5 (history/SMS/export)
data class PromiseItem(
    val task: String,
    val quantity: String?,       // null if not explicitly stated
    val proofQuote: String,      // verbatim substring of transcript (G1)
    val attributedTo: Speaker,   // UNKNOWN => rendered "Unconfirmed"
    val confirmed: Boolean
)
data class CallSummary(
    val id: Long,
    val sessionLabel: String?,   // optional user-entered label; never a queried call number
    val createdAt: Long,
    val items: List<PromiseItem>,
    val fullTranscript: String   // kept per T1; NOT audio
)
```

# Phase 2 — Manual Capture Session & Microphone Backend

> Prerequisite reading: `AGENTS.md`, `docs/00-CONSTITUTION.md`, and
> `docs/06-companion-backend-migration.md`.

## Phase Goal

Build the backend for an explicitly user-started, local microphone recording
session. The user starts and stops every session. JustSaid is **not** a default
dialer, an `InCallService`, a carrier-call recorder, or a VoIP application.

The supported audio contract is deliberately narrow:

- Input: `MediaRecorder.AudioSource.MIC` only.
- Format: mono, 16 kHz, signed 16-bit PCM in an app-private temporary WAV.
- Meaning: local microphone audio. Speakerphone may make a remote voice audible,
  but this is incidental and must not be promised, detected, or attributed.
- Start: only after an explicit user action from a visible JustSaid activity.
- Stop: only after an explicit user action. Automatic call detection is outside
  this phase and must never be required for correctness.

## Non-goals [CRITICAL]

Do not implement or retain:

- `ROLE_DIALER`, `InCallService`, `Call`, `TelecomManager`, or dialer intent
  filters.
- `AccessibilityService` or an accessibility-based capture workaround.
- `VOICE_CALL`, `VOICE_UPLINK`, `VOICE_DOWNLINK`, `VOICE_RECOGNITION`, or
  `VOICE_COMMUNICATION` audio sources.
- Phone-number lookup, contact lookup, call-log access, call-state polling, or
  automatic/background capture.
- Stereo capture, speaker labels, or a claim that the remote party was heard.

## Owned backend boundaries

This phase owns only backend files and manifest/service declarations:

```
audio/
  MicrophoneAudioSource.kt       # the sole AudioRecord source: MIC + mono
  WavWriter.kt                   # streaming 16 kHz PCM writer
  WavFileProvider.kt             # unique private session temp file
  RecordedSession.kt             # handoff to STT/pipeline
  SessionPipeline.kt             # pipeline interface

session/
  CaptureSessionState.kt         # sealed state model
  CaptureSessionController.kt    # explicit start/stop state machine
  StaleAudioCleaner.kt           # synchronous startup/session-start sweep

service/
  MicrophoneCaptureService.kt    # microphone FGS while AudioRecord is active
```

The future UI calls `CaptureSessionController.start()` and `.stop()`. It does
not own files, audio, or pipeline transitions.

## Data handoff contract

```kotlin
enum class CaptureInput { MICROPHONE_MONO }

data class RecordedSession(
    val id: String,                 // randomly generated; no phone number/PII
    val wavFile: File,              // app-private temporary file
    val input: CaptureInput,
    val sampleRate: Int,            // always 16_000
    val channels: Int,              // always 1
    val startedAt: Long,
    val durationMs: Long,
    val sessionLabel: String?,      // optional user-entered label only
)

sealed interface CaptureSessionState {
    data object Idle : CaptureSessionState
    data class Recording(val sessionId: String, val startedAt: Long) : CaptureSessionState
    data class Finalizing(val sessionId: String) : CaptureSessionState
    data class Processing(val sessionId: String) : CaptureSessionState
    data class Completed(val sessionId: String) : CaptureSessionState
    data class Failed(val userMessage: String) : CaptureSessionState
}

interface SessionPipeline {
    suspend fun process(session: RecordedSession): JustSaidResult<CallSummary>
}
```

`TranscriptSegment.speaker` is always `Speaker.UNKNOWN` for this input. The
summary guardrail must therefore render extracted items as Unconfirmed.

## Lifecycle

```text
visible activity + explicit Start tap
  → check RECORD_AUDIO
  → synchronously delete stale JustSaid temporary WAVs
  → create unique private WAV and start microphone FGS
  → AudioRecord(MIC, MONO) streams PCM to WavWriter

explicit Stop tap
  → stop/release AudioRecord
  → patch and close WAV header
  → stop microphone FGS
  → SessionPipeline: STT → LLM → guardrails
  → delete WAV in finally, then report result
```

Processing runs only while the app presents a visible processing state. If that
visible task is destroyed, the safe outcome is cancellation plus WAV deletion;
do not disguise long inference as a phone-call foreground service.

## Privacy, failure, and recovery [CRITICAL]

- Generate each filename with `File.createTempFile()` or a cryptographically
  random session id. Do not use `currentTimeMillis()` as a unique identifier.
- `StaleAudioCleaner` deletes only the app's known temporary-session filenames;
  it runs before recording starts and on app launch.
- The pipeline owns one `try/finally` deletion point. Check the deletion result;
  on failure, retain a non-content error marker and retry on the next sweep.
- Never put raw audio in Room, DataStore, external storage, backups, logs, or a
  share intent.
- `onStart()` must be idempotent, `stop()` safe in every state, and a failed
  AudioRecord init must delete the newly created file.

## Permissions and manifest target

Keep only the permissions and components required for this phase:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".service.MicrophoneCaptureService"
    android:exported="false"
    android:foregroundServiceType="microphone" />
```

Remove all dialer, telecom, call-log, phone-state, contacts, and accessibility
service declarations as part of the migration task. `POST_NOTIFICATIONS` is
requested where the platform requires it; the recording session must still have
a clear foreground-service notification and Stop action.

## Required tests

- `CaptureSessionControllerTest`: legal state transitions, double start/stop,
  init failure cleanup, and pipeline launch exactly once.
- `MicrophoneAudioSourceTest`: production source uses only `MIC` and mono.
- `WavWriterTest`: mono 16 kHz/16-bit header and streaming output.
- `StaleAudioCleanerTest`: deletes matching stale files only; never exports or
  touches unrelated cache files.
- `SessionPipelineWavDeletionTest`: deletion succeeds on STT, LLM, cancellation,
  and persistence failures.
- Instrumented test: user-started microphone FGS starts with granted permission,
  then releases the microphone and stops after explicit Stop.

## Worker-agent prompt (backend only)

> Implement Phase 2 of JustSaid: the **manual microphone capture backend**.
> Read `AGENTS.md`, `docs/00-CONSTITUTION.md`, this file, and
> `docs/06-companion-backend-migration.md`. Do not build or modify Compose UI.
> Do not use telecom, a default-dialer role, an accessibility service, contacts,
> call logs, call-state listeners, or any AudioRecord source other than `MIC`.
>
> Create the `RecordedSession`, `CaptureSessionState`, `SessionPipeline`,
> `CaptureSessionController`, `StaleAudioCleaner`, `MicrophoneAudioSource`, and
> `MicrophoneCaptureService` boundaries described above. Use Hilt interfaces and
> injected dispatchers. `start()` is invoked only from a visible activity after
> permission grant; `stop()` is explicit. Stream mono 16 kHz/16-bit PCM to a
> unique private cache WAV. Stop/release capture before starting STT. Delete the
> temporary WAV on every failure and in the post-processing `finally` block.
>
> Update the manifest and Gradle dependencies only to remove obsolete telecom,
> accessibility, and unneeded permissions and to declare the microphone FGS.
> Preserve the existing offline-only downloader boundary. Add the JVM and
> instrumented tests listed in this phase. Do not change UI packages.

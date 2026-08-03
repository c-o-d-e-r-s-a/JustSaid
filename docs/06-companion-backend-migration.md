# Phase 6 — Companion Backend Migration

## Purpose

This is the implementation order for pivoting the existing default-dialer
prototype into a manual, offline call companion. It deliberately excludes UI
redesign. Each task is independently reviewable and must compile before the
next begins.

## Target architecture

```text
Explicit Start in visible JustSaid activity
  → CaptureSessionController
  → MicrophoneCaptureService + AudioRecord(MIC, mono)
  → private temporary WAV
  → explicit Stop
  → streaming STT
  → bounded/chunked task extraction + literal-proof validation
  → optional text retention
  → WAV deletion and stale-file recovery
```

There is no default dialer, InCallService, phone-number lookup, call-state
machine, accessibility service, stereo source, remote-audio claim, or online AI
path.

## Agent task 1 — Remove unsupported platform integration

**Scope:** `AndroidManifest.xml`, Gradle, Hilt modules, `telecom/`, the existing
accessibility service, and tests that exclusively cover dialer integration.

- Remove dialer intent filters, `JustSaidInCallService`, call-style phone-call
  service declarations, accessibility-service declarations, and their obsolete
  permissions.
- Keep only microphone FGS permissions, notification permission, and the model
  downloader's `INTERNET` permission.
- Remove Hilt bindings that require `CallStateHolder`, `CallActions`, contact or
  call-audio gateways. Do not edit Compose screens in this task.
- Replace tests with manifest/permission tests proving prohibited components and
  permissions are absent.

**Acceptance:** `assembleDebug` succeeds; no source import references
`android.telecom`, `AccessibilityService`, `VOICE_CALL`, `VOICE_RECOGNITION`,
or `VOICE_COMMUNICATION`.

## Agent task 2 — Build the manual capture backend

**Scope:** `audio/`, new `session/`, `JustSaidApp` startup wiring, new microphone
capture service, DI, JVM and instrumented tests. No UI edits.

Implement the exact Phase 2 contracts: explicit state machine, private unique
WAV, `MIC` mono source, microphone FGS started only from a visible activity,
explicit stop, and stale-temp cleaner. The controller must expose `StateFlow`;
it must not inspect a phone call or start itself.

**Acceptance:** a fake audio source can drive start → frames → stop →
`RecordedSession`; every early failure deletes its file.

## Agent task 3 — Make STT session-safe and streaming

**Scope:** `stt/`, tests, and only the data contracts it consumes.

- Replace `RecordedCall`/capture-tier branching with `RecordedSession`.
- Decode WAV in bounded 30-second windows; do not load an entire recording into
  a `ByteArray` and then a full `FloatArray`.
- Keep one native Whisper context for the session, VAD-gate each window, and set
  every segment speaker to `UNKNOWN`.
- Bound the maximum accepted duration until chunked summary extraction lands.
  Return a friendly failure, not a blank or silently truncated summary.

**Acceptance:** a fixture longer than one window is transcribed without a full
file-size allocation; a stereo fixture cannot create attributed speakers.

## Agent task 4 — Make summarization bounded and reliable

**Scope:** `llm/`, `summary/`, `pipeline/`, tests. No history or UI edits.

- Keep literal-substring proof validation as the final authority.
- For transcripts exceeding the model context, extract candidate promises per
  bounded transcript chunk, validate each against its original chunk, then merge
  and deduplicate candidates. Never silently submit an overlong prompt.
- Mark every item `UNKNOWN`/Unconfirmed for microphone input.
- The pipeline deletes the WAV in `finally`, reports deletion failure, and does
  not auto-save text. It returns a summary for the later UI retention decision.

**Acceptance:** a long fixture yields either validated candidates or a clear
user-safe failure; all pipeline outcomes delete the temporary WAV.

## Agent task 5 — Align persistence for a companion session

**Scope:** `core/CallSummary.kt`, `data/db/`, `data/repo/`, migrations, tests.
No UI edits.

- Replace automatically gathered `contactName`/`phoneNumber` with optional
  user-entered `sessionLabel`.
- Make persistence explicit: `SessionPipeline` returns a summary; a later UI
  action calls `SummaryRepo.save()`.
- Preserve SQLCipher, text-only storage, export isolation, history cleanup, and
  the proof quote.

**Acceptance:** a migration preserves old rows where possible, new rows contain
no queried phone metadata, and no session is saved until `save()` is called.

## Order and handoff rule

Run tasks 1 → 2 → 3 → 4 → 5. Each agent may change only the task's named
scope, must read all linked docs, and must leave a short handoff describing
contract changes, test results, and any device-specific microphone behavior.

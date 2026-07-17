# Phase 2 — Default Dialer, Call Lifecycle & Audio Capture Engine

> Prerequisite reading: `AGENTS.md`, `docs/00-CONSTITUTION.md`, Phase 1 outputs
> (`core/`, `SettingsRepo`, theme, nav).

---

## Phase Goal

Make JustSaid a usable **default dialer** that:
1. Registers via `RoleManager` (`ROLE_DIALER`) and handles calls through
   `InCallService` (incoming + outgoing).
2. Shows an in-call UI with the big **`🔴 LISTEN FOR LISTS`** toggle and a
   CallStyle system notification, running inside a compliant **foreground service**.
3. Captures two-way call audio into a private `.wav` buffer using a **tiered
   capture strategy** with graceful degradation, mapping speakers to channels
   when possible.
4. Resolves the remote number → contact name via the Contacts ContentProvider.
5. On disconnect (if LISTEN was ON), hands the finished `.wav` + metadata to the
   pipeline entry point (Phase 3/4) and shows the loading modal.

## Why not androidx core-telecom / CallsManager?

`androidx.core-telecom` `CallsManager#addCall` is for apps that **place their
own self-managed VoIP calls**. JustSaid handles the user's **carrier** calls as
the system dialer, which is the `InCallService` + `ROLE_DIALER` contract. Use the
platform `android.telecom` API. (We still adopt the *spirit* of the telecom
guidance: let the framework own audio focus/routing, use CallStyle notifications,
and obey foreground-service rules.)

## The Audio Capture Reality (READ THIS) [CRITICAL]

True stereo **local-left / remote-right** capture needs
`AudioSource.VOICE_CALL`, which requires `CAPTURE_AUDIO_OUTPUT` — a
signature/privileged permission **not grantable to a sideloaded APK on Android
10+**. Being the default dialer does not grant it. Therefore implement a tiered
`AudioSource` behind an interface and pick the best available at runtime:

| Tier | Mechanism | Result | Availability |
|---|---|---|---|
| **1 Stereo** | `MediaRecorder`/`AudioRecord` with `VOICE_CALL` | True L=local, R=remote | Rooted / OEM-permissive / some pre-A10 |
| **2 Dual-mono** | `VOICE_RECOGNITION` (+ optional AccessibilityService trick) | Both parties, mono, no channel split | Most OEMs when accessibility enabled (the "Cube ACR" technique) |
| **3 Mic-only** | `MIC` | Local clearly, remote faint on speakerphone | Universal fallback |

- Probe tiers at capture start; record which tier succeeded into call metadata so
  Phase 3/4 knows whether channel-based speaker attribution is trustworthy.
- **Never** promise stereo in the UI. The pipeline degrades: Tier 1 → channel
  attribution; Tiers 2/3 → mono, speakers marked `UNKNOWN` (LLM labels
  "Unconfirmed", per Constitution S2).

## Architecture & Targeted Dependencies

- **Owned dirs:** `telecom/`, `audio/`, `ui/incall/`, `data/contacts/`.
- **Platform APIs:** `android.telecom.InCallService`, `Call`, `Call.Callback`,
  `RoleManager`, `AudioRecord`/`MediaRecorder`, `ContactsContract`,
  `NotificationCompat.CallStyle`, foreground service type `microphone`+`phoneCall`.
- **DI:** bind `AudioSource` interface so tests inject `FileAudioSource`
  (streams a fixture wav) — this is the linchpin of device-free testing.

### Key components

```
telecom/
  JustSaidInCallService.kt   # onCallAdded/Removed → updates CallStateHolder
  CallStateHolder.kt         # @Singleton StateFlow<CallState> (sealed)
  CallActions.kt             # answer/hangup wrappers over Call
audio/
  AudioSource.kt             # interface { start(): Flow<ShortArray>/frames; stop() }
  VoiceCallAudioSource.kt    # Tier 1
  VoiceRecognitionAudioSource.kt  # Tier 2
  MicAudioSource.kt          # Tier 3
  AudioSourceFactory.kt      # probes tiers, returns best + tier tag
  WavWriter.kt               # 16kHz/16-bit PCM; stereo interleave when Tier 1
  CaptureController.kt       # ties toggle + call-active → write to cacheDir wav
service/
  CaptureForegroundService.kt  # holds mic during active call; CallStyle notif
ui/incall/
  InCallScreen.kt + InCallViewModel.kt   # the 🔴 LISTEN toggle, contact name
data/contacts/
  ContactResolver.kt         # number → display name (ContactsContract)
```

### Optional TTS notice

If `SettingsRepo.ttsNoticeEnabled`, on call `Active` speak once via
`android.speech.tts.TextToSpeech`: *"This call is being recorded for AI
assistance."* Play into the call is not guaranteed on all devices — play on the
device speaker; document the limitation. Off by default.

## Data Handoff Boundaries

**Consumes (from Phase 1):** `SettingsRepo` (`ttsNoticeEnabled`, `alwaysListen`),
`ModelPaths` (not used directly but injected), theme/nav, `JustSaidResult`,
dispatchers.

**Produces (contract for Phase 3):** a `RecordedCall` value handed to a pipeline
entry interface (Phase 3/4 implement the consumer; Phase 2 defines the type +
calls the entry point):
```kotlin
// audio/RecordedCall.kt
enum class CaptureTier { STEREO, DUAL_MONO, MIC_ONLY }
data class RecordedCall(
    val wavFile: File,          // in cacheDir; pipeline MUST delete after summary
    val tier: CaptureTier,      // trust channel attribution only if STEREO
    val sampleRate: Int,        // 16000
    val channels: Int,          // 2 if STEREO else 1
    val phoneNumber: String,
    val contactName: String?,   // from ContactResolver
    val durationMs: Long
)
// audio/CallPipeline.kt  (interface implemented in Phase 3/4)
interface CallPipeline { suspend fun process(call: RecordedCall) }
```
Phase 2 wires: on `Disconnected` && listenToggled → show loading modal →
`callPipeline.process(recordedCall)` on a background dispatcher. For Phase 2
standalone, provide a `NoOpCallPipeline` that just deletes the wav, so the flow
is testable before Phase 3 exists.

**Must NOT do:** transcription, LLM, DB writes. Must not persist the wav beyond
handing it to the pipeline. Must not store audio anywhere but `cacheDir`.

## Acceptance / Tests

- `WavWriterTest`: header correctness, stereo interleave, mono path.
- `CaptureControllerTest`: buffer only while toggle ON && call Active; stop on
  disconnect; wav path is in cacheDir.
- `AudioSourceFactoryTest`: tier selection logic with faked capabilities.
- `InCallToggleTest` (Espresso, `FileAudioSource` via Hilt test module + `adb
  shell telecom add-call` per TESTING.md B): toggle ON → disconnect → loading
  modal appears → `CallPipeline.process` invoked.
- Accessibility: LISTEN toggle ≥48dp, high contrast, content description.

---

## 🤖 Worker-Agent Implementation Prompt (copy/paste)

**Recommended model: Claude Opus 4.8** (telecom + real-time audio + foreground
service + graceful-degradation logic is the trickiest non-native phase; use the
strongest reasoning model). Fallback: Opus 4.7.

> You are implementing **Phase 2** of the JustSaid Android app. Read `AGENTS.md`,
> `docs/00-CONSTITUTION.md`, and `docs/02-dialer-audio-capture.md` and obey them
> as hard rules. Phase 1 already provides `core/` (`JustSaidResult`,
> dispatchers, `ModelPaths`), `SettingsRepo`, the Compose theme, and navigation.
> Do not implement STT, LLM, or Room.
>
> **Goal:** Make the app a **default dialer** using the platform
> `android.telecom` API (NOT androidx core-telecom — that's for self-managed
> VoIP). Implement `RoleManager` `ROLE_DIALER` request flow and a
> `JustSaidInCallService` that tracks call lifecycle into a `@Singleton`
> `CallStateHolder` exposing `StateFlow<CallState>` (sealed:
> `Idle|Ringing|Active|Held|Disconnected`). Build the in-call Compose screen with
> a large high-contrast **`🔴 LISTEN FOR LISTS`** toggle and a
> `NotificationCompat.CallStyle` notification, all under a compliant
> **foreground service** (types `phoneCall`+`microphone`).
>
> **Audio capture — tiered with graceful degradation [CRITICAL]:** Implement an
> `AudioSource` interface and three impls chosen at runtime by
> `AudioSourceFactory`: Tier 1 `VOICE_CALL` (true stereo L=local/R=remote — may
> fail without privileged permission; that's expected), Tier 2
> `VOICE_RECOGNITION` (dual-mono), Tier 3 `MIC` (fallback). Record which tier
> succeeded in `RecordedCall.tier`. Write audio to a `.wav` in `context.cacheDir`
> at 16 kHz/16-bit PCM via `WavWriter` (stereo interleave only for Tier 1).
> Capture ONLY while the LISTEN toggle is ON and the call is Active.
>
> Resolve the remote number → contact display name via `ContactResolver`
> (`ContactsContract`). If `SettingsRepo.ttsNoticeEnabled`, speak "This call is
> being recorded for AI assistance" once on call Active via `TextToSpeech`
> (device speaker; document that injecting into the call stream isn't guaranteed).
>
> **Handoff:** define `audio/RecordedCall.kt` (with `CaptureTier` enum) and
> `audio/CallPipeline.kt` (`interface CallPipeline { suspend fun
> process(call: RecordedCall) }`) exactly as in the phase doc. On `Disconnected`
> && the toggle was ON: show a full-screen loading modal and call
> `callPipeline.process(recordedCall)` on `@DefaultDispatcher`. Provide a
> `NoOpCallPipeline` (deletes the wav) bound via Hilt so this phase runs
> standalone; Phase 3/4 will replace the binding.
>
> **Deliver:** all files under `telecom/`, `audio/`, `service/`, `ui/incall/`,
> `data/contacts/` per the phase doc, plus manifest entries (dialer intent
> filters, `InCallService` with `BIND_INCALL_SERVICE` + metadata, foreground
> service declarations, permissions: `READ_PHONE_STATE`, `READ_CALL_LOG`,
> `READ_CONTACTS`, `RECORD_AUDIO`, `FOREGROUND_SERVICE`,
> `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `MANAGE_OWN_CALLS`).
> Add a Hilt module binding `AudioSource`/`AudioSourceFactory` so tests can
> inject a `FileAudioSource`.
>
> **Constraints:** No network. Never persist the wav outside `cacheDir`; never
> store audio in DB/prefs. Let the telecom framework manage audio focus/routing —
> do not manually poke `AudioManager`/Bluetooth. MVVM + `StateFlow`. All strings
> in `strings.xml`. Provide unit tests: `WavWriterTest`, `CaptureControllerTest`,
> `AudioSourceFactoryTest`; and Espresso `InCallToggleTest` using an injected
> `FileAudioSource` + `adb shell telecom add-call` (see `TESTING.md` §B).
>
> **Done when:** `assembleDebug` + `testDebugUnitTest` pass; app can be set as
> default dialer; toggling LISTEN during a mocked call produces a wav in cacheDir
> and, on hangup, invokes `CallPipeline.process` and shows the loading modal.

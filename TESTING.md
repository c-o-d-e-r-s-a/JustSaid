# TESTING.md — JustSaid Verification Guide

> **Companion pivot:** The default-dialer, `adb shell telecom`, stereo, and
> in-call-overlay sections below describe the retired prototype. Do not use them
> for new work. Backend agents must follow the manual microphone-session tests in
> `docs/02-dialer-audio-capture.md` and the task order in
> `docs/06-companion-backend-migration.md`.

Three independent verification tracks. A phase is not "done" until its relevant
track passes. All tracks are designed to run **without a live phone call** where
possible (per the Testing Strategy: the audio source is dependency-injected).

---

## A. CLI Model Benchmarking (native correctness + speed, no Android)

Goal: prove the GGML models transcribe/summarize acceptably *before* wiring JNI,
and measure real device speed. Do this on a desktop first, then on-device via
`adb`.

### A.1 whisper.cpp bench (desktop, sanity)

```bash
# from app/src/main/cpp/whisper.cpp
cmake -B build -DWHISPER_BUILD_TESTS=OFF
cmake --build build -j --config Release

# quantized model recommended for the app: Q5_0 multilingual base or small
./build/bin/whisper-cli \
  -m models/ggml-base-q5_0.bin \
  -f ../../test/resources/fixtures/two_party_sample_16k_mono.wav \
  -t 4 --language auto

# For the stereo tier: split channels, transcribe each, then merge with speaker tags.
ffmpeg -i stereo_call.wav -map_channel 0.0.0 left.wav  -map_channel 0.0.1 right.wav
```

Record: real-time factor (audio_sec / process_sec), WER against a hand
transcript. Target: RTF ≥ 1.0 on a mid-range device with Q5_0 base.

### A.2 On-device native bench via adb

```bash
adb push build/bin/whisper-cli /data/local/tmp/
adb push models/ggml-base-q5_0.bin /data/local/tmp/
adb push fixture.wav /data/local/tmp/
adb shell "cd /data/local/tmp && ./whisper-cli -m ggml-base-q5_0.bin -f fixture.wav -t 6"
```

### A.3 llama.cpp summarizer bench

```bash
# from app/src/main/cpp/llama.cpp — build the CLI once:
cmake -B build -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=ON
cmake --build build -j --config Release

./build/bin/llama-cli -m models/llama-3.2-3b-instruct-q4_k_m.gguf \
  -p "$(cat ../../../test/resources/fixtures/system_prompt.txt)$(cat ../../../test/resources/fixtures/sample_transcript.txt)" \
  -n 512 --temp 0.1 --no-warmup -no-cnv

# On-device variant (mirrors A.2):
adb push build/bin/llama-cli /data/local/tmp/
adb push models/llama-3.2-3b-instruct-q4_k_m.gguf /data/local/tmp/
adb shell "cd /data/local/tmp && ./llama-cli -m llama-3.2-3b-instruct-q4_k_m.gguf \
  -p \"$(cat prompt.txt)\" -n 512 --temp 0.1 -no-cnv"
```

Verify: output obeys the `Task/Item [Qty] (Proof: "quote")` format and that
every quote is a literal substring of the transcript (the guardrail the app
enforces in code — see Phase 4).

---

## B. Mocking Calls via `adb shell telecom` (no second phone)

The default-dialer + InCallService path can be exercised without a real call.

### B.1 Register the app as default dialer (once, on the test device)

```bash
# List phone accounts / verify role
adb shell telecom get-default-dialer
adb shell telecom set-default-dialer com.justsaid.app.debug
```

### B.2 Add a fake incoming/outgoing call

```bash
# Add a mock call handle so InCallService receives onCallAdded()
adb shell telecom add-call
# Some OEM builds support direct emulation:
adb shell am start -a android.intent.action.CALL -d tel:+15555550123

# Cycle call states for lifecycle testing (if supported by build):
adb shell telecom cleanup-orphan-phone-accounts
```

### B.3 Inject audio without speaking

Because the capture source is injected (`AudioSource` interface, Phase 2), the
instrumented test binds a `FileAudioSource` that streams a fixture wav instead
of the mic. This lets Espresso drive the full toggle→summary flow on CI/emulator.

```bash
# push a fixture the FileAudioSource will read in debug builds
adb push app/src/test/resources/fixtures/two_party_sample_16k_mono.wav \
  /sdcard/Android/data/com.justsaid.app.debug/files/mock_input.wav
```

---

## C. JUnit / Espresso Test Structure

### C.1 JVM unit tests — `app/src/test/` (fast, no device) [primary safety net]

| Area | Test | What it proves |
|---|---|---|
| `summary/` | `PromiseParserTest` | Output parsed to items; any item whose quote is not a substring of transcript is DROPPED. |
| `summary/` | `HallucinationGuardTest` | "Unconfirmed" labeling; empty output when no evidence. |
| `stt/` | `WhisperEngineFakeTest` | Kotlin wrapper contract using a fake native bridge (no `.so`). |
| `audio/` | `WavWriterTest` | Stereo L/R interleave, 16kHz PCM header correctness. |
| `audio/` | `AudioLifecycleTest` | `.wav` deleted after summary completes (success AND failure paths). |
| `data/db/` | `SummaryDaoTest` (Robolectric/Room) | Encrypted CRUD; auto-cleanup >30d logic. |
| `data/download/` | `ModelDownloaderTest` (MockWebServer) | Resume, checksum, progress, isolated-dir target. |
| `export/` | `SmsIntentBuilderTest` | `smsto:` URI + body correctly formed for a number. |

Run: `./gradlew :app:testDebugUnitTest`

### C.2 Instrumented / Espresso — `app/src/androidTest/`

| Test | Flow |
|---|---|
| `OnboardingFlowTest` | Legal disclaimer is unskippable; download gate blocks main UI until models present. |
| `InCallToggleTest` | With `FileAudioSource` bound via Hilt test module, toggle ON → disconnect → loading modal → summary screen shows. |
| `SummaryActionsTest` | Save writes a row; "Send via SMS" fires `ACTION_SENDTO` (asserted with Espresso-Intents `intended()`). |
| `AccessibilityTest` | `AccessibilityChecks.enable()` — contrast, touch-target ≥48dp, content descriptions. |
| `WhisperJniSmokeTest` | Loads real `.so`, runs a 3-sec fixture, asserts non-empty transcript (device/emulator only). |

Run: `./gradlew :app:connectedDebugAndroidTest`

### C.3 CI note

CI runs Track C.1 (JVM) on every push. Track A and Track C.2 run on a nightly
device-lab job (or manually), since they need models/hardware. Never gate merges
on network model downloads — CI uses tiny stub models in `fixtures/` for JNI
smoke only.

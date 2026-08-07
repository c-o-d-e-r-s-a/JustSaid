# Phase 3 — Optional Local Voice Commands

## Goal

Add a short, user-initiated voice-command path alongside typed routine
activation. This phase repurposes whisper.cpp for commands such as “start my
study routine”; it does not record conversations, meetings, calls, or long
notes.

## Scope

- Own `command/` audio input, temporary-file lifecycle, Whisper wrapper use,
  command transcript boundary, manifest microphone permission, and tests.
- Capture only after a visible tap. Cap capture at 15 seconds and offer a clear
  Stop button. Typed input remains available if permission is denied.
- Reuse one Whisper context per command, run inference off the main thread, and
  return a bounded `CommandText` to Phase 4.

## Audio contract

- `MIC`, mono, 16 kHz, 16-bit PCM, private cache only.
- No foreground microphone service, wake word, background capture, call audio,
  speaker labels, audio imports, or capture history.
- Delete the temporary WAV in a verified `finally` path immediately after STT
  returns, throws, or is cancelled. The full transcript is never stored.

## Contract

```kotlin
interface VoiceCommandTranscriber {
    suspend fun transcribe(commandAudio: TemporaryCommandAudio): JustSaidResult<CommandText>
}

@JvmInline
value class CommandText(val value: String)
```

An empty, silent, unsupported, or ambiguous command returns a friendly failure
and has no routine side effect.

## Acceptance tests

- Fixture “start my study routine” produces bounded text with fake native
  bridge; silence produces no execution.
- Every success/failure/cancellation path deletes its temporary WAV and verifies
  deletion.
- `RECORD_AUDIO` is requested only after a voice-button tap.
- A static manifest/source test rejects call, foreground mic service,
  Accessibility, and background-recording additions.

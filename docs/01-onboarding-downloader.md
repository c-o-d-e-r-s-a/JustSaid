# Phase 1 — Offline Onboarding, Safety Notice, and Model Download

## Goal

Set up the local-only foundation without implementing routine execution. A user
can understand the product boundary, choose typed-only use or optional voice
commands, and download required models through the one permitted network path.

## Scope

- Own `JustSaidApp`, `MainActivity`, `core/`, initial `di/`,
  `data/download/`, settings bootstrap, onboarding UI, theme, and manifest.
- Present an unskippable first-run safety notice:
  - routines run only after an explicit user action;
  - the app cannot and will not pay, send, submit, delete, or handle account
    credentials;
  - model downloads are the only network use;
  - optional voice commands are processed locally and temporary audio is
    immediately deleted.
- Offer **Use without voice** as a complete path. Voice-model download is
  optional and initiated by the user.
- Implement resumable, checksum-verified model download into private app
  storage. `ModelDownloader` is the only OkHttp owner.
- Create a Keystore-backed DB passphrase and expose model-ready state.

## Must not do

Do not implement routines, timers, DND, package launching, command recording,
Accessibility, call capture, foreground services, or a public template feed.

## Required contracts

```kotlin
interface ModelDownloader {
    fun download(model: ModelId): Flow<DownloadState>
}

data class OnboardingState(
    val acceptedSafetyNotice: Boolean,
    val voiceCommandsEnabled: Boolean,
    val requiredModelsReady: Boolean,
)
```

`ModelPaths` owns all private model locations. No feature calls the network to
check for updates at startup.

## Permissions

Declare only `INTERNET` for downloader use and `POST_NOTIFICATIONS` only if the
completed phase needs visible download progress. `RECORD_AUDIO` is deferred to
Phase 3 and is requested only when the user turns on voice commands.

## Acceptance tests

- First-run safety notice cannot be skipped.
- Typed-only use reaches the empty routine library with no microphone request.
- Download resumes, verifies checksum, exposes progress, and fails safely.
- Airplane mode after download permits all onboarding completion and local use.
- Search/static test proves no network client exists outside `data/download/`.

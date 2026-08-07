# 00 — Routine Runner Constitution

Every phase inherits these non-negotiable rules. A rule marked **[CRITICAL]** is
a release blocker. Read this document with `AGENTS.md` before implementation.

## Product boundary

The app is a local, user-triggered routine runner. It performs a short list of
low-risk actions previously created or approved by that same user. It is not a
general agent, call recorder, default dialer, VoIP client, payment assistant,
Accessibility controller, cloud service, or workflow marketplace.

## Privacy and network

- **P1 [CRITICAL]** The only network boundary is
  `data/download/ModelDownloader` fetching explicitly configured model files.
  No analytics, ads, telemetry, crash reporting, GMS/Firebase service,
  marketplace, account, sync, socket, or DNS lookup elsewhere.
- **P2 [CRITICAL]** Never log command audio, full command transcripts, routine
  names containing user data, checklist contents, model prompts, or execution
  slots in release builds. Debug logs must be metadata-only.
- **P3** With models installed, every feature works in airplane mode. External
  apps launched by a user-approved target are outside this app's network
  boundary and must be visibly identified as such.

## Authority and action safety

- **R1 [CRITICAL]** A routine contains only values of the sealed `RoutineAction`
  type. V1 actions are `StartLocalTimer`, `SetApprovedFocusMode`,
  `LaunchApprovedTarget`, `ShowLocalChecklist`, and
  `ShowLocalNotification`.
- **R2 [CRITICAL]** No action may originate from an LLM response, a voice
  transcript, or an imported template. These sources can at most select an
  already-saved local routine and fill schema-approved, non-sensitive slots.
- **R3 [CRITICAL]** An explicit user gesture, widget action, or Quick Settings
  tile starts every run. No scheduled/background/boot/start-on-notification
  execution and no always-listening microphone in v1.
- **R4 [CRITICAL]** Payments, purchases, transfers, credentials, OTPs,
  biometrics, account recovery/settings, messages/emails/posts/forms, deletion,
  installation, permissions, arbitrary intents/URLs, and UI-node/gesture
  automation are prohibited. They must be unrepresentable in the data model.
- **R5 [CRITICAL]** Every external target is created and approved locally. A
  routine refers to it by opaque local ID; it never stores a package or URI
  provided by an untrusted source.
- **R6** Validate the full routine before the first action. Missing permission,
  target, or state means fail closed: stop the run and explain what the user can
  fix. Do not skip to a guessed alternative.
- **R7** The execution UI always displays current action, completed actions,
  failures/skips, and a visible Stop control. Stop is idempotent.

## Voice-command lifecycle

- **V1 [CRITICAL]** Voice capture is optional, visibly user-initiated, and
  bounded to 15 seconds. There is no foreground microphone service or wake
  word.
- **V2 [CRITICAL]** Raw audio is private-cache-only and deleted in a verified
  `finally` path immediately after STT ends, including failure/cancellation.
  No audio in Room, preferences, MediaStore, exports, backups, or logs.
- **V3** Do not save the full command transcript. Retain only the resulting
  routine ID and minimal non-sensitive execution receipt when needed.

## Local-model constraints

- **M1 [CRITICAL]** Kotlin validates a model response against a closed schema
  and an existing local routine ID before any execution. Invalid, ambiguous, or
  unknown output has zero side effects.
- **M2** Bound the prompt to the command plus local routine names/IDs. Do not
  feed browser/UI data, account data, long recordings, or arbitrary imported
  files into the model.
- **M3** Native contexts follow init once → reuse for the request → idempotent
  free. Inference is off-main-thread and must not log content in release.

## Template sharing

- **S1 [CRITICAL]** There is no in-app public platform, account, discovery
  feed, remote installer, or remote execution path.
- **S2** A local `.routine.json` import is an untrusted template containing only
  allowed action kinds and generic suggestions. It cannot contain code, a
  package name, URI, token, secret, permission, nested routine, loop, or model
  prompt.
- **S3** The import screen renders every action and requires local target mapping
  and explicit save before the template becomes a routine.

## UX and accessibility

- **U1** Compose single-activity UI. Primary actions are one tap; no hidden
  gesture or jargon-heavy dashboard.
- **U2** WCAG AA: 4.5:1 contrast, 18sp base text, 48dp touch targets, and
  TalkBack content descriptions.
- **U3** Explain what each routine will do before first execution. Never imply
  that the app controls an external app beyond the documented action.

## Error and persistence rules

- **E1** Layer boundaries return `JustSaidResult<T>`; no exceptions cross them.
- **E2** Routine and target data are encrypted at rest. Store only data needed
  to run/review the routine and minimal execution receipts; never command audio
  or complete transcripts.
- **E3** Clear Library requires confirmation and removes routines, targets, and
  receipts. Failed cleanup is surfaced and retryable.

## Shared target types

```kotlin
sealed interface RoutineAction {
    data class StartLocalTimer(val durationMinutes: Int) : RoutineAction
    data class SetApprovedFocusMode(val enabled: Boolean) : RoutineAction
    data class LaunchApprovedTarget(val targetId: String) : RoutineAction
    data class ShowLocalChecklist(val checklistId: String) : RoutineAction
    data class ShowLocalNotification(val messageId: String) : RoutineAction
}

data class Routine(
    val id: String,
    val displayName: String,
    val actions: List<RoutineAction>,
)

data class CommandResolution(
    val routineId: String,
    val slots: Map<String, String>,
)

sealed interface ActionReceipt {
    val actionIndex: Int
    data class Completed(override val actionIndex: Int) : ActionReceipt
    data class Skipped(override val actionIndex: Int, val reason: String) : ActionReceipt
    data class Failed(override val actionIndex: Int, val reason: String) : ActionReceipt
}
```

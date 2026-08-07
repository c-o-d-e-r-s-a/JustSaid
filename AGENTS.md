# AGENTS.md — Routine Runner Worker-Agent Rulebook

> Read this file and `docs/00-CONSTITUTION.md` in full before changing code.
> Rules marked **[CRITICAL]** are release blockers.

JustSaid *(temporary project name)* is a **100% on-device, local-first Android
routine runner**. A person explicitly activates a routine. The app executes
only a typed, reviewed, low-risk action list. On-device speech-to-text and a
small local model may resolve a short command to an existing routine; they do
not control the phone freely. **Zero cloud. No analytics. No telemetry.**

The product is not a call recorder, default dialer, VoIP service, generic
Accessibility controller, payment tool, or autonomous background agent.

## 0. Prime Directives [CRITICAL]

1. **Offline or die.** The only code allowed to touch the network is the model
   downloader in `data/download/` using OkHttp against an approved Hugging Face
   model URL. No other HTTP, sockets, DNS, analytics, crash SDK, ads,
   Firebase/GMS service, account, marketplace, sync, or telemetry.
2. **Typed actions only.** A routine may contain only the sealed action types
   in `routine/`. No JavaScript, shell commands, reflection, arbitrary intents,
   arbitrary URLs, arbitrary UI nodes, or model-generated action payloads.
3. **The model never has authority.** It can return only an ID of an existing
   local routine plus schema-validated slots. A missing/ambiguous match must
   stop and ask the user; it must never select a likely action.
4. **Explicit execution.** Every run begins from a visible user action, widget,
   or Quick Settings tile. No schedules, boot receivers, background triggers,
   always-listening wake words, or automatic retry loops in v1.
5. **Fail closed.** Resolve all target package, URI, permissions, and action
   parameters before execution. Any unexpected condition stops the remaining
   actions and reports what happened. Never substitute, improvise, or retry an
   action that might change data.
6. **High-impact actions are prohibited.** Never automate payment, purchase,
   banking, password/OTP/biometric entry, account recovery/settings, message or
   email sending, posting, submission, deletion, app installation, or
   permission changes.
7. **No public executable workflow platform.** Imported routine files are
   untrusted declarative templates, not code. They cannot grant capabilities;
   the user must review them and map every external target to a local approved
   target before saving.
8. **Command audio is radioactive.** Temporary raw command audio lives only in
   private cache and is deleted immediately after STT succeeds or fails. Never
   persist, back up, export, or log command audio or a full command transcript.
9. **The user is your grandparent.** One-tap primary actions, large
   high-contrast text, simple explanations of every action, and WCAG AA.
10. **Stay in scope.** Do not modify files outside the assigned phase unless
    that phase prompt names them. Do not refactor a different phase “to help.”

## 1. Target Folder Structure

The current tree contains retired capture code until Phase 6 migration. New
routine-runner code belongs in this target layout; do not invent top-level
directories.

```text
JustSaid/
├─ app/src/main/
│  ├─ java/com/justsaid/app/
│  │  ├─ JustSaidApp.kt
│  │  ├─ MainActivity.kt
│  │  ├─ core/                 # Result types, clock, dispatchers
│  │  ├─ di/
│  │  ├─ command/              # typed input, short STT, resolver contract
│  │  ├─ routine/              # sealed actions, validator, executor, gate
│  │  ├─ data/
│  │  │  ├─ db/                # encrypted routine/receipt storage
│  │  │  ├─ repo/
│  │  │  └─ download/          # the only network boundary
│  │  ├─ ui/
│  │  │  ├─ onboarding/
│  │  │  ├─ routines/
│  │  │  ├─ run/
│  │  │  ├─ library/
│  │  │  └─ settings/
│  │  └─ export/               # local template import/export only
│  ├─ cpp/                     # whisper.cpp and llama.cpp JNI
│  └─ res/
├─ docs/
├─ AGENTS.md
├─ README.md
└─ TESTING.md
```

## 2. Kotlin and Architecture Rules

- Kotlin only for app code; C++17 only for native model bridges. Gradle Kotlin
  DSL only—never Groovy build files.
- Compose Material3, single activity, MVVM. A ViewModel exposes immutable
  `StateFlow<UiState>` and owns side effects.
- Hilt constructor injection. Every platform dependency—clock, package
  launcher, DND gateway, notification gateway, command recorder, model paths—
  is behind an interface with a fakeable implementation.
- Coroutines and Flow only. Inject `@IoDispatcher` and `@DefaultDispatcher`;
  never hardcode `Dispatchers.IO`.
- Boundaries return `JustSaidResult<T>`; do not throw across layers.
- No global mutable state other than Hilt-scoped singletons. All user-facing
  strings belong in `res/values/strings.xml`.

## 3. Action and Execution Rules [CRITICAL]

The Phase 2 sealed action model is the sole authority. V1 permits only actions
equivalent to `StartLocalTimer`, `SetApprovedFocusMode`,
`LaunchApprovedTarget`, `ShowLocalChecklist`, and `ShowLocalNotification`.

- `LaunchApprovedTarget` resolves a local target ID, never a package/URI from a
  command, import, or model response.
- An imported template carries suggestions only. It cannot name an executable
  package or add a new action type without local user approval.
- Each action reports `Completed`, `Skipped`, or `Failed`; the first failure
  ends the run. No recursive actions or loops.
- A routine is immutable while it is executing. A new explicit run is required
  after completion or failure.
- The execution screen always shows the current action and a visible Stop
  button. Stop is immediate and idempotent.

**Accessibility is out of scope for v1.** Do not add an `AccessibilityService`,
overlay, UI-node scraping, gesture injection, or screen-content capture. A
future proposal would require a new constitution, threat model, permission
review, and user authorization; it is not an incremental task.

## 4. Command and Native Rules

- Typed activation is the baseline. Optional voice capture starts only from a
  visible activity after a tap and is limited to a short command (15 seconds or
  less). There is no background microphone service or wake word.
- If voice capture creates a WAV, use private cache, delete it in `finally`
  after STT, and verify deletion. Do not retain the transcript.
- The local LLM receives a bounded command and a compact list of existing
  routine names/IDs. Its structured response contains only `routineId` and
  allowed slot values. Validate it in Kotlin before the execution gate.
- Native contexts follow init once → infer → idempotent free. Never infer on the
  main thread, never log speech/transcript content in release, and never
  allocate large buffers per inference window.

## 5. Permissions Policy [CRITICAL]

Declare only what a completed phase needs:

- `INTERNET` solely for the model downloader.
- `RECORD_AUDIO` only for optional, visibly user-started voice commands.
- `POST_NOTIFICATIONS` only for routine progress/completion notifications.
- DND access is a user-granted system special access, requested only when a
  routine first needs it.

Do not declare a foreground microphone service, accessibility service, overlay,
contacts, call log/state, dialer, SMS, storage, location, camera, notification
listener, exact-alarm, boot receiver, or package-install permission in v1.

## 6. Definition of Done

- `:app:assembleDebug` and JVM tests pass.
- No new network call exists outside `data/download/`.
- Every action is schema-validated, locally approved, visible to the user, and
  covered by a fake-gateway JVM test.
- Unsupported/imported/model-generated actions fail closed.
- Command audio and transcripts are not persisted; cleanup is verified.
- User-visible strings, TalkBack descriptions, 48dp touch targets, and WCAG AA
  contrast are checked.
- Public functions have intent-focused KDoc and handoff types match the phase
  document.

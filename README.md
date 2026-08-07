# JustSaid *(temporary project name)*

**An open-source, local-first Android routine runner.**

JustSaid helps a person start a repeatable, low-risk phone routine with one
explicit action: a tap, Quick Settings tile, widget, or short on-device voice
command. A routine is made from a small, auditable list of safe actions such as
starting a local focus timer, enabling a user-approved focus mode, opening a
user-approved app or deep link, and showing a local checklist.

The app is not a call recorder, default dialer, VoIP service, general-purpose
phone controller, or autonomous agent. It never automates payments, account
security, messages, deletion, or submission.

> **Pivot status:** this documentation defines the routine-runner product.
> Existing source code still contains the retired conversation-capture
> prototype and must be migrated according to
> [`docs/06-routine-backend-migration.md`](docs/06-routine-backend-migration.md)
> before it is described as a routine runner.

## Product promise

> Start your personal phone routine privately. The app works from an explicit
> command, runs only actions you approved, and never sends your data to a cloud.

Example, after the user has created and approved a **Study** routine:

1. The user taps **Start Study**, invokes the Quick Settings tile, or presses
   the in-app microphone and says “start my study routine.”
2. JustSaid starts a local timer, enables a focus mode the user previously
   authorized, opens their chosen notes and audio apps, and shows their local
   checklist.
3. It reports each completed or skipped action. Unexpected state always stops
   the routine; it never guesses a way forward.

## What v1 can and cannot do

| Supported v1 actions | Never-supported actions |
| --- | --- |
| Start a local timer, show a local checklist, display a notification | Payments, purchases, transfers, subscriptions |
| Enable a user-granted system focus/DND mode | Password, OTP, biometric, or account-recovery handling |
| Launch a user-approved package or app link | Sending messages, emails, posts, or forms |
| Resolve a local voice command to an existing routine | Deleting files/data, changing account/security settings |
| Import a declarative routine template after review | Arbitrary scripts, arbitrary UI taps, background execution |

The app uses whisper.cpp only for a short, explicitly initiated voice command
and llama.cpp only to map that command to an **existing local routine ID** and
its allowed slots. The model is never allowed to create an action, choose a
package, generate a URL, or execute an imported workflow.

Voice commands are optional. Typed activation, a widget, and a Quick Settings
tile are first-class paths. An always-listening wake word is deliberately out
of scope because it would require persistent microphone access and introduce
battery and privacy costs.

## Privacy and sharing

- All routine data and command processing stay on device. The only allowed
  network code is the first-run model downloader.
- Raw command audio is private temporary data and is deleted immediately after
  transcription, whether transcription succeeds or fails.
- The app stores no command-audio history or full command transcripts.
- A future community library may distribute **declarative templates** through
  an external channel such as GitHub. There is no public workflow platform in
  this project, no accounts, and no remote execution. Every imported template
  is untrusted until the user reviews it and maps its suggested targets to their
  own approved targets.

## Architecture

```text
Explicit user trigger
  -> typed command / optional short voice command
  -> local command resolver (existing routine IDs only)
  -> RoutineExecutionGate
  -> typed SafeActionExecutor
  -> local execution receipt
```

The full contracts, safety rules, and phased migration are in [`docs/`](docs/).
Start with [`docs/00-CONSTITUTION.md`](docs/00-CONSTITUTION.md) and
[`AGENTS.md`](AGENTS.md).

## Development

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

See [`TESTING.md`](TESTING.md) for the mandatory safety, offline, and device
checks.

## Name

“JustSaid” is a placeholder while the project is renamed. The application ID
and package names must not change until a separate, deliberate rename task.

# Phase 6 — Retired Capture Prototype to Routine Runner Migration

## Purpose

The repository currently implements a manual conversation-capture pipeline.
Android's active-call microphone restrictions make that the wrong product core.
This document defines the ordered backend migration to the safe routine-runner
architecture. Do not redesign all UI until the new backend contracts are tested.

## Target architecture

```text
Explicit tap / widget / Quick Settings tile
  -> typed command OR short visible voice command
  -> existing-routine resolver
  -> validation gate
  -> typed safe-action executor
  -> local receipt
```

There is no call recording, call state, dialer role, VoIP, continuous listening,
conversation summary, speaker inference, arbitrary accessibility control, or
public executable workflow marketplace.

## Task 1 — Remove the retired conversation product surface

**Scope:** capture/session/pipeline-specific UI and docs references, retired
summary/history models, microphone FGS, raw session cleanup, and their tests.

- Remove call/conversation claims, session labels, task/promise extraction,
  speaker fields, and summary/export flows from the active product path.
- Remove the foreground microphone service. Keep a small private command-audio
  component only when Phase 3 lands.
- Preserve local model downloader, native wrappers, DI discipline, encryption,
  and test fixtures that are still reusable.
- Add static tests that forbid dialer/call/foreground-mic/Accessibility APIs.

**Acceptance:** `assembleDebug` succeeds with no active source reference to
telecom, call recording, `AudioRecord` session capture, call summaries, or
microphone foreground services.

## Task 2 — Establish the typed safe-action backend

**Scope:** new `routine/`, `core/` target types, DI, JVM tests. No full library
or voice UI.

Implement Phase 2 exactly: sealed action model, validator, fakeable gateways,
explicit state machine, action receipts, stop, and fail-closed behavior. Start
with local timer/checklist/notification; add focus and approved-target gateways
only after their fakes/tests pass.

**Acceptance:** fakes prove ordered actions, first-failure stop, missing-target
preflight failure, and impossible prohibited actions.

## Task 3 — Build local library and approval storage

**Scope:** `data/db/`, `data/repo/`, template types, target approval storage,
migrations, JVM tests. No LLM changes.

Persist typed routines and approved targets in encrypted storage. Add local-only
template parse/review contracts; no network, accounts, discovery, or remote
workflow download.

**Acceptance:** an imported template has no ability to launch anything until a
user maps and saves it as a local routine.

## Task 4 — Add typed activation and safe routine run UI

**Scope:** `ui/routines/`, `ui/run/`, widget/tile entry points if needed, and
instrumented tests. Do not add voice yet.

The user can select a routine, inspect its actions, run it, see progress, and
stop it. Entry points must produce the same explicit `UserTrigger` contract.

**Acceptance:** device test completes a local fixture routine in airplane mode;
stop/recreation cannot leave a run behind.

## Task 5 — Add optional short voice selection

**Scope:** `command/`, Whisper bridge reuse, `RECORD_AUDIO`, manifest, tests.

Implement the Phase 3 short-capture contract and then add the Phase 4
constrained resolver. Voice is an alternative to selecting a routine, not an
execution authority. If a model is unavailable or resolution is uncertain, the
user returns to routine selection.

**Acceptance:** all paths delete source audio, resolver output cannot create or
alter an action, and typed routines still work with microphone permission denied.

## Task 6 — Harden and document template sharing

**Scope:** template importer/exporter, docs, tests. No server or marketplace.

Define/version the declarative format and make its parser hostile-input safe.
Templates may be shared manually, for example via a GitHub release, but there
is no public platform or remote execution feature.

**Acceptance:** fuzz/malformed imports fail closed; exported templates reveal no
local target identifiers, packages, command history, or sensitive data.

## Handoff rule

Run Tasks 1 through 6 in order. Each agent changes only named scope, records
tests run, and states any Android/OEM-specific restriction. Do not begin a
future broad Accessibility or public-platform proposal inside this migration.

# Phase 5 — Routine Library, Target Approval, Receipts, and Template Review

## Goal

Persist and manage a user's local routine library without adding cloud sharing
or a public workflow platform. This phase adds the first complete backend/UI
loop after Phases 1–4 are safe.

## Scope

- Own encrypted Room entities/DAOs, repositories, `ui/routines/`, `ui/library/`,
  `ui/run/`, local template import/export, and settings.
- Store routines, their typed actions, locally approved targets, checklists,
  optional focus-mode configuration, and minimal execution receipts.
- Provide create/edit/review/run/stop/delete/clear-library experiences with
  large, understandable UI.

## Persistence rules

- Use SQLCipher/Keystore protection already established in Phase 1.
- Never store raw command audio, full command transcripts, LLM prompts,
  arbitrary external-app UI content, credentials, tokens, or account data.
- Receipts contain action index, safe action kind, time, and result—not raw
  user-provided command text.
- Clear Library removes routines, approved targets, checklists, and receipts
  after confirmation.

## Template import/export

V1 supports local file share/import only. It does not browse, download,
authenticate, sync, or execute a remote library.

A `.routine.json` contains a version, display name, allowed action kinds, and
generic configuration suggestions. It must not contain code, URI/package,
secrets, action IDs outside the sealed list, nested routines, conditions,
loops, or model text. Import always follows this sequence:

```text
untrusted file -> strict parse -> show every action -> map local targets
-> user review -> explicit save -> runnable local routine
```

Unmapped external actions cannot be saved or run.

## Acceptance tests

- Encrypted persistence round trips a valid routine and receipt.
- Target approval is required before a target action can execute.
- Import rejects every prohibited field/action; valid import remains disabled
  until the user maps targets and confirms.
- Export contains no installed-package details, command history, or secrets.
- Clear Library removes all stored routine data after confirmation.

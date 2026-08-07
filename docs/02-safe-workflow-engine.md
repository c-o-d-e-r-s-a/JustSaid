# Phase 2 — Safe Routine Model and Execution Engine

## Goal

Build the product core: deterministic, low-risk routines that can run without
voice or a local LLM. This phase owns the backend only; UI may expose a small
debug/temporary trigger but does not redesign the full experience.

## Scope

- Own `routine/`, its Hilt bindings, fakeable platform gateways, and JVM tests.
- Define the Constitution `RoutineAction`, `Routine`, `ActionReceipt`, and a
  `RoutineExecutionGate` state machine.
- Implement only five executors: local timer, user-approved focus/DND mode,
  locally approved target launch, local checklist display, and local
  notification.
- Create `ApprovedTarget` locally from a visible configuration screen/API. It
  may wrap an installed package or user-approved app link, but the routine
  references only an opaque target ID.

## Explicitly forbidden

- Accessibility, overlays, UI-node inspection, gesture injection, screen
  recording/capture, arbitrary intent extras, arbitrary URI/package values.
- Recursion, loops, condition scripts, delayed/scheduled runs, retries after a
  failure, broadcasts/boot triggers, and background execution.
- Any action listed as prohibited in Constitution R4.

## State machine

```text
Idle
  -> Validating(routineId)
  -> Executing(routineId, actionIndex)
  -> Completed(receipts) | Failed(receipts, reason) | Stopped(receipts)
```

Only an explicit `start(routineId, userTrigger)` may leave `Idle`. Validate the
full routine and all referenced approved targets before action zero. Execute in
order. The first failure ends the run; `stop()` ends it immediately and is safe
to call repeatedly.

## Gateway contracts

```kotlin
interface FocusModeGateway {
    suspend fun enableApprovedMode(): JustSaidResult<Unit>
    suspend fun disableApprovedMode(): JustSaidResult<Unit>
}

interface ApprovedTargetLauncher {
    suspend fun launch(target: ApprovedTarget): JustSaidResult<Unit>
}

interface LocalTimerGateway {
    suspend fun start(minutes: Int): JustSaidResult<Unit>
}
```

Every gateway is constructor-injected. Production package launch uses an
explicit, previously approved target; tests use fakes. A target no longer
installed is a failure, not an opportunity to search the device or browser.

## Acceptance tests

- A valid fixture performs actions in order and produces immutable receipts.
- Missing target or focus access fails before any action runs.
- A failed action prevents all later actions.
- Stop is immediate/idempotent and leaves no background work.
- The validator and import parser make prohibited actions impossible to create.

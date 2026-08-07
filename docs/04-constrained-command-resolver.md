# Phase 4 — Constrained Local Command Resolver

## Goal

Turn typed or transcribed short command text into a selection of an **existing
local routine**, never an instruction to control the phone. llama.cpp is a
convenience parser, not an authority.

## Scope

- Own `command/CommandResolver`, constrained LLM prompt/bridge, Kotlin schema
  parser/validator, and JVM tests. Do not add actions or modify executors.
- Receive `CommandText` plus a compact `List<RoutineDescriptor(id, name)>`.
- Return `JustSaidResult<CommandResolution>` only when the returned routine ID
  exactly exists locally and all slots conform to that routine's declared,
  non-sensitive schema.

## Hard output boundary

The model response is strict JSON:

```json
{"routineId":"study","slots":{}}
```

It may not contain action kinds, package names, URIs, free-form arguments,
execution instructions, model prompts, or confidence-based guesses. Kotlin
rejects unknown fields, duplicate keys, bad JSON, unknown IDs, and invalid slot
keys. A failed result is presented as “Choose a routine” with no side effect.

## Prompt requirements

- Bound input to one 15-second command and the local routine descriptor list.
- Temperature at or below 0.2; bounded tokens; no follow-up tool calls.
- State that unrecognised input must return a no-match response.
- Do not retain prompts, command text, or raw output after resolution.

## Native rules

Use the existing llama.cpp JNI lifecycle: init once per resolution, infer on
`@DefaultDispatcher`, then idempotently free. No large per-token allocations or
release logging of model input/output.

## Acceptance tests

- Exact, case/whitespace-normalized routine name resolves to its existing ID.
- Ambiguous command, invented ID, prompt injection, malformed JSON, action
  payload, package name, URI, or slot outside the routine schema is rejected.
- Resolver failure never reaches `RoutineExecutionGate.start`.

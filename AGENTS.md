# AGENTS.md — JustSaid Worker-Agent Rulebook

> Read this file **in full** before writing any code. It is the law. Code that
> violates a rule marked **[CRITICAL]** is a high-priority bug and will be
> rejected. When in doubt, follow the "Constitution" in `docs/00-CONSTITUTION.md`.

JustSaid is a **100% on-device, local-first** Android call companion. A user
explicitly starts a microphone capture session around a normal phone call;
JustSaid transcribes that microphone audio (whisper.cpp) and summarizes it into
"promises/tasks with verbatim proof" (llama.cpp). It is not a default dialer,
carrier-call recorder, or VoIP provider. **Zero cloud. No analytics. No
telemetry.**

---

## 0. The Prime Directives [CRITICAL]

1. **Offline or die.** The ONLY code allowed to touch the network is the model
   downloader (`data/download/`, Phase 1) using OkHttp against a Hugging Face
   URL. ANY other network call, socket, DNS lookup, analytics/crash SDK, or
   GMS/Firebase dependency is a **critical security failure**. Do not add them.
2. **Raw audio is radioactive.** The temporary `.wav` buffer MUST be deleted the
   instant the LLM summary completes (success OR failure). No raw audio is ever
   persisted, backed up, or logged. See `docs/00-CONSTITUTION.md#audio-lifecycle`.
3. **No promise without proof.** Every extracted task/promise MUST carry a
   verbatim quote copied from the transcript. If code cannot locate the quote as
   a substring of the transcript, the item is DROPPED, not guessed.
4. **The user is your grandparent.** Single-tap actions, huge high-contrast
   text, no jargon, no dashboards, no gestures. WCAG AA minimum.
5. **Do not modify files outside your assigned Phase** unless the Phase prompt
   explicitly lists the file. Never refactor another phase's code "to help."

---

## 1. Canonical Folder Structure

Worker agents MUST place files exactly here. Do not invent new top-level dirs.

```
JustSaid/
├─ app/
│  ├─ build.gradle.kts
│  ├─ proguard-rules.pro
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml
│     │  ├─ java/com/justsaid/app/
│     │  │  ├─ JustSaidApp.kt            # @HiltAndroidApp entry
│     │  │  ├─ MainActivity.kt           # single-activity Compose host
│     │  │  ├─ di/                       # Hilt modules (AudioModule, DbModule, ...)
│     │  │  ├─ ui/
│     │  │  │  ├─ theme/                 # high-contrast, large-text theme
│     │  │  │  ├─ onboarding/            # Phase 1: legal + downloader screens
│     │  │  │  ├─ capture/               # Phase 2: manual capture controls (future UI)
│     │  │  │  ├─ summary/               # Phase 4/5: summary + Save/Send buttons
│     │  │  │  ├─ history/               # Phase 5: saved summaries list
│     │  │  │  └─ settings/              # Phase 5: language, TTS, cleanup, clear
│     │  │  ├─ audio/                    # Phase 2: microphone capture engine
│     │  │  ├─ session/                  # Phase 2: CaptureSession state machine
│     │  │  ├─ stt/                      # Phase 3: WhisperEngine (Kotlin wrapper)
│     │  │  ├─ llm/                      # Phase 4: LlmEngine (Kotlin wrapper) + prompts
│     │  │  ├─ summary/                  # Phase 4: PromiseParser, guardrails
│     │  │  ├─ data/
│     │  │  │  ├─ db/                    # Room entities, DAOs, SQLCipher factory
│     │  │  │  ├─ repo/                  # repositories (SummaryRepo, SettingsRepo)
│     │  │  │  ├─ download/              # Phase 1: ModelDownloader (OkHttp)
│     │  │  │  └─ contacts/              # ContactResolver (ContentProvider)
│     │  │  ├─ export/                   # Phase 5: PDF/Text exporter, SMS intent
│     │  │  └─ core/                     # constants, Result types, dispatchers
│     │  ├─ cpp/                         # NDK / JNI (Phase 3 & 4)
│     │  │  ├─ CMakeLists.txt
│     │  │  ├─ justsaid_whisper_jni.cpp
│     │  │  ├─ justsaid_llm_jni.cpp
│     │  │  ├─ whisper.cpp/              # vendored source (git submodule/checkout)
│     │  │  └─ llama.cpp/                # vendored source (git submodule/checkout)
│     │  └─ res/                         # layouts-free (Compose), strings, colors
│     ├─ test/                          # JVM unit tests (no device needed)
│     │  ├─ java/com/justsaid/app/...
│     │  └─ resources/fixtures/          # small mock .wav + transcript .txt
│     └─ androidTest/                   # Espresso + instrumented JNI tests
├─ docs/                                # THE BLUEPRINT (read your phase file)
│  ├─ 00-CONSTITUTION.md
│  ├─ 01-onboarding-downloader.md
│  ├─ 02-dialer-audio-capture.md          # manual companion capture phase
│  ├─ 03-whisper-ndk.md
│  ├─ 04-llm-summarizer.md
│  ├─ 05-intent-history.md
│  └─ 06-companion-backend-migration.md
├─ .gitignore
├─ AGENTS.md   (this file)
├─ TESTING.md
└─ README.md
```

---

## 2. Kotlin / Architecture Conventions

- **Language:** Kotlin only for app code; C++17 for native. No Java.
- **Build scripts [CRITICAL]:** Gradle Kotlin DSL only (`build.gradle.kts`,
  `settings.gradle.kts`). **Never** output Groovy (`build.gradle`). If you catch
  yourself writing Groovy syntax, stop and convert to Kotlin DSL. Chosen for
  type safety + IDE autocomplete, which reduces generated-config errors.
- **UI:** Jetbrains Compose + Material3. Single-activity (`MainActivity`) +
  `navigation-compose`. No XML layouts (strings/colors XML is fine).
- **Architecture:** MVVM. `ViewModel` exposes immutable `StateFlow<UiState>`.
  UI is a pure function of state. Side effects go through the ViewModel.
- **DI:** Hilt, constructor injection. **[CRITICAL for testing]** Every hardware
  dependency (audio source, model paths, clock, contacts) is injected behind an
  interface so unit tests can substitute fakes with no physical device.
- **Concurrency:** Coroutines + `Flow`. Inject `CoroutineDispatcher`
  (`@IoDispatcher`, `@DefaultDispatcher`) — never hardcode `Dispatchers.IO`.
- **Errors:** Return a sealed `JustSaidResult<T>` (`Success`/`Failure`) from
  repos/engines. Do not throw across layer boundaries. See `core/`.
- **No global mutable state.** No singletons except Hilt-scoped ones.
- **Strings:** All user-facing text in `res/values/strings.xml` for
  accessibility/locale. No hardcoded UI strings in Kotlin.

---

## 3. JNI / NDK Conventions [CRITICAL]

The native layer is the highest-risk area. Follow exactly.

1. **One JNI file per engine.** `justsaid_whisper_jni.cpp` and
   `justsaid_llm_jni.cpp`. Never mix whisper and llama symbols in one file.
2. **Naming:** JNI functions use the full mangled package
   `Java_com_justsaid_app_stt_WhisperEngine_<method>`. Kotlin `external fun`
   declarations live in the matching wrapper class only.
3. **Opaque handles:** Native context pointers (`whisper_context*`,
   `llama_context*`) are passed to Kotlin as a `long` handle. Kotlin treats it
   as opaque. Never dereference or arithmetic on it in Kotlin.
4. **Lifecycle = init once, reuse, free once:**
   - `nativeInit(modelPath, params) -> long handle`  (allocations happen HERE)
   - `nativeInfer(handle, ...) -> result`            (no model realloc)
   - `nativeFree(handle)`                              (idempotent; null-safe)
   Reuse the context/state across audio chunks. Do **not** load the model per
   chunk. See `docs/00-CONSTITUTION.md#native-memory`.
5. **Memory safety:**
   - Always release JNI local refs / `ReleaseStringUTFChars` /
     `ReleasePrimitiveArrayCritical` on every path, including error paths.
   - Guard every handle: `if (handle == 0) return error`.
   - No raw `new`/`delete` for buffers reused across inference — preallocate in
     `nativeInit`, reuse in `nativeInfer` (the "zero runtime allocation" goal;
     realistically: **zero *per-chunk* allocation**, buffers sized at init).
   - Free order for llama/whisper: free state → free context → free backend.
6. **Threading:** Native inference runs on a background thread from Kotlin
   (coroutine on `@DefaultDispatcher`). Never call `nativeInfer` on the main
   thread. Thread count param = number of **physical** big cores (query at init).
7. **No `abort()`/`exit()` in native code.** Return error codes; let Kotlin
   surface a friendly message.
8. **No logging of transcript or audio content in native code** in release
   builds (`#ifndef NDEBUG` only).

---

## 4. State Management (Manual Capture Lifecycle)

Capture state is owned by `session/` and flows one direction:

```
Visible JustSaid activity (explicit user tap)
   → CaptureSessionController (StateFlow<CaptureSessionState>)
      → MicrophoneCaptureController (start/stop private mono buffer)
         → on user stop:
              → SttEngine (Phase 3) → LlmEngine (Phase 4)
                 → user chooses retention → delete .wav [CRITICAL]
```

- `CaptureSessionState` is a sealed class: `Idle | Recording | Finalizing |
  Processing | Completed | Failed`.
- The microphone foreground service may start **only from a visible activity
  after an explicit capture tap** and stops as soon as capture ends.
- There is no automatic start, default-dialer role, call-state listener, or
  accessibility-service dependency. A user starts and stops every session.
- Audio is mono and every transcript segment is `UNKNOWN`; never infer a remote
  caller or promise two-sided capture.

---

## 5. Permissions Policy [CRITICAL]

Request the **minimum** and only when needed: `RECORD_AUDIO`,
`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`, and
`POST_NOTIFICATIONS` where required. `READ_CONTACTS` is optional and may only
be requested for a later, user-initiated recipient picker. Do not declare
`READ_CALL_LOG`, `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `MANAGE_OWN_CALLS`,
or dialer-role components. Do not add an `AccessibilityService`. **`INTERNET`
is declared but must be used ONLY by the downloader.** No external storage,
location, camera, or contacts write. Justify any new permission in the PR
description.

---

## 6. Definition of Done (every Phase)

- [ ] Compiles; `./gradlew :app:assembleDebug` passes.
- [ ] Unit tests for pure logic pass on JVM (no device).
- [ ] No new network calls outside `data/download/`.
- [ ] No raw audio persisted; wav deletion verified where applicable.
- [ ] All user strings in `strings.xml`; screens pass TalkBack + large-font.
- [ ] Public functions have KDoc stating intent (not narrating code).
- [ ] Handoff types match the "Data Handoff Boundaries" in your phase doc.

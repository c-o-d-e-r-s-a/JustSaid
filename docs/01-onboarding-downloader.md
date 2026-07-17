# Phase 1 — Onboarding, Legal Gate & Model Download Manager

> Prerequisite reading: `AGENTS.md`, `docs/00-CONSTITUTION.md`.
> This phase produces the app skeleton + first-run experience. It is the only
> phase permitted to use the network, and only for downloading model weights.

---

## Phase Goal

On a fresh install, guide a non-technical user through:
1. An **unskippable legal disclaimer** about regional call-recording consent.
2. **Model download** (STT + LLM) from Hugging Face into private storage, with a
   big visible progress bar, resumable on failure, checksum-verified.
3. A gate that blocks the rest of the app until both models are present.

Also stands up the app skeleton every later phase builds on: Hilt, Compose
theme (high-contrast/large-text), navigation, `core/` types, DataStore settings.

## Architecture & Targeted Dependencies

- **New/owned dirs:** `JustSaidApp.kt`, `MainActivity.kt`, `core/`, `di/`,
  `ui/theme/`, `ui/onboarding/`, `data/download/`, `data/repo/SettingsRepo.kt`.
- **Deps (already in `build.gradle.kts`):** Compose+Material3, Hilt,
  navigation-compose, DataStore, OkHttp (downloader), security-crypto (for the
  DB passphrase created here so Phase 5 can open the DB).
- **Model registry:** hardcode a `ModelCatalog` object with entries:
  - STT multilingual: `ggml-base-q5_0.bin` (default, Auto-Detect).
  - STT english-small (optional, chosen via settings): `ggml-small.en-q5_0.bin`.
  - LLM: `llama-3.2-3b-instruct-q4_k_m.gguf`.
  Each entry: `{ id, fileName, url (HF), sizeBytes, sha256 }`. Put the real HF
  URLs behind a single constant so they can be updated in one place.
- **Storage target [CRITICAL]:** `context.filesDir/models/` (app-private,
  isolated). Never external storage. This dir is git-ignored and backup-excluded.
- **Downloader design:**
  - OkHttp with `Range` header support for **resume** (store `.part` files,
    rename on completion).
  - Emit `Flow<DownloadProgress>` (`bytesDownloaded/totalBytes`, per-file + overall).
  - Verify SHA-256 after each file; delete + fail on mismatch.
  - Wi-Fi-preferred but not required; show data-usage warning.
  - Single-flight: a `DownloadManager`-style coordinator so re-entry resumes,
    not restarts.
- **Legal gate:** `SettingsRepo` DataStore flag `legalAccepted: Boolean`. Nav
  start destination = `legal` if not accepted, else `download` if models missing,
  else hand off to main (`incall`/`history` shell from later phases — for now a
  placeholder `HomeScreen`).

## Data Handoff Boundaries

**Consumes:** nothing (entry point).

**Produces (contract later phases rely on):**
- `core/JustSaidResult.kt`, `core/Dispatchers.kt` (`@IoDispatcher`,
  `@DefaultDispatcher` qualifiers), `core/ModelPaths.kt`:
  ```kotlin
  interface ModelPaths {
      fun sttModelFile(): File     // resolved from settings (auto vs en)
      fun llmModelFile(): File
      fun modelsReady(): Boolean
  }
  ```
- `data/repo/SettingsRepo.kt` exposing `Flow`s + setters for:
  `legalAccepted`, `ttsNoticeEnabled` (Phase 2), `sttLanguageLock`
  (`AUTO`/`EN`, Phase 3), `autoCleanupEnabled` (Phase 5), `alwaysListen`.
- A DB passphrase provisioned into `EncryptedSharedPreferences` /
  Android Keystore, read by Phase 5's SQLCipher factory. **Define the key alias
  constant here:** `core/Crypto.kt` → `const val DB_KEY_ALIAS`.
- `ui/theme/` `JustSaidTheme` (colors, typography ≥18sp, high contrast).

**Must NOT do:** touch telecom, audio, or JNI. No DB entities (Phase 5 owns Room
schema) — only provision the passphrase.

## Acceptance / Tests

- `ModelDownloaderTest` (MockWebServer): resume via Range, checksum reject,
  progress emission, writes to private dir.
- `OnboardingFlowTest` (Espresso): legal screen cannot be dismissed without
  "I Agree"; main content unreachable until `modelsReady()`.
- Airplane-mode: after models present, app opens straight to Home.

---

## 🤖 Worker-Agent Implementation Prompt (copy/paste)

**Recommended model: Claude Sonnet 4.5** (broad Android+Kotlin scaffolding,
medium complexity, no exotic native code). Composer 2.5 is an acceptable
faster alternative for the pure-UI parts.

> You are implementing **Phase 1** of the JustSaid Android app. First read
> `AGENTS.md` and `docs/00-CONSTITUTION.md` in the repo and obey them as hard
> rules. Do not implement any other phase; do not touch telecom, audio, STT,
> LLM, or Room entities.
>
> **Goal:** Build the app skeleton + first-run flow: (1) Hilt app + single
> Compose activity with `navigation-compose`; (2) high-contrast, large-text
> Material3 theme in `ui/theme/`; (3) an **unskippable** legal disclaimer screen
> about regional call-recording consent; (4) a **resumable, checksum-verified
> model downloader** that fetches whisper + llama GGML weights from Hugging Face
> into `context.filesDir/models/` with a large visible progress bar; (5) a gate
> that routes: legal-not-accepted → Legal, else models-missing → Download, else
> → a placeholder `HomeScreen`.
>
> **Deliver these files** (exact paths from `AGENTS.md` §1):
> - `app/src/main/java/com/justsaid/app/JustSaidApp.kt` (`@HiltAndroidApp`)
> - `MainActivity.kt` (`@AndroidEntryPoint`, hosts `NavHost`)
> - `core/JustSaidResult.kt`, `core/Dispatchers.kt`, `core/Crypto.kt`
>   (`DB_KEY_ALIAS`), `core/ModelPaths.kt` (+ impl in `di/` or `data/`)
> - `di/AppModule.kt` (dispatchers, OkHttp, DataStore, ModelPaths, SettingsRepo)
> - `ui/theme/{Color,Type,Theme}.kt` — contrast ≥4.5:1, base ≥18sp, scales with
>   system font; large 48dp+ buttons.
> - `ui/onboarding/LegalScreen.kt` + `LegalViewModel` — full-screen, scroll the
>   disclaimer, a single big **"I Agree"** button that sets
>   `SettingsRepo.legalAccepted=true`. No skip/back path.
> - `ui/onboarding/DownloadScreen.kt` + `DownloadViewModel` — shows per-file and
>   overall progress, MB counts, retry button, Wi-Fi/data note. Blocks until done.
> - `ui/onboarding/HomeScreen.kt` — placeholder ("You're all set") for later phases.
> - `data/download/ModelCatalog.kt` — data class entries + real HF URLs behind a
>   single `object HuggingFace { const val BASE = ... }`; fields: id, fileName,
>   url, sizeBytes, sha256. Include: `ggml-base-q5_0.bin` (default STT),
>   `ggml-small.en-q5_0.bin` (optional), `llama-3.2-3b-instruct-q4_k_m.gguf`.
> - `data/download/ModelDownloader.kt` — OkHttp; `Range`-based resume via `.part`
>   files; SHA-256 verify then atomic rename; `Flow<DownloadProgress>`;
>   single-flight; returns `JustSaidResult`.
> - `data/repo/SettingsRepo.kt` — DataStore Preferences: `legalAccepted`,
>   `ttsNoticeEnabled`, `sttLanguageLock (AUTO|EN)`, `autoCleanupEnabled`,
>   `alwaysListen`. Expose `Flow`s + suspend setters.
> - `AndroidManifest.xml` — application class, single activity, `INTERNET`
>   permission (comment: downloader-only), `allowBackup=false`, backup rules
>   excluding cache/models.
> - Provision a random 32-byte DB passphrase into `EncryptedSharedPreferences`
>   under `DB_KEY_ALIAS` on first run (Phase 5 will read it). Do NOT create Room.
>
> **Constraints:** No network calls anywhere except `ModelDownloader`. All user
> text in `strings.xml`. MVVM with `StateFlow<UiState>`. Constructor-injected
> dispatchers (no hardcoded `Dispatchers.IO`). Return `JustSaidResult`, don't
> throw across layers. Provide unit tests: `ModelDownloaderTest` (MockWebServer:
> resume, checksum-fail, progress) and a `SettingsRepoTest`. Provide
> `OnboardingFlowTest` (Espresso) asserting the legal gate is unskippable and the
> download gate blocks Home.
>
> **Done when:** `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest`
> pass, and the three files/tests above exist and behave as specified.

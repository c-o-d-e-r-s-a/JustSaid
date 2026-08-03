# JustSaid

**Open-source, local-first Android call companion + on-device AI summarizer.**
JustSaid records an explicitly user-started microphone note around a phone call,
transcribes it on the device, and extracts promises and to-dos backed by
verbatim proof. It is built for non-technical users (grandparents included).

> 🔒 **100% offline.** No cloud, accounts, analytics, or telemetry. Temporary
> raw audio is deleted after processing. Retained text is stored locally in an
> encrypted database.

## What it does

1. You open JustSaid and explicitly start a **call note session** before or
   during a normal phone call. JustSaid is not your phone app or default dialer.
2. It records **microphone audio only** into a private temporary buffer. The
   local speaker is captured best; speakerphone may make the other party audible,
   but remote audio is never guaranteed or attributed.
3. When you stop the note, it runs **whisper.cpp** then a small **llama.cpp**
   model entirely on-device to extract concise, proof-backed tasks.
4. You review, save, export, or share the text result. JustSaid sends nothing
   itself.

> **Important limitation:** JustSaid is not a two-sided carrier-call recorder.
> Capture quality varies by device, headset, and speakerphone use. See
> `docs/02-dialer-audio-capture.md` for the exact supported contract.

## Architecture at a glance

| Concern | Choice | Why |
|---|---|---|
| Session control | Explicit user-started capture session | Clear consent; no default-dialer role or hidden capture. |
| Audio capture | `MIC`, mono, 16 kHz PCM | Supported local microphone input; speakerphone may improve audibility. |
| STT | whisper.cpp via JNI | Fast on-device transcription. |
| LLM | llama.cpp via JNI | Offline task extraction with code-enforced proof checks. |
| Storage | Room + SQLCipher | Encrypted text-only history. |
| Models | First-run download from Hugging Face | Weights are never bundled in the APK. |

## Repository layout

- **`AGENTS.md`** — contributor rulebook.
- **`TESTING.md`** — local benchmarks and automated testing guidance.
- **`docs/`** — architecture blueprint. Start with:
  - `00-CONSTITUTION.md` — non-negotiable standards.
  - `06-companion-backend-migration.md` — backend-only pivot plan and
    agent-sized tasks.

## Build

Requires Android SDK 35, NDK r26+, and CMake 3.22+.

```bash
./gradlew :app:assembleDebug
```

The first launch downloads STT and LLM weights into app-private storage. The
current repository vendors whisper.cpp and llama.cpp source under
`app/src/main/cpp/`; before public releases, maintainers must record and test
their exact upstream revisions.

## Legal and safety

Recording and consent laws vary by region. JustSaid requires a first-run
disclosure and an explicit user action for every recording. You are responsible
for complying with the laws where you and the other party are located.

## License

JustSaid is released under the MIT License. Vendored native dependencies retain
their own licenses.

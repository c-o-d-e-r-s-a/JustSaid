# JustSaid

**Open-source, local-first Android call recorder + on-device AI summarizer.**
Records your phone calls, transcribes them entirely on the device, and extracts
the *promises and to-dos* — each backed by a verbatim quote — so you have proof
of what was actually said. Built for non-technical users (grandparents included).

> 🔒 **100% offline.** No cloud, no accounts, no analytics. Raw audio is deleted
> the moment the summary is made. Only text summaries are kept, in an encrypted
> local database.

---

## What it does

1. Acts as your phone's **default dialer** so it can access call audio.
2. During a call, a big **`🔴 LISTEN FOR LISTS`** button buffers the audio.
3. When the call ends, it runs **whisper.cpp** (speech-to-text) then a small
   **llama.cpp** LLM (3B) — all on-device — to produce a summary like:

   ```
   • Milk [2 liters]   (Proof: "grab two liters of milk on the way back")
   • Fix the sink      (Proof: "yeah I'll fix the sink this weekend")
   • Budget            (Unconfirmed — amount not clearly stated)
   ```
4. You tap **💾 Save** or **✉️ Send via SMS** (opens your normal messaging app).

## Architecture at a glance

| Concern | Choice | Why |
|---|---|---|
| Dialer / call audio | `InCallService` + `RoleManager` | Correct API for a default dialer handling *carrier* calls (androidx core-telecom is for self-managed VoIP, not this). |
| Audio capture | Tiered: stereo `VOICE_CALL` → mono `VOICE_RECOGNITION` → mic | Stereo L/R speaker split isn't grantable to a sideloaded APK on Android 10+, so we degrade gracefully. See `docs/02`. |
| STT | whisper.cpp (GGML, Q5_0) via JNI | Fast on-device, quantized, shared ggml runtime with the LLM. |
| LLM | llama.cpp (Llama 3.2 3B, Q4_K_M) via JNI | One C++ toolchain for both engines; reliable structured output + NNAPI/Vulkan offload. |
| Storage | Room + SQLCipher | Encrypted text-only history. |
| Models | Downloaded at first run from Hugging Face | Never bundled in the APK (see `.gitignore`). |

## Repository layout

- **`AGENTS.md`** — the rulebook every contributor/agent must follow.
- **`TESTING.md`** — CLI benchmarking, `adb` call mocking, and JUnit/Espresso.
- **`docs/`** — the phase-by-phase build blueprint:
  - `00-CONSTITUTION.md` — non-negotiable engineering standards.
  - `01`…`05` — one self-contained spec per development phase.

## Build

Requires Android SDK 35, NDK r26+, CMake 3.22+.

### 1. Clone with native dependencies

whisper.cpp and llama.cpp are vendored as **git submodules** under
`app/src/main/cpp/` (their sources are compiled by our CMake into a single
`libjustsaid_native.so`). The submodule *sources* are tracked; the build output
and model weights are git-ignored.

```bash
# Fresh clone — pull the app plus both native submodules in one step:
git clone --recurse-submodules <repo-url>

# Already cloned without submodules? Initialize them:
git submodule update --init --recursive
```

If you are setting the submodules up for the first time in this repo (maintainer
step, run once — pin to a known-good tag, do not float on the default branch):

```bash
git submodule add https://github.com/ggml-org/whisper.cpp app/src/main/cpp/whisper.cpp
git submodule add https://github.com/ggml-org/llama.cpp   app/src/main/cpp/llama.cpp

# Pin each to a tested release tag (example tags — verify current before pinning):
git -C app/src/main/cpp/whisper.cpp checkout v1.7.4
git -C app/src/main/cpp/llama.cpp   checkout b4585

git add .gitmodules app/src/main/cpp/whisper.cpp app/src/main/cpp/llama.cpp
git commit -m "Vendor whisper.cpp and llama.cpp as pinned submodules"
```

To update a submodule later, `checkout` a newer tag inside it, re-run the CLI
benchmarks in `TESTING.md` §A, then commit the new submodule pointer.

### 2. Build the APK

```bash
./gradlew :app:assembleDebug
```

First app launch downloads the STT + LLM model weights (~1–2 GB) into the app's
private storage — the weights themselves are never committed (see `.gitignore`).

## Legal

Call recording consent laws vary by region. JustSaid shows an **unskippable
disclaimer** on first launch and offers an optional spoken "this call is being
recorded" notice. **You are responsible for complying with the laws where you
and the other party are located.**

## License

JustSaid is released under the **MIT License** — see [`LICENSE`](LICENSE). The
vendored native dependencies keep their own licenses: whisper.cpp and llama.cpp
are both MIT (see their subdirectories under `app/src/main/cpp/`).

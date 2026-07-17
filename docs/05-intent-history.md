# Phase 5 — Summary UI, SMS Intent Brokerage, Encrypted History, Settings & Export

> Prerequisite reading: `AGENTS.md`, `docs/00-CONSTITUTION.md` (Text Lifecycle
> T1–T4, UX U1–U4), Phase 1 (`SettingsRepo`, DB passphrase in Keystore),
> Phase 4 (`CallSummary`, `SummaryRepo` interface).

---

## Phase Goal

Close the loop for the user:
1. **Summary screen** (post-call): show the promises with big text and two
   single-tap buttons — **`💾 Save to History`** and **`✉️ Send to <Name> via SMS`**.
2. **SMS brokerage:** `Intent.ACTION_SENDTO` with `smsto:<number>` prefilled and
   the markdown summary in the body → opens the user's normal messaging app.
3. **Encrypted history:** Room + SQLCipher store of `CallSummary` (text only),
   opened with the passphrase Phase 1 provisioned.
4. **Settings:** language lock, TTS notice, auto-cleanup (>30d, OFF by default),
   Clear History (confirm dialog), and STT-model choice re-download.
5. **Export:** per-summary and full-history export to PDF/Text for sharing.

## Architecture & Targeted Dependencies

- **Owned:** `data/db/` (entities, DAOs, `SqlCipherFactory`, `JustSaidDatabase`),
  `data/repo/SummaryRepo.kt` (real impl of Phase 4's interface),
  `ui/summary/`, `ui/history/`, `ui/settings/`, `export/`.
- **DB:** Room + `net.zetetic:sqlcipher-android`. Passphrase read from
  `EncryptedSharedPreferences` under `Crypto.DB_KEY_ALIAS` (Phase 1). Schema:
  ```
  CallSummaryEntity(id PK, contactName?, phoneNumber, createdAt, fullTranscript)
  PromiseItemEntity(id PK, summaryId FK, task, quantity?, proofQuote,
                    attributedTo, confirmed)
  ```
  DAO: insert summary+items (transaction), list (newest first, `Flow`),
  delete-all, `deleteOlderThan(cutoffMillis)` for auto-cleanup.
- **Auto-cleanup:** if `SettingsRepo.autoCleanupEnabled`, run
  `deleteOlderThan(now-30d)` on app start (and/or a `WorkManager` periodic job —
  no network). OFF by default (T2).
- **SMS intent:** `export/SmsIntentBuilder.kt`:
  ```kotlin
  fun build(number: String, body: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
      .putExtra("sms_body", body)
  ```
  Body = markdown rendering of the summary (bullet per item + proof). Long SMS
  will split; that's fine. Never send automatically — user taps, then their app
  opens.
- **Export:** `export/SummaryExporter.kt` — Text (build a `.txt` in cacheDir and
  share via `ACTION_SEND` + `FileProvider`) and PDF (`android.graphics.pdf.
  PdfDocument`, paginate, large fonts). Provide a `FileProvider` in the manifest
  scoped to an export cache dir.
- **Summary rendering:** `summary/SummaryMarkdown.kt` — one place that turns a
  `CallSummary` into the shareable markdown/plain text (reused by SMS + export).

### UX (Constitution U1–U4)

- Summary screen: title = contact name/number + date; list of items, each item
  shows the task, optional `[Qty]`, and the proof quote in a quoted style;
  "Unconfirmed" items visually flagged (e.g., amber chip) but still listed.
- Two full-width ≥56dp buttons. Loading modal (from Phase 2) transitions here.
- History screen: newest-first list, tap → detail = summary screen (read-only) +
  Export/Send/Delete. Big search-free simple list.
- Settings screen: plain toggles/rows with descriptions; Clear History shows a
  confirmation dialog; changing STT language triggers Phase 1 downloader for the
  needed model.

## Data Handoff Boundaries

**Consumes:** `CallSummary`/`PromiseItem` (Constitution), `SummaryRepo`
interface (Phase 4), `SettingsRepo` + `Crypto.DB_KEY_ALIAS` (Phase 1),
`CaptureTier`/contact name flow via the emitted summary.

**Produces:** the real `SummaryRepo` binding (replaces Phase 4's fake), full
navigation targets (`summary`, `history`, `settings`) wired into `MainActivity`'s
`NavHost`, and the shareable markdown.

**Must NOT do:** re-open audio/telecom/native concerns. Never store audio. Never
add network beyond the existing downloader.

## Acceptance / Tests

- `SummaryDaoTest` (Room in-memory or SQLCipher test): insert+read, newest-first,
  `deleteOlderThan`, delete-all.
- `SmsIntentBuilderTest`: `smsto:` URI + `sms_body` correct.
- `SummaryMarkdownTest`: item rendering, Unconfirmed labeling, quantity omission.
- `SummaryExporterTest`: text export content; PDF non-empty (Robolectric).
- Espresso `SummaryActionsTest`: Save writes a row; "Send via SMS" fires
  `ACTION_SENDTO` (assert with Espresso-Intents `intended()`).
- Espresso `AccessibilityTest`: contrast, ≥48dp targets, content descriptions.

---

## 🤖 Worker-Agent Implementation Prompt (copy/paste)

**Recommended model: Claude Sonnet 4.5** (Room/UI/intents — well-trodden Android
territory; strong and cost-effective). Composer 2.5 is fine for the Compose
screens if you split UI from data.

> You are implementing **Phase 5** of the JustSaid Android app: summary UI, SMS
> brokerage, encrypted history, settings, and export. Read `AGENTS.md`,
> `docs/00-CONSTITUTION.md` (Text Lifecycle T1–T4, UX U1–U4), and
> `docs/05-intent-history.md`; obey as hard rules. Phase 1 provides
> `SettingsRepo`, dispatchers, and a DB passphrase stored in
> `EncryptedSharedPreferences` under `Crypto.DB_KEY_ALIAS`. Phase 4 emits a
> `CallSummary` and defines a `SummaryRepo` interface (currently a fake binding);
> `CallSummary`/`PromiseItem` are Constitution types. Do not touch telecom,
> audio, or native code.
>
> **Goal:** Let a non-technical user save/send/browse/export call summaries.
>
> **Encrypted DB:** Implement Room + SQLCipher in `data/db/`:
> `JustSaidDatabase`, `CallSummaryEntity`, `PromiseItemEntity` (FK), a DAO with
> transactional insert(summary+items), newest-first `Flow` list, `deleteAll`,
> and `deleteOlderThan(cutoffMillis)`. Open the DB via a `SqlCipherFactory` using
> the passphrase from `EncryptedSharedPreferences` (`Crypto.DB_KEY_ALIAS`).
> Implement `data/repo/SummaryRepo.kt` as the REAL impl of Phase 4's interface
> and replace the fake Hilt binding. Store TEXT ONLY — never audio.
>
> **Summary screen (`ui/summary/`):** show contact/number + date and the list of
> promises (task, optional `[Qty]`, proof quote in quote style; "Unconfirmed"
> items flagged but shown). Two full-width ≥56dp buttons: **💾 Save to History**
> (writes via `SummaryRepo`) and **✉️ Send to <Name> via SMS**. The post-call
> loading modal from Phase 2 navigates here.
>
> **SMS brokerage (`export/SmsIntentBuilder.kt`):** `Intent(ACTION_SENDTO,
> Uri.parse("smsto:<number>"))` with the markdown summary in `sms_body`; launch
> it (never auto-send). Rendering comes from `summary/SummaryMarkdown.kt` (single
> source of truth, reused by export).
>
> **History (`ui/history/`):** newest-first simple list; tap → read-only detail
> with Export/Send/Delete.
>
> **Settings (`ui/settings/`):** rows for STT language lock (AUTO/EN — changing
> triggers the Phase 1 downloader for the needed model), TTS notice toggle,
> auto-cleanup toggle (>30 days, OFF by default; when ON run
> `deleteOlderThan(now-30d)` on app start), and **Clear History** with a
> confirmation dialog. All bound to `SettingsRepo`.
>
> **Export (`export/SummaryExporter.kt`):** export a summary (or full history) to
> Text and PDF (`android.graphics.pdf.PdfDocument`, large fonts, paginated),
> shared via `ACTION_SEND` + a `FileProvider` (declare it in the manifest scoped
> to an export cache dir).
>
> **Constraints:** No network. Text-only persistence. MVVM + `StateFlow`. All
> strings in `strings.xml`; WCAG AA (contrast, ≥48dp, TalkBack). Provide tests:
> `SummaryDaoTest`, `SmsIntentBuilderTest`, `SummaryMarkdownTest`,
> `SummaryExporterTest`, and Espresso `SummaryActionsTest` (Save writes a row;
> Send fires `ACTION_SENDTO` via Espresso-Intents `intended()`) and
> `AccessibilityTest`.
>
> **Done when:** `assembleDebug` + `testDebugUnitTest` pass, history persists
> encrypted across restarts, Send opens the messaging app prefilled, and export
> produces a shareable PDF/Text file.

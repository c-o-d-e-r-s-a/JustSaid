package com.justsaid.app.llm

import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.Transcript

/**
 * Phase 4 boundary: consumes the [Transcript] Phase 3 produced and returns the
 * validated promise summary (every item's proof quote code-verified against the
 * transcript, Constitution G1). [session] supplies metadata for the summary
 * header — its wav file is never touched here; the pipeline owns deletion
 * (Constitution A1).
 */
interface LlmSummarizer {
    suspend fun summarize(session: RecordedSession, transcript: Transcript): JustSaidResult<CallSummary>
}

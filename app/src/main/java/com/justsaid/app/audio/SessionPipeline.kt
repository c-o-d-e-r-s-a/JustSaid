package com.justsaid.app.audio

import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult

/**
 * Post-capture processing entry point. The implementation owns WAV deletion in
 * `finally` (Constitution A1). Phase 3/4 wire STT and summarization here.
 */
interface SessionPipeline {
    suspend fun process(session: RecordedSession): JustSaidResult<CallSummary>
}

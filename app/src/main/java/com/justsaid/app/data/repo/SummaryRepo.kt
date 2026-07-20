package com.justsaid.app.data.repo

import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult

/**
 * Phase 5 boundary: persists validated summaries to the encrypted DB. Phase 4 only
 * depends on this interface (bound to [InMemorySummaryRepo] until Phase 5 lands the
 * Room/SQLCipher implementation).
 */
interface SummaryRepo {
    /** Persists [summary] and returns it with its storage id assigned. */
    suspend fun save(summary: CallSummary): JustSaidResult<CallSummary>
}

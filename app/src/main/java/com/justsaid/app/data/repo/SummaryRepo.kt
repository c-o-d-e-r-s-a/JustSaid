package com.justsaid.app.data.repo

import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult

/**
 * Phase 5 boundary: persists validated summaries to the encrypted DB. Phase 4 only
 * depends on this interface; the Room/SQLCipher implementation is [RoomSummaryRepo].
 */
interface SummaryRepo {
    /** Persists [summary] and returns it with its storage id assigned. */
    suspend fun save(summary: CallSummary): JustSaidResult<CallSummary>
}

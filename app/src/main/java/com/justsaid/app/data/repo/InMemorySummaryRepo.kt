package com.justsaid.app.data.repo

import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stand-in until Phase 5 lands the Room/SQLCipher store: keeps summaries in
 * process memory only (text, never audio) and assigns monotonically increasing
 * ids. Lets the full Phase 4 pipeline run and be tested end-to-end before the
 * encrypted DB exists. Phase 5 replaces the Hilt binding.
 */
@Singleton
class InMemorySummaryRepo @Inject constructor() : SummaryRepo {

    private val nextId = AtomicLong(1)

    private val saved = mutableListOf<CallSummary>()

    override suspend fun save(summary: CallSummary): JustSaidResult<CallSummary> {
        val withId = summary.copy(id = nextId.getAndIncrement())
        synchronized(saved) { saved += withId }
        return JustSaidResult.Success(withId)
    }
}

package com.justsaid.app.data.repo

import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.IoDispatcher
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.PromiseItem
import com.justsaid.app.core.Speaker
import com.justsaid.app.data.db.CallSummaryEntity
import com.justsaid.app.data.db.PromiseItemEntity
import com.justsaid.app.data.db.SummaryDao
import com.justsaid.app.data.db.SummaryWithItems
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real [SummaryRepo]: persists text-only summaries in the SQLCipher-encrypted
 * Room store (replaces Phase 4's in-memory fake). Also exposes the reads/deletes
 * the history and settings screens need (Text Lifecycle T1–T3).
 */
@Singleton
class RoomSummaryRepo @Inject constructor(
    private val dao: SummaryDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : SummaryRepo {

    override suspend fun save(summary: CallSummary): JustSaidResult<CallSummary> =
        withContext(dispatcher) {
            try {
                val id = dao.insertSummaryWithItems(summary.toEntity(), summary.items.toEntities())
                JustSaidResult.Success(summary.copy(id = id))
            } catch (e: Exception) {
                JustSaidResult.Failure("Could not save the summary", e)
            }
        }

    /** All saved summaries, newest first. */
    fun observeAll(): Flow<List<CallSummary>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getById(id: Long): CallSummary? =
        withContext(dispatcher) { dao.getById(id)?.toDomain() }

    suspend fun deleteById(id: Long) =
        withContext(dispatcher) { dao.deleteById(id) }

    /** T3: Clear History. */
    suspend fun deleteAll() =
        withContext(dispatcher) { dao.deleteAll() }

    /** T2: auto-cleanup of summaries older than [cutoffMillis]. */
    suspend fun deleteOlderThan(cutoffMillis: Long) =
        withContext(dispatcher) { dao.deleteOlderThan(cutoffMillis) }
}

private fun CallSummary.toEntity() = CallSummaryEntity(
    sessionLabel = sessionLabel,
    createdAt = createdAt,
    fullTranscript = fullTranscript,
)

private fun List<PromiseItem>.toEntities() = map {
    PromiseItemEntity(
        summaryId = 0L, // assigned inside the DAO transaction
        task = it.task,
        quantity = it.quantity,
        proofQuote = it.proofQuote,
        attributedTo = it.attributedTo.name,
        confirmed = it.confirmed,
    )
}

private fun SummaryWithItems.toDomain() = CallSummary(
    id = summary.id,
    sessionLabel = summary.sessionLabel,
    createdAt = summary.createdAt,
    items = items.map {
        PromiseItem(
            task = it.task,
            quantity = it.quantity,
            proofQuote = it.proofQuote,
            attributedTo = runCatching { Speaker.valueOf(it.attributedTo) }.getOrDefault(Speaker.UNKNOWN),
            confirmed = it.confirmed,
        )
    },
    fullTranscript = summary.fullTranscript,
)

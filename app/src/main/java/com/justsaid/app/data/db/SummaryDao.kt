package com.justsaid.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Encrypted-store access for summaries. Covers the full Text Lifecycle:
 * insert (T1), auto-cleanup by age (T2), Clear History (T3), and the reads the
 * history/export screens need (T4).
 */
@Dao
interface SummaryDao {

    @Insert
    suspend fun insertSummary(summary: CallSummaryEntity): Long

    @Insert
    suspend fun insertItems(items: List<PromiseItemEntity>)

    /** Atomically persists the summary header and its items; returns the new summary id. */
    @Transaction
    suspend fun insertSummaryWithItems(
        summary: CallSummaryEntity,
        items: List<PromiseItemEntity>,
    ): Long {
        val summaryId = insertSummary(summary)
        insertItems(items.map { it.copy(summaryId = summaryId) })
        return summaryId
    }

    @Transaction
    @Query("SELECT * FROM call_summaries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SummaryWithItems>>

    @Transaction
    @Query("SELECT * FROM call_summaries WHERE id = :id")
    suspend fun getById(id: Long): SummaryWithItems?

    @Query("DELETE FROM call_summaries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_summaries")
    suspend fun deleteAll()

    /** T2 auto-cleanup: removes summaries (and cascaded items) created before [cutoffMillis]. */
    @Query("DELETE FROM call_summaries WHERE createdAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}

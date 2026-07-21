package com.justsaid.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted call summary header (Constitution T1: text only, retained until the
 * user deletes it). The raw audio never reaches this layer (A2).
 */
@Entity(tableName = "call_summaries")
data class CallSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val contactName: String?,
    val phoneNumber: String,
    val createdAt: Long,
    val fullTranscript: String,
)

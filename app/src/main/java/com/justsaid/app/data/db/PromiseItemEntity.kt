package com.justsaid.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One extracted promise, owned by a [CallSummaryEntity]. Cascade delete keeps
 * items from outliving their summary (Clear History / auto-cleanup wipe both).
 */
@Entity(
    tableName = "promise_items",
    foreignKeys = [
        ForeignKey(
            entity = CallSummaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["summaryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("summaryId")],
)
data class PromiseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val summaryId: Long,
    val task: String,
    val quantity: String?,
    val proofQuote: String,
    val attributedTo: String,
    val confirmed: Boolean,
)

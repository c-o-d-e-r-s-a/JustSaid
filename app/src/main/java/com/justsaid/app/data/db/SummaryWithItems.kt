package com.justsaid.app.data.db

import androidx.room.Embedded
import androidx.room.Relation

/** A summary row together with its promise items (Room @Relation projection). */
data class SummaryWithItems(
    @Embedded val summary: CallSummaryEntity,
    @Relation(parentColumn = "id", entityColumn = "summaryId")
    val items: List<PromiseItemEntity>,
)

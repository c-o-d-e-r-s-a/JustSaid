package com.justsaid.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The encrypted store (opened through SQLCipher via [SqlCipherFactory]).
 * Text only — transcripts and promises. Audio never reaches this layer (A2).
 */
@Database(
    entities = [CallSummaryEntity::class, PromiseItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class JustSaidDatabase : RoomDatabase() {
    abstract fun summaryDao(): SummaryDao
}

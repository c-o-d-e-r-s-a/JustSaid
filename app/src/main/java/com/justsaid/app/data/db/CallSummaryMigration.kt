package com.justsaid.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Replaces dialer [contactName]/[phoneNumber] columns with optional [sessionLabel]. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS call_summaries_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionLabel TEXT,
                createdAt INTEGER NOT NULL,
                fullTranscript TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO call_summaries_new (id, sessionLabel, createdAt, fullTranscript)
            SELECT id,
                CASE
                    WHEN contactName IS NOT NULL AND contactName != '' THEN contactName
                    ELSE phoneNumber
                END,
                createdAt,
                fullTranscript
            FROM call_summaries
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE call_summaries")
        db.execSQL("ALTER TABLE call_summaries_new RENAME TO call_summaries")
    }
}

package com.justsaid.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Companion task 5: v1 dialer rows (contactName + phoneNumber) migrate to sessionLabel.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class CallSummaryMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migrate1To2_preservesContactNameAsSessionLabel() = runTest {
        val dbName = "migration-test-preserves"
        seedV1Database(
            dbName,
            "INSERT INTO call_summaries (contactName, phoneNumber, createdAt, fullTranscript) " +
                "VALUES ('Ada', '+15555550123', 100, 'You: I''ll buy milk')",
        )

        val db = openWithMigration(dbName)
        val row = db.summaryDao().getById(1)
        assertThat(row).isNotNull()
        assertThat(row!!.summary.sessionLabel).isEqualTo("Ada")
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2_fallsBackToPhoneNumberWhenContactNameMissing() = runTest {
        val dbName = "migration-test-fallback"
        seedV1Database(
            dbName,
            "INSERT INTO call_summaries (contactName, phoneNumber, createdAt, fullTranscript) " +
                "VALUES (NULL, '+15555550123', 200, 'text')",
        )

        val db = openWithMigration(dbName)
        val row = db.summaryDao().getById(1)
        assertThat(row).isNotNull()
        assertThat(row!!.summary.sessionLabel).isEqualTo("+15555550123")
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun seedV1Database(dbName: String, insertSql: String) {
        context.deleteDatabase(dbName)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(V1SchemaCallback())
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        db.execSQL(insertSql)
        db.close()
        helper.close()
    }

    private fun openWithMigration(dbName: String): JustSaidDatabase =
        Room.databaseBuilder(context, JustSaidDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .build()

    private class V1SchemaCallback : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE call_summaries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    contactName TEXT,
                    phoneNumber TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    fullTranscript TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE promise_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    summaryId INTEGER NOT NULL,
                    task TEXT NOT NULL,
                    quantity TEXT,
                    proofQuote TEXT NOT NULL,
                    attributedTo TEXT NOT NULL,
                    confirmed INTEGER NOT NULL,
                    FOREIGN KEY(summaryId) REFERENCES call_summaries(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX index_promise_items_summaryId ON promise_items(summaryId)")
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}

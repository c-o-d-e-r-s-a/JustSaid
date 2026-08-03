package com.justsaid.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * DAO contract (Text Lifecycle T1–T3): transactional insert of summary+items,
 * newest-first list, per-id read, delete-all, and the >30d auto-cleanup query.
 * Runs against in-memory Room on the JVM — encryption is supplied by the
 * SQLCipher factory at the DB-open layer, not by the queries under test.
 * A plain [android.app.Application] is used because [com.justsaid.app.JustSaidApp]'s
 * startup needs the AndroidKeyStore, which does not exist under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class SummaryDaoTest {

    private lateinit var db: JustSaidDatabase
    private lateinit var dao: SummaryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JustSaidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.summaryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun summary(createdAt: Long, label: String? = "Ada") = CallSummaryEntity(
        sessionLabel = label,
        createdAt = createdAt,
        fullTranscript = "You: I'll buy milk",
    )

    private fun items(count: Int) = List(count) { i ->
        PromiseItemEntity(
            summaryId = 0L,
            task = "Task $i",
            quantity = if (i == 0) "2" else null,
            proofQuote = "proof $i",
            attributedTo = "LOCAL",
            confirmed = true,
        )
    }

    @Test
    fun insertAndReadBack_withItems() = runTest {
        val id = dao.insertSummaryWithItems(summary(createdAt = 100L), items(2))

        val loaded = dao.getById(id)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.summary.sessionLabel).isEqualTo("Ada")
        assertThat(loaded.items).hasSize(2)
        assertThat(loaded.items.map { it.summaryId }.distinct()).containsExactly(id)
        assertThat(loaded.items.first { it.task == "Task 0" }.quantity).isEqualTo("2")
    }

    @Test
    fun observeAll_isNewestFirst() = runTest {
        dao.insertSummaryWithItems(summary(createdAt = 100L, label = "Old"), emptyList())
        dao.insertSummaryWithItems(summary(createdAt = 300L, label = "New"), emptyList())
        dao.insertSummaryWithItems(summary(createdAt = 200L, label = "Mid"), emptyList())

        val all = dao.observeAll().first()
        assertThat(all.map { it.summary.sessionLabel }).containsExactly("New", "Mid", "Old").inOrder()
    }

    @Test
    fun deleteOlderThan_removesOnlyOldRows() = runTest {
        dao.insertSummaryWithItems(summary(createdAt = 100L, label = "Old"), items(1))
        dao.insertSummaryWithItems(summary(createdAt = 500L, label = "New"), items(1))

        dao.deleteOlderThan(cutoffMillis = 300L)

        val remaining = dao.observeAll().first()
        assertThat(remaining.map { it.summary.sessionLabel }).containsExactly("New")
    }

    @Test
    fun deleteAll_wipesEverything() = runTest {
        dao.insertSummaryWithItems(summary(createdAt = 100L), items(3))
        dao.insertSummaryWithItems(summary(createdAt = 200L), items(1))

        dao.deleteAll()

        assertThat(dao.observeAll().first()).isEmpty()
    }

    @Test
    fun deleteById_cascadesToItems() = runTest {
        val id = dao.insertSummaryWithItems(summary(createdAt = 100L), items(2))
        val keptId = dao.insertSummaryWithItems(summary(createdAt = 200L, label = "Kept"), items(1))

        dao.deleteById(id)

        assertThat(dao.getById(id)).isNull()
        val kept = dao.getById(keptId)
        assertThat(kept).isNotNull()
        assertThat(kept!!.items).hasSize(1)
    }
}

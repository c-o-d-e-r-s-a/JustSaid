package com.justsaid.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.PromiseItem
import com.justsaid.app.core.Speaker
import com.justsaid.app.data.db.JustSaidDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Companion task 5: persistence is explicit via [SummaryRepo.save]; new rows store
 * optional [CallSummary.sessionLabel] only — no queried phone metadata.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class RoomSummaryRepoCompanionTest {

    private lateinit var db: JustSaidDatabase
    private lateinit var repo: RoomSummaryRepo
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JustSaidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomSummaryRepo(db.summaryDao(), dispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun save_persistsSessionLabelWithoutPhoneMetadata() = runTest(dispatcher) {
        val summary = CallSummary(
            id = 0L,
            sessionLabel = "dentist call",
            createdAt = 500L,
            items = listOf(
                PromiseItem(
                    task = "Call back",
                    quantity = null,
                    proofQuote = "I'll call back",
                    attributedTo = Speaker.UNKNOWN,
                    confirmed = false,
                ),
            ),
            fullTranscript = "I'll call back",
        )

        val saved = when (val result = repo.save(summary)) {
            is JustSaidResult.Success -> result.value
            is JustSaidResult.Failure -> error(result.reason)
        }

        val loaded = repo.getById(saved.id)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.sessionLabel).isEqualTo("dentist call")
        assertThat(loaded.phoneNumber).isEmpty()
        assertThat(loaded.items.single().proofQuote).isEqualTo("I'll call back")
    }

    @Test
    fun save_allowsNullSessionLabel() = runTest(dispatcher) {
        val summary = CallSummary(
            id = 0L,
            sessionLabel = null,
            createdAt = 100L,
            items = emptyList(),
            fullTranscript = "hello",
        )

        val saved = when (val result = repo.save(summary)) {
            is JustSaidResult.Success -> result.value
            is JustSaidResult.Failure -> error(result.reason)
        }

        assertThat(repo.getById(saved.id)?.sessionLabel).isNull()
    }
}

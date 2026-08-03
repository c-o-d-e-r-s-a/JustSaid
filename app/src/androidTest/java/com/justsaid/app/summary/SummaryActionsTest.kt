package com.justsaid.app.summary

import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.platform.app.InstrumentationRegistry
import com.justsaid.app.R
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.PromiseItem
import com.justsaid.app.core.Speaker
import com.justsaid.app.data.repo.RoomSummaryRepo
import com.justsaid.app.ui.summary.SummaryScreen
import com.justsaid.app.ui.summary.SummaryViewModel
import com.justsaid.app.ui.theme.JustSaidTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Phase 5 acceptance: Save writes a row into the encrypted store; "Send via SMS"
 * fires `ACTION_SENDTO` with the `smsto:` number and prefilled body (asserted via
 * Espresso-Intents; nothing is ever auto-sent).
 */
@HiltAndroidTest
class SummaryActionsTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Inject lateinit var summaryEvents: SummaryEvents
    @Inject lateinit var repo: RoomSummaryRepo
    @Inject lateinit var llmEngine: com.justsaid.app.llm.LlmEngine

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { repo.deleteAll() }
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
        summaryEvents.clear()
    }

    private fun unsavedSummary() = CallSummary(
        id = 0L,
        contactName = "Ada",
        phoneNumber = "+15555550123",
        createdAt = System.currentTimeMillis(),
        items = listOf(
            PromiseItem(
                task = "Buy milk",
                quantity = null,
                proofQuote = "I'll buy milk",
                attributedTo = Speaker.LOCAL,
                confirmed = true,
            ),
        ),
        fullTranscript = "You: I'll buy milk",
    )

    private fun showSummaryScreen() {
        summaryEvents.publish(unsavedSummary())
        val viewModel = SummaryViewModel(summaryEvents, repo, llmEngine)
        composeRule.setContent {
            JustSaidTheme {
                SummaryScreen(onDone = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun save_writesRowIntoEncryptedStore() {
        showSummaryScreen()

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.summary_save_content_desc))
            .performClick()

        val saved = runBlocking {
            withTimeout(5_000) { repo.observeAll().first { it.isNotEmpty() } }
        }
        assertEquals(1, saved.size)
        assertEquals("Ada", saved.first().contactName)
        assertEquals("Buy milk", saved.first().items.single().task)
    }

    @Test
    fun send_firesActionSendToWithPrefilledBody() {
        showSummaryScreen()

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.summary_send_content_desc, "Ada"))
            .performClick()

        intended(
            allOf(
                hasAction(Intent.ACTION_SENDTO),
                hasData(android.net.Uri.parse("smsto:+15555550123")),
                hasExtra(
                    org.hamcrest.CoreMatchers.equalTo("sms_body"),
                    containsString("Buy milk"),
                ),
            ),
        )
    }
}

package com.justsaid.app

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.PromiseItem
import com.justsaid.app.core.Speaker
import com.justsaid.app.ui.history.HistoryContent
import com.justsaid.app.ui.history.HistoryUiState
import com.justsaid.app.ui.settings.SettingsContent
import com.justsaid.app.ui.settings.SettingsUiState
import com.justsaid.app.ui.summary.SummaryContent
import com.justsaid.app.ui.summary.SummaryUiState
import com.justsaid.app.ui.theme.JustSaidTheme
import org.junit.Rule
import org.junit.Test

/**
 * Constitution U2 on the Phase 5 screens: every action is reachable through a
 * TalkBack content description and meets the >=48dp touch-target minimum
 * (primary actions are >=56dp).
 */
class AccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val summary = CallSummary(
        id = 1L,
        sessionLabel = "Ada",
        createdAt = System.currentTimeMillis(),
        items = listOf(
            PromiseItem(
                task = "Buy milk",
                quantity = "2",
                proofQuote = "I'll buy milk",
                attributedTo = Speaker.UNKNOWN,
                confirmed = false,
            ),
        ),
        fullTranscript = "I'll buy milk",
    )

    @Test
    fun summaryScreen_actionsHaveDescriptions_andBigTouchTargets() {
        composeRule.setContent {
            JustSaidTheme {
                SummaryContent(
                    state = SummaryUiState(summary = summary, saved = false),
                    onSave = {},
                    onReadModeSelected = {},
                    onDone = {},
                )
            }
        }

        listOf(
            context.getString(R.string.summary_save_content_desc),
            context.getString(R.string.summary_share_content_desc),
            context.getString(R.string.summary_done_content_desc),
        ).forEach { desc ->
            composeRule.onNodeWithContentDescription(desc)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(56.dp)
        }
    }

    @Test
    fun historyScreen_rowsAndActions_areAccessible()  {
        composeRule.setContent {
            JustSaidTheme {
                HistoryContent(
                    state = HistoryUiState(summaries = listOf(summary)),
                    onOpenSummary = {},
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.history_row_content_desc, "Ada"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.history_back_content_desc))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun settingsScreen_togglesAndButtons_areAccessible() {
        composeRule.setContent {
            JustSaidTheme {
                SettingsContent(
                    state = SettingsUiState(),
                    onLanguageSelected = {},
                    onSpokenLanguageToggled = { _, _ -> },
                    onTtsNoticeChanged = {},
                    onAutoCleanupChanged = {},
                    onClearHistory = {},
                    onBack = {},
                )
            }
        }

        listOf(
            context.getString(R.string.settings_language_auto_content_desc),
            context.getString(R.string.settings_language_en_content_desc),
            context.getString(R.string.settings_clear_content_desc),
        ).forEach { desc ->
            composeRule.onNodeWithContentDescription(desc)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.settings_tts_content_desc))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.settings_cleanup_content_desc))
            .assertIsDisplayed()
    }
}

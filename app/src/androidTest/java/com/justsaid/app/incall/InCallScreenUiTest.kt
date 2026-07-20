package com.justsaid.app.incall

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.justsaid.app.R
import com.justsaid.app.ui.incall.CallPhase
import com.justsaid.app.ui.incall.InCallContent
import com.justsaid.app.ui.incall.InCallUiState
import com.justsaid.app.ui.theme.JustSaidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Stateless UI checks for the in-call overlay: the LISTEN toggle and the post-call modal. */
class InCallScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun listenToggle_isDisplayed_andInvokesCallback() {
        var toggled: Boolean? = null
        composeRule.setContent {
            JustSaidTheme {
                InCallContent(
                    state = InCallUiState(
                        visible = true,
                        phase = CallPhase.ACTIVE,
                        displayName = "Mum",
                        listenEnabled = false,
                    ),
                    onToggleListen = { toggled = it },
                    onAnswer = {},
                    onHangup = {},
                )
            }
        }

        val desc = context.getString(R.string.incall_listen_content_desc_off)
        composeRule.onNodeWithContentDescription(desc).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(desc).performClick()
        assertTrue("Tapping LISTEN must request turning it on", toggled == true)
    }

    @Test
    fun loadingModal_shows_whenProcessing() {
        composeRule.setContent {
            JustSaidTheme {
                InCallContent(
                    state = InCallUiState(
                        visible = true,
                        phase = CallPhase.DISCONNECTED,
                        displayName = "Mum",
                        showLoadingModal = true,
                    ),
                    onToggleListen = {},
                    onAnswer = {},
                    onHangup = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.incall_processing)).assertIsDisplayed()
    }
}

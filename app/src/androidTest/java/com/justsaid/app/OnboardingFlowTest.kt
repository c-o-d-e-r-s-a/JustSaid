package com.justsaid.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.justsaid.app.ui.onboarding.DownloadContent
import com.justsaid.app.ui.onboarding.DownloadUiState
import com.justsaid.app.ui.onboarding.LegalContent
import com.justsaid.app.ui.theme.JustSaidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun legalScreen_hasNoSkip_onlyAgreeAdvances() {
        var agreed = false
        composeRule.setContent {
            JustSaidTheme {
                LegalContent(onAgree = { agreed = true })
            }
        }

        // The only affordance forward is the "I Agree" button; Home is unreachable here.
        composeRule.onNodeWithText(context.getString(R.string.home_title)).assertDoesNotExist()

        val agreeDesc = context.getString(R.string.legal_agree_content_desc)
        composeRule.onNodeWithContentDescription(agreeDesc).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(agreeDesc).performClick()

        assertTrue("Agreeing must be the action that advances onboarding", agreed)
    }

    @Test
    fun downloadGate_blocksHome_untilComplete() {
        composeRule.setContent {
            JustSaidTheme {
                DownloadContent(state = DownloadUiState(isComplete = false), onRetry = {})
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.download_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_title)).assertDoesNotExist()
    }

    @Test
    fun downloadGate_showsRetry_onError() {
        composeRule.setContent {
            JustSaidTheme {
                DownloadContent(state = DownloadUiState(isError = true), onRetry = {})
            }
        }

        val retryDesc = context.getString(R.string.download_retry_content_desc)
        composeRule.onNodeWithContentDescription(retryDesc).assertIsDisplayed()
    }
}

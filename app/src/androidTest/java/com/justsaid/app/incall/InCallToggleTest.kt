package com.justsaid.app.incall

import com.justsaid.app.audio.CaptureController
import com.justsaid.app.audio.PipelineState
import com.justsaid.app.telecom.CallState
import com.justsaid.app.telecom.CallStateHolder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Device-free end-to-end of the capture handoff: with the [com.justsaid.app.audio.FileAudioSource]
 * bound via [TestCaptureModule], toggling LISTEN during an Active call then disconnecting must
 * produce a wav and invoke [com.justsaid.app.audio.CallPipeline.process] (surfaced as the loading
 * modal in the UI). Call lifecycle is injected via [CallStateHolder] instead of a live call
 * (equivalent to `adb shell telecom add-call`, TESTING.md §B).
 */
@HiltAndroidTest
class InCallToggleTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var callStateHolder: CallStateHolder
    @Inject lateinit var captureController: CaptureController
    @Inject lateinit var pipeline: CountingPipeline

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun toggleOn_thenDisconnect_capturesAndInvokesPipeline() {
        callStateHolder.update(CallState.Active("+15555550123"))
        captureController.setListen(true)

        assertTrue(
            "capture should start once LISTEN is on during an active call",
            waitUntil { captureController.isCapturing.value },
        )

        callStateHolder.update(CallState.Disconnected("+15555550123"))

        assertTrue(
            "pipeline must run on disconnect when audio was captured",
            waitUntil { pipeline.count == 1 },
        )
        val recorded = pipeline.last
        assertNotNull(recorded)
        assertTrue(recorded!!.wavFile.exists())
        assertEquals(PipelineState.DONE, captureController.pipelineState.value)
    }

    @Test
    fun noToggle_thenDisconnect_doesNotInvokePipeline() {
        callStateHolder.update(CallState.Active("+15555550999"))
        callStateHolder.update(CallState.Disconnected("+15555550999"))

        // Give the controller a moment to (not) do anything.
        Thread.sleep(300)
        assertEquals(0, pipeline.count)
    }

    /** Polls [condition] until true or timeout; async capture runs on Dispatchers.Default. */
    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }
}

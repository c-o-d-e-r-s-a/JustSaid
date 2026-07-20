package com.justsaid.app.audio

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.data.contacts.ContactResolver
import com.justsaid.app.data.repo.SettingsRepo
import com.justsaid.app.telecom.CallState
import com.justsaid.app.telecom.CallStateHolder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureControllerTest {

    private lateinit var tempDir: File
    private lateinit var holder: CallStateHolder
    private lateinit var pipeline: RecordingPipeline
    private lateinit var fileProvider: RecordingWavFileProvider
    private lateinit var settings: SettingsRepo

    @Before
    fun setUp() {
        tempDir = createTempDir(prefix = "capture_ctrl_test")
        holder = CallStateHolder()
        pipeline = RecordingPipeline()
        fileProvider = RecordingWavFileProvider(tempDir)
        settings = mockk(relaxed = true)
        every { settings.alwaysListen } returns flowOf(false)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `does not capture while listen is off`() = runTest {
        val controller = CaptureController(
            holder, { FakeAudioSource() }, fileProvider, FakeContactResolver(null),
            pipeline, settings, StandardTestDispatcher(testScheduler),
        )
        holder.update(CallState.Active("123"))
        advanceUntilIdle()
        assertThat(controller.isCapturing.value).isFalse()
        assertThat(fileProvider.created).isEmpty()
    }

    @Test
    fun `captures only while listen on and call active, into cacheDir`() = runTest {
        val controller = CaptureController(
            holder, { FakeAudioSource() }, fileProvider, FakeContactResolver(null),
            pipeline, settings, StandardTestDispatcher(testScheduler),
        )
        holder.update(CallState.Active("123"))
        advanceUntilIdle()

        controller.setListen(true)
        advanceUntilIdle()
        assertThat(controller.isCapturing.value).isTrue()
        assertThat(fileProvider.created).hasSize(1)
        assertThat(fileProvider.created.first().parentFile).isEqualTo(tempDir)

        controller.setListen(false)
        advanceUntilIdle()
        assertThat(controller.isCapturing.value).isFalse()
    }

    @Test
    fun `on disconnect with captured audio, invokes pipeline with the wav`() = runTest {
        val controller = CaptureController(
            holder, { FakeAudioSource() }, fileProvider, FakeContactResolver("Mum"),
            pipeline, settings, StandardTestDispatcher(testScheduler),
        )
        holder.update(CallState.Active("555"))
        advanceUntilIdle()
        controller.setListen(true)
        advanceUntilIdle()

        holder.update(CallState.Disconnected("555"))
        advanceUntilIdle()

        assertThat(pipeline.callCount).isEqualTo(1)
        val recorded = pipeline.processed!!
        assertThat(recorded.wavFile.exists()).isTrue()
        assertThat(recorded.tier).isEqualTo(CaptureTier.MIC_ONLY)
        assertThat(recorded.channels).isEqualTo(1)
        assertThat(recorded.phoneNumber).isEqualTo("555")
        assertThat(recorded.contactName).isEqualTo("Mum")
        assertThat(controller.pipelineState.value).isEqualTo(PipelineState.DONE)
    }

    @Test
    fun `on disconnect without listening, pipeline is never called`() = runTest {
        val controller = CaptureController(
            holder, { FakeAudioSource() }, fileProvider, FakeContactResolver(null),
            pipeline, settings, StandardTestDispatcher(testScheduler),
        )
        holder.update(CallState.Active("999"))
        advanceUntilIdle()
        holder.update(CallState.Disconnected("999"))
        advanceUntilIdle()

        assertThat(pipeline.callCount).isEqualTo(0)
        // Nothing to process -> straight to DONE so the UI can dismiss (no PROCESSING modal).
        assertThat(controller.pipelineState.value).isEqualTo(PipelineState.DONE)
    }

    private class FakeAudioSource : AudioSource {
        override val tier = CaptureTier.MIC_ONLY
        override val channels = 1
        override fun frames(): Flow<ShortArray> = flow {
            repeat(5) { emit(ShortArray(160) { 1 }) }
        }
    }

    private class FakeContactResolver(private val name: String?) : ContactResolver {
        override suspend fun resolve(phoneNumber: String): String? = name
    }

    private class RecordingPipeline : CallPipeline {
        var processed: RecordedCall? = null
        var callCount = 0
        override suspend fun process(call: RecordedCall) {
            processed = call
            callCount++
        }
    }

    private class RecordingWavFileProvider(private val dir: File) : WavFileProvider {
        val created = mutableListOf<File>()
        private var counter = 0
        override fun newWavFile(): File =
            File(dir, "call_${counter++}.wav").also { created.add(it) }
    }
}

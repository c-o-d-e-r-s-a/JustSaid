package com.justsaid.app.capture

import com.justsaid.app.audio.AudioSource
import com.justsaid.app.audio.AudioSourceFactory
import com.justsaid.app.audio.FileAudioSource
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.audio.SessionPipeline
import com.justsaid.app.audio.WavFileProvider
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment
import com.justsaid.app.di.CaptureBindsModule
import com.justsaid.app.di.CaptureModule
import com.justsaid.app.pipeline.SessionPipelineImpl
import com.justsaid.app.session.SessionWavFiles
import com.justsaid.app.summary.PendingSummaryHandoff
import com.justsaid.app.summary.SummaryEvents
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Configurable pipeline outcome for instrumented session smoke tests. */
enum class FakePipelineOutcome {
  Success,
  SttFailure,
  SummaryFailure,
  ProcessingThrow,
}

/**
 * Replaces production capture bindings for instrumented capture tests: streams a fixture
 * WAV instead of the microphone and runs a fake STT/summary pipeline with real handoff
 * and WAV deletion semantics.
 */
@Module
@TestInstallIn(
  components = [SingletonComponent::class],
  replaces = [CaptureModule::class, CaptureBindsModule::class],
)
object TestCaptureModule {

  private val summaryEvents = SummaryEvents()

  /** Set by the test before Hilt builds the component. */
  var fixtureWavFile: File? = null

  /** Last private session WAV created by [provideWavFileProvider]. */
  @Volatile
  var lastWavFile: File? = null

  /** When true, [provideAudioSourceFactory] emits no frames (no-audio failure path). */
  @Volatile
  var useEmptyAudioSource: Boolean = false

  /** Drives fake STT/summary behavior in [provideSessionPipeline]. */
  @Volatile
  var pipelineOutcome: FakePipelineOutcome = FakePipelineOutcome.Success

  @Provides
  @Singleton
  fun provideFixtureFile(): File = checkNotNull(fixtureWavFile)

  @Provides
  @Singleton
  fun provideAudioSourceFactory(fixture: File): AudioSourceFactory =
    AudioSourceFactory {
      if (useEmptyAudioSource) EmptyAudioSource() else FileAudioSource(fixture)
    }

  @Provides
  @Singleton
  fun provideWavFileProvider(): WavFileProvider = WavFileProvider {
    File.createTempFile("justsaid_session_test_", ".wav").also { lastWavFile = it }
  }

  @Provides
  @Singleton
  fun provideSummaryEvents(): SummaryEvents = summaryEvents

  @Provides
  @Singleton
  fun providePendingSummaryHandoff(events: SummaryEvents): PendingSummaryHandoff = events

  @Provides
  @Singleton
  fun provideSessionPipeline(handoff: PendingSummaryHandoff): SessionPipeline =
    object : SessionPipeline {
      override suspend fun process(session: RecordedSession): JustSaidResult<CallSummary> {
        var result: JustSaidResult<CallSummary>? = null
        try {
          result = when (pipelineOutcome) {
            FakePipelineOutcome.Success ->
              JustSaidResult.Success(summaryFor(session))
            FakePipelineOutcome.SttFailure ->
              JustSaidResult.Failure(STT_FAILED)
            FakePipelineOutcome.SummaryFailure ->
              JustSaidResult.Failure(SUMMARY_FAILED)
            FakePipelineOutcome.ProcessingThrow ->
              throw IllegalStateException("processing exploded")
          }
        } finally {
          SessionWavFiles.deleteVerified(session.wavFile)
        }
        return fakeAfterWavDeletion(session.wavFile, result, handoff)
      }
    }

  private fun fakeAfterWavDeletion(
    wavFile: File,
    result: JustSaidResult<CallSummary>?,
    handoff: PendingSummaryHandoff,
  ): JustSaidResult<CallSummary> {
    val outcome = SessionPipelineImpl.wavDeletionOutcome(wavFile.exists(), result)
    if (outcome is JustSaidResult.Success && outcome.value.id == 0L) {
      handoff.publish(outcome.value)
    }
    return outcome
  }

  private fun summaryFor(session: RecordedSession): CallSummary {
    val transcript = Transcript(
      listOf(TranscriptSegment(Speaker.UNKNOWN, "I'll buy milk", 0L, 900L)),
    )
    return CallSummary(
      id = 0L,
      sessionLabel = session.sessionLabel,
      createdAt = session.startedAt,
      items = emptyList(),
      fullTranscript = transcript.plainText(),
    )
  }

  /** Audio source that completes without emitting PCM (empty capture). */
  private class EmptyAudioSource : AudioSource {
    override val tier = com.justsaid.app.audio.CaptureTier.MIC_ONLY
    override val channels: Int = 1
    override fun frames(): Flow<ShortArray> = emptyFlow()
  }

  internal const val STT_FAILED: String = "Could not transcribe audio."
  internal const val SUMMARY_FAILED: String = "Could not summarize transcript."
}

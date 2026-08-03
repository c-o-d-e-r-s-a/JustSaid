package com.justsaid.app.capture

import com.justsaid.app.audio.AudioSourceFactory
import com.justsaid.app.audio.FileAudioSource
import com.justsaid.app.audio.WavFileProvider
import com.justsaid.app.audio.SessionPipeline
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.di.CaptureBindsModule
import com.justsaid.app.di.CaptureModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton

/**
 * Replaces production capture bindings for instrumented capture tests: streams a fixture
 * WAV instead of the microphone.
 */
@Module
@TestInstallIn(
  components = [SingletonComponent::class],
  replaces = [CaptureModule::class, CaptureBindsModule::class],
)
object TestCaptureModule {

  @Provides
  @Singleton
  fun provideFixtureFile(): File = checkNotNull(fixtureWavFile)

  @Provides
  @Singleton
  fun provideAudioSourceFactory(fixture: File): AudioSourceFactory =
    AudioSourceFactory { FileAudioSource(fixture) }

  @Provides
  @Singleton
  fun provideWavFileProvider(): WavFileProvider = WavFileProvider {
    File.createTempFile("justsaid_session_test_", ".wav")
  }

  @Provides
  @Singleton
  fun provideSessionPipeline(): SessionPipeline = object : SessionPipeline {
    override suspend fun process(session: RecordedSession): JustSaidResult<CallSummary> {
      session.wavFile.delete()
      return JustSaidResult.Success(
        CallSummary(
          id = 0L,
          sessionLabel = session.sessionLabel,
          createdAt = session.startedAt,
          items = emptyList(),
          fullTranscript = "",
        ),
      )
    }
  }

  /** Set by the test before Hilt builds the component. */
  var fixtureWavFile: File? = null
}

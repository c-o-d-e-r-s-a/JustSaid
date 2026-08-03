package com.justsaid.app.di

import android.content.Context
import com.justsaid.app.audio.AudioSourceFactory
import com.justsaid.app.audio.RealAudioSourceFactory
import com.justsaid.app.audio.SessionPipeline
import com.justsaid.app.audio.WavFileProvider
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.pipeline.SessionPipelineImpl
import com.justsaid.app.summary.PendingSummaryHandoff
import com.justsaid.app.summary.SummaryEvents
import com.justsaid.app.session.CaptureServiceGateway
import com.justsaid.app.session.MicrophoneCaptureServiceGateway
import com.justsaid.app.session.StaleAudioCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import javax.inject.Singleton

/** Phase 2 wiring for manual microphone capture. */
@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {

  @Provides
  @Singleton
  fun provideAudioSourceFactory(
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
  ): AudioSourceFactory = RealAudioSourceFactory(dispatcher)

  /** Fresh private wav in cacheDir per capture (never external storage — Constitution A2). */
  @Provides
  @Singleton
  fun provideWavFileProvider(
    @ApplicationContext context: Context,
  ): WavFileProvider = WavFileProvider {
    File.createTempFile(
      StaleAudioCleaner.SESSION_WAV_PREFIX,
      ".wav",
      context.cacheDir,
    )
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureBindsModule {

  @Binds
  @Singleton
  abstract fun bindSessionPipeline(impl: SessionPipelineImpl): SessionPipeline

  @Binds
  @Singleton
  abstract fun bindPendingSummaryHandoff(impl: SummaryEvents): PendingSummaryHandoff

  @Binds
  @Singleton
  abstract fun bindCaptureServiceGateway(impl: MicrophoneCaptureServiceGateway): CaptureServiceGateway
}

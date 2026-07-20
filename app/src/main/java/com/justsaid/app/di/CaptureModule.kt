package com.justsaid.app.di

import android.content.Context
import com.justsaid.app.audio.AudioRecordTierProbe
import com.justsaid.app.audio.AudioSourceFactory
import com.justsaid.app.audio.CallPipeline
import com.justsaid.app.audio.RealAudioSourceFactory
import com.justsaid.app.audio.WavFileProvider
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.data.contacts.ContactResolver
import com.justsaid.app.data.contacts.ContactResolverImpl
import com.justsaid.app.pipeline.CallPipelineImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import javax.inject.Singleton

/**
 * Phase 2 wiring for capture. The capture source is bound behind an interface so tests can
 * inject a `FileAudioSource` factory, and the pipeline is the NoOp deleter until Phase 3/4
 * replace it.
 */
@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {

    @Provides
    @Singleton
    fun provideAudioSourceFactory(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): AudioSourceFactory = RealAudioSourceFactory(AudioRecordTierProbe(), dispatcher)

    /** Fresh private wav in cacheDir per capture (never external storage — Constitution A2). */
    @Provides
    @Singleton
    fun provideWavFileProvider(
        @ApplicationContext context: Context,
    ): WavFileProvider = WavFileProvider {
        File(context.cacheDir, "call_${System.currentTimeMillis()}.wav")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureBindsModule {

    @Binds
    @Singleton
    abstract fun bindCallPipeline(impl: CallPipelineImpl): CallPipeline

    @Binds
    @Singleton
    abstract fun bindContactResolver(impl: ContactResolverImpl): ContactResolver
}

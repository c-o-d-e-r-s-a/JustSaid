package com.justsaid.app.incall

import android.content.Context
import com.justsaid.app.audio.AudioSource
import com.justsaid.app.audio.AudioSourceFactory
import com.justsaid.app.audio.CallPipeline
import com.justsaid.app.audio.CaptureTier
import com.justsaid.app.audio.FileAudioSource
import com.justsaid.app.audio.RecordedCall
import com.justsaid.app.audio.WavFileProvider
import com.justsaid.app.audio.WavWriter
import com.justsaid.app.data.contacts.ContactResolver
import com.justsaid.app.di.CaptureBindsModule
import com.justsaid.app.di.CaptureModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counting stand-in for the real pipeline: records that process() ran (and how many times)
 * WITHOUT deleting the wav, so the instrumented test can assert both invocation and output.
 */
@Singleton
class CountingPipeline @Inject constructor() : CallPipeline {
    @Volatile var count = 0
    @Volatile var last: RecordedCall? = null
    override suspend fun process(call: RecordedCall) {
        last = call
        count++
    }
}

/**
 * Swaps the capture wiring for device-free testing (TESTING.md §B.3): a [FileAudioSource]
 * streaming a generated fixture instead of the mic, a cacheDir wav target, a fake contact
 * resolver, and the [CountingPipeline].
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CaptureModule::class])
object TestCaptureModule {

    @Provides
    @Singleton
    fun provideAudioSourceFactory(
        @ApplicationContext context: Context,
    ): AudioSourceFactory = AudioSourceFactory {
        val fixture = File(context.cacheDir, "fixture_input.wav").apply {
            if (!exists()) {
                WavWriter(this, channels = 1).apply {
                    open()
                    // ~0.3s of non-silent PCM so the writer accumulates data.
                    repeat(3) { write(ShortArray(AudioSource.SAMPLE_RATE / 10) { 500 }) }
                    close()
                }
            }
        }
        FileAudioSource(fixture, tier = CaptureTier.MIC_ONLY, channels = 1)
    }

    @Provides
    @Singleton
    fun provideWavFileProvider(
        @ApplicationContext context: Context,
    ): WavFileProvider = WavFileProvider {
        File(context.cacheDir, "test_call_${System.currentTimeMillis()}.wav")
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CaptureBindsModule::class])
object TestCaptureBindsModule {

    @Provides
    @Singleton
    fun providePipeline(pipeline: CountingPipeline): CallPipeline = pipeline

    @Provides
    @Singleton
    fun provideContactResolver(): ContactResolver = object : ContactResolver {
        override suspend fun resolve(phoneNumber: String): String? = null
    }
}

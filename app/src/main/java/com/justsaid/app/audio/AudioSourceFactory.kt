package com.justsaid.app.audio

import kotlinx.coroutines.CoroutineDispatcher

/** Chooses the microphone capture source for a session. */
fun interface AudioSourceFactory {
    fun create(): AudioSource
}

/**
 * Production factory: companion capture uses [MediaRecorder.AudioSource.MIC] only.
 */
class RealAudioSourceFactory(
    private val dispatcher: CoroutineDispatcher,
) : AudioSourceFactory {

    override fun create(): AudioSource = MicrophoneAudioSource(dispatcher)
}

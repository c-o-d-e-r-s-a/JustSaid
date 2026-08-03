package com.justsaid.app.session

import android.content.Context
import com.justsaid.app.service.MicrophoneCaptureService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Starts and stops the microphone foreground service around capture. */
interface CaptureServiceGateway {
    /** @return false if foreground activation was refused or timed out. */
    suspend fun start(): Boolean
    fun shutdown()
}

@Singleton
class MicrophoneCaptureServiceGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activationCoordinator: ForegroundActivationCoordinator,
) : CaptureServiceGateway {

    override suspend fun start(): Boolean {
        val (attemptId, deferred) = activationCoordinator.beginAttempt()
        return try {
            if (!MicrophoneCaptureService.startService(context, attemptId)) {
                activationCoordinator.cancel(attemptId)
                return false
            }
            val activated = try {
                withTimeout(FOREGROUND_ACTIVATION_TIMEOUT_MS) { deferred.await() }
            } catch (_: TimeoutCancellationException) {
                activationCoordinator.cancel(attemptId)
                false
            }
            if (!activated) {
                shutdown()
            }
            activated
        } finally {
            activationCoordinator.endAttempt(attemptId)
        }
    }

    override fun shutdown() = MicrophoneCaptureService.shutdown(context)

    private companion object {
        const val FOREGROUND_ACTIVATION_TIMEOUT_MS = 10_000L
    }
}

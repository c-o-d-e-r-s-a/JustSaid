package com.justsaid.app.session

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

/**
 * Per-attempt foreground activation handoff between [com.justsaid.app.service.MicrophoneCaptureService]
 * and [CaptureServiceGateway]. Each capture start registers a token; late or stale completions
 * after cancel are ignored so a timed-out attempt cannot resume capture.
 */
@Singleton
class ForegroundActivationCoordinator @Inject constructor() {

    private val lock = Any()
    private val pending = mutableMapOf<String, Attempt>()

    /** Registers a new activation attempt and returns its id plus the awaitable result. */
    fun beginAttempt(): Pair<String, CompletableDeferred<Boolean>> {
        val attemptId = UUID.randomUUID().toString()
        val attempt = Attempt(CompletableDeferred())
        synchronized(lock) {
            pending[attemptId] = attempt
        }
        return attemptId to attempt.deferred
    }

    /**
     * Completes [attemptId] from the service thread.
     *
     * @return false when the attempt was cancelled or is unknown (caller must stop the service).
     */
    fun complete(attemptId: String, success: Boolean): Boolean {
        synchronized(lock) {
            val attempt = pending.remove(attemptId) ?: return false
            if (attempt.cancelled) return false
            if (!attempt.deferred.isCompleted) {
                attempt.deferred.complete(success)
            }
            return true
        }
    }

    /** Marks [attemptId] as cancelled and completes the awaiter with false (timeout / start failure). */
    fun cancel(attemptId: String) {
        synchronized(lock) {
            val attempt = pending[attemptId] ?: return
            attempt.cancelled = true
            pending.remove(attemptId)
            if (!attempt.deferred.isCompleted) {
                attempt.deferred.complete(false)
            }
        }
    }

    /** Drops bookkeeping for [attemptId] after the gateway finishes awaiting. */
    fun endAttempt(attemptId: String) {
        synchronized(lock) { pending.remove(attemptId) }
    }

    private class Attempt(val deferred: CompletableDeferred<Boolean>) {
        @Volatile var cancelled: Boolean = false
    }
}

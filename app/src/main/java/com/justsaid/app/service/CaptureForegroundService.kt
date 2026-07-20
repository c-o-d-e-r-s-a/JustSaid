package com.justsaid.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.justsaid.app.BuildConfig
import com.justsaid.app.MainActivity
import com.justsaid.app.R
import com.justsaid.app.audio.CaptureController
import com.justsaid.app.telecom.CallActions
import com.justsaid.app.telecom.CallState
import com.justsaid.app.telecom.CallStateHolder
import com.justsaid.app.telecom.phoneNumber
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Compliant foreground service that (a) keeps the process foregrounded while a call is live so
 * the CallStyle notification can be shown, and (b) upgrades to the `microphone` FGS type only
 * while audio is actually being captured. It owns NO capture logic —
 * [com.justsaid.app.audio.CaptureController] does — it merely mirrors call state into the
 * notification.
 *
 * Lifecycle-critical ordering [why]: `startForegroundService()` requires `startForeground()` to
 * be called promptly, and a `CallStyle` notification may only be posted *after* the service is
 * foregrounded (or with a granted full-screen intent). We therefore call [startForegroundSafely]
 * synchronously in [onCreate] before observing state, and route every notification update through
 * `startForeground` (never a bare `NotificationManager.notify`). Posting the CallStyle
 * notification before the service was foregrounded is what previously crashed the whole process —
 * taking the InCallService down with it and handing calls back to the OEM dialer.
 */
@AndroidEntryPoint
class CaptureForegroundService : LifecycleService() {

    @Inject lateinit var callStateHolder: CallStateHolder
    @Inject lateinit var callActions: CallActions
    @Inject lateinit var ttsAnnouncer: TtsAnnouncer
    @Inject lateinit var captureController: CaptureController

    private var announcedThisCall = false

    override fun onCreate() {
        super.onCreate()
        createChannel()

        // Establish the foreground service up front. If the platform refuses (e.g. FGS-start
        // restrictions), degrade to no capture rather than crash the process / InCallService.
        if (!startForegroundSafely(callStateHolder.state.value, captureController.isCapturing.value)) {
            return
        }

        lifecycleScope.launch {
            combine(callStateHolder.state, captureController.isCapturing, ::Pair).collect { (state, capturing) ->
                when (state) {
                    is CallState.Idle -> stopSelf()
                    is CallState.Active -> {
                        startForegroundSafely(state, capturing)
                        if (!announcedThisCall) {
                            announcedThisCall = true
                            ttsAnnouncer.maybeAnnounce()
                        }
                    }
                    else -> startForegroundSafely(state, capturing)
                }
            }
        }
    }

    override fun onDestroy() {
        ttsAnnouncer.shutdown()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_HANGUP -> callActions.hangup()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            // Initial start: onCreate already called startForeground(); nothing more to do.
        }
        return START_STICKY
    }

    /**
     * Posts/updates the ongoing-call notification as a foreground service. The `microphone` type
     * is added ONLY when audio is actively being captured AND RECORD_AUDIO is granted — receiving
     * a call never needs the mic, and requesting the mic type without the permission would throw.
     *
     * @return true if the service is (still) foregrounded; false if the start was refused (in
     *   which case the service has stopped itself).
     */
    private fun startForegroundSafely(state: CallState, capturing: Boolean): Boolean {
        val type = foregroundServiceType(micRequested = capturing, hasRecordAudio = hasRecordAudio())
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(state), type)
            true
        } catch (e: Exception) {
            // Covers SecurityException and (API 31+) ForegroundServiceStartNotAllowedException, plus
            // any notification rejection. A failure here must degrade gracefully, never crash the
            // hosting process (which also runs the InCallService).
            if (BuildConfig.DEBUG) Log.w(TAG, "startForeground refused (${e.javaClass.simpleName}); stopping service", e)
            stopSelf()
            false
        }
    }

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildNotification(state: CallState): android.app.Notification {
        val caller = state.phoneNumber.ifBlank { getString(R.string.incall_unknown_caller) }
        val person = Person.Builder().setName(caller).setImportant(true).build()

        val fullScreen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hangup = PendingIntent.getService(
            this,
            1,
            Intent(this, CaptureForegroundService::class.java).setAction(ACTION_HANGUP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.incall_notification_title))
            .setContentText(caller)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangup))
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.incall_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.incall_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "CaptureFgs"
        private const val CHANNEL_ID = "justsaid_calls"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_HANGUP = "com.justsaid.app.action.HANGUP"
        private const val ACTION_STOP = "com.justsaid.app.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, CaptureForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CaptureForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}

/**
 * Pure policy for the foreground-service type bitmask: always `phoneCall`; add `microphone` only
 * when capture is active AND RECORD_AUDIO is granted. Kept as a free function (no Android state)
 * so the gating logic is unit-testable on the JVM.
 */
internal fun foregroundServiceType(
    micRequested: Boolean,
    hasRecordAudio: Boolean,
    phoneCallType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
    microphoneType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
): Int = if (micRequested && hasRecordAudio) phoneCallType or microphoneType else phoneCallType

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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.justsaid.app.BuildConfig
import com.justsaid.app.MainActivity
import com.justsaid.app.R
import com.justsaid.app.session.CaptureSessionController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Microphone foreground service while [AudioRecord] is active. Started only when capture
 * begins from a visible activity; stops when capture ends. Owns no capture logic.
 */
@AndroidEntryPoint
class MicrophoneCaptureService : LifecycleService() {

  @Inject lateinit var captureSessionController: CaptureSessionController

  override fun onCreate() {
    super.onCreate()
    createChannel()
    if (!enterForeground()) {
      stopSelf()
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    when (intent?.action) {
      ACTION_USER_STOP -> captureSessionController.stop()
      ACTION_SHUTDOWN -> stopSelf()
    }
    return START_NOT_STICKY
  }

  private fun enterForeground(): Boolean {
    if (!hasRecordAudio()) return false
    val type = microphoneForegroundServiceType(hasRecordAudio = true)
    return try {
      ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
      true
    } catch (e: Exception) {
      if (BuildConfig.DEBUG) {
        Log.w(TAG, "startForeground refused (${e.javaClass.simpleName}); stopping service", e)
      }
      false
    }
  }

  private fun hasRecordAudio(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  private fun buildNotification(): android.app.Notification {
    val openApp = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val stopCapture = PendingIntent.getService(
      this,
      1,
      Intent(this, MicrophoneCaptureService::class.java).setAction(ACTION_USER_STOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(getString(R.string.capture_notification_title))
      .setContentText(getString(R.string.capture_notification_text))
      .setContentIntent(openApp)
      .setOngoing(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .addAction(
        R.drawable.ic_launcher_foreground,
        getString(R.string.capture_notification_stop),
        stopCapture,
      )
      .build()
  }

  private fun createChannel() {
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.capture_channel_name),
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = getString(R.string.capture_channel_desc)
      setShowBadge(false)
    }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  companion object {
    private const val TAG = "MicCaptureFgs"
    private const val CHANNEL_ID = "justsaid_capture"
    private const val NOTIFICATION_ID = 43
    private const val ACTION_USER_STOP = "com.justsaid.app.action.CAPTURE_STOP"
    private const val ACTION_SHUTDOWN = "com.justsaid.app.action.CAPTURE_SHUTDOWN"

    /**
     * @return false if the platform refused foreground start (caller must abort capture).
     */
    fun start(context: Context): Boolean {
      val intent = Intent(context, MicrophoneCaptureService::class.java)
      return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
        true
      } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG, "startForegroundService refused", e)
        false
      }
    }

    fun shutdown(context: Context) {
      context.startService(
        Intent(context, MicrophoneCaptureService::class.java).setAction(ACTION_SHUTDOWN),
      )
    }
  }
}

/** Microphone FGS type only — no phone-call type in the companion product. */
internal fun microphoneForegroundServiceType(
  hasRecordAudio: Boolean,
  microphoneType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
): Int = if (hasRecordAudio) microphoneType else 0

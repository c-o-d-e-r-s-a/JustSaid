package com.justsaid.app.session

import android.content.Context
import com.justsaid.app.service.MicrophoneCaptureService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Starts and stops the microphone foreground service around capture. */
interface CaptureServiceGateway {
  fun start(): Boolean
  fun shutdown()
}

@Singleton
class MicrophoneCaptureServiceGateway @Inject constructor(
  @ApplicationContext private val context: Context,
) : CaptureServiceGateway {
  override fun start(): Boolean = MicrophoneCaptureService.start(context)
  override fun shutdown() = MicrophoneCaptureService.shutdown(context)
}

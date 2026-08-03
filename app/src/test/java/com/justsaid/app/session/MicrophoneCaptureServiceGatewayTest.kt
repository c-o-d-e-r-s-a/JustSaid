package com.justsaid.app.session

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.justsaid.app.service.MicrophoneCaptureService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MicrophoneCaptureServiceGatewayTest {

  private val context = mockk<Context>(relaxed = true)
  private val coordinator = ForegroundActivationCoordinator()

  @Before
  fun setUp() {
    mockkObject(MicrophoneCaptureService)
    every { MicrophoneCaptureService.startService(any(), any()) } returns true
    every { MicrophoneCaptureService.shutdown(any()) } returns Unit
  }

  @After
  fun tearDown() {
    unmockkObject(MicrophoneCaptureService)
  }

  @Test
  fun `foreground activation timeout shuts down service`() = runTest {
    val gateway = MicrophoneCaptureServiceGateway(context, coordinator)
    val started = async { gateway.start() }
    testScheduler.advanceTimeBy(10_001)
    assertThat(started.await()).isFalse()
    verify { MicrophoneCaptureService.shutdown(context) }
  }
}

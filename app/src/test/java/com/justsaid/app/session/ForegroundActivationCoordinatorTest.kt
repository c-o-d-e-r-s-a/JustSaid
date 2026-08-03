package com.justsaid.app.session

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundActivationCoordinatorTest {

  private val coordinator = ForegroundActivationCoordinator()

  @Test
  fun `successful attempt completes awaiter`() = runTest {
    val (attemptId, deferred) = coordinator.beginAttempt()

    assertThat(coordinator.complete(attemptId, success = true)).isTrue()
    assertThat(deferred.await()).isTrue()
  }

  @Test
  fun `cancel completes awaiter false and rejects late activation`() = runTest {
    val (attemptId, deferred) = coordinator.beginAttempt()

    coordinator.cancel(attemptId)
    assertThat(deferred.await()).isFalse()
    assertThat(coordinator.complete(attemptId, success = true)).isFalse()
  }

  @Test
  fun `timeout then late service start is rejected`() = runTest {
    val (attemptId, deferred) = coordinator.beginAttempt()

    coordinator.cancel(attemptId)
    assertThat(deferred.await()).isFalse()

    val lateAccepted = coordinator.complete(attemptId, success = true)
    assertThat(lateAccepted).isFalse()
  }

  @Test
  fun `unknown attempt id is rejected`() {
    assertThat(coordinator.complete("missing", success = true)).isFalse()
  }
}

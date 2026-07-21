package com.justsaid.app.export

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Phase 5 doc: `smsto:` URI + `sms_body` correctly formed (never auto-sent). */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class SmsIntentBuilderTest {

    @Test
    fun `builds ACTION_SENDTO with smsto uri and body`() {
        val intent = SmsIntentBuilder.build("+15555550123", "Ada — call summary\n• Buy milk")

        assertThat(intent.action).isEqualTo(Intent.ACTION_SENDTO)
        assertThat(intent.data.toString()).isEqualTo("smsto:+15555550123")
        assertThat(intent.getStringExtra("sms_body")).isEqualTo("Ada — call summary\n• Buy milk")
    }

    @Test
    fun `number is carried verbatim including formatting characters`() {
        val intent = SmsIntentBuilder.build("555-0123", "hi")

        assertThat(intent.data.toString()).isEqualTo("smsto:555-0123")
    }
}

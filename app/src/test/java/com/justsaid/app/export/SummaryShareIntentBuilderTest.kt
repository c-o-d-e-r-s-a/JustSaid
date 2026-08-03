package com.justsaid.app.export

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Generic text share: ACTION_SEND with plain body (no phone number). */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class SummaryShareIntentBuilderTest {

    @Test
    fun `builds ACTION_SEND with plain text body`() {
        val intent = SummaryShareIntentBuilder.build("Ada — summary\n• Buy milk")

        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(intent.type).isEqualTo("text/plain")
        assertThat(intent.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("Ada — summary\n• Buy milk")
    }
}

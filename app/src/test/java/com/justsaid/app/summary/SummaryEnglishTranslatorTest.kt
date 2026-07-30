package com.justsaid.app.summary

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.PromiseItem
import com.justsaid.app.core.Speaker
import org.junit.Test

class SummaryEnglishTranslatorTest {

    @Test
    fun `parse extracts transcript and task titles`() {
        val summary = CallSummary(
            id = 0L,
            contactName = null,
            phoneNumber = "+1",
            createdAt = 0L,
            items = listOf(
                PromiseItem("dood leke aana", "5 litre", "5 litre dood leke aana", Speaker.UNKNOWN, false),
            ),
            fullTranscript = "SPEAKER_?: 5 litre dood leke aana",
        )
        val raw = """
            TRANSCRIPT:
            SPEAKER_?: Bring 5 liters of milk.
            TASKS:
            1. Bring 5 liters of milk
        """.trimIndent()

        val view = SummaryEnglishTranslator.parse(raw, summary)

        assertThat(view).isNotNull()
        assertThat(view!!.transcript).contains("Bring 5 liters")
        assertThat(view.taskTitles).containsExactly("Bring 5 liters of milk")
    }
}

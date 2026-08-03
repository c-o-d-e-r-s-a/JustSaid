package com.justsaid.app.summary

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment
import org.junit.Test

class TranscriptChunkerTest {

    private fun segment(text: String, index: Int = 0) = TranscriptSegment(
        speaker = Speaker.UNKNOWN,
        text = text,
        startMs = index * 1_000L,
        endMs = index * 1_000L + 900L,
    )

    @Test
    fun `short transcript stays one chunk`() {
        val transcript = Transcript(listOf(segment("I'll buy milk")))

        val chunks = TranscriptChunker.chunks(transcript, maxPlainTextChars = 500)

        assertThat(chunks).hasSize(1)
        assertThat(chunks.first().plainText()).contains("I'll buy milk")
    }

    @Test
    fun `long transcript splits on segment boundaries`() {
        val segments = (1..20).map { i ->
            segment("commitment number $i with enough words to consume space", i)
        }
        val transcript = Transcript(segments)

        val chunks = TranscriptChunker.chunks(transcript, maxPlainTextChars = 120)

        assertThat(chunks.size).isGreaterThan(1)
        chunks.forEach { chunk ->
            assertThat(chunk.plainText().length).isAtMost(120)
        }
        assertThat(chunks.flatMap { it.segments }.map { it.text })
            .containsExactlyElementsIn(segments.map { it.text })
    }

    @Test
    fun `oversized single segment is split on words`() {
        val longText = buildString {
            repeat(40) { append("word$it ") }
        }.trim()
        val transcript = Transcript(listOf(segment(longText)))

        val chunks = TranscriptChunker.chunks(transcript, maxPlainTextChars = 80)

        assertThat(chunks.size).isGreaterThan(1)
        chunks.forEach { chunk ->
            assertThat(chunk.plainText().length).isAtMost(80)
        }
    }
}

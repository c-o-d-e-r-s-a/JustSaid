package com.justsaid.app.stt

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.data.repo.SttLanguageLock
import org.junit.Test

class SttTranscribeHintTest {

    @Test
    fun `english lock ignores spoken set`() {
        val (lang, allowed) = SttTranscribeHint.resolve(SttLanguageLock.EN, setOf("hi", "en"))
        assertThat(lang).isEqualTo("en")
        assertThat(allowed).isEmpty()
    }

    @Test
    fun `auto with no picks stays full auto`() {
        val (lang, allowed) = SttTranscribeHint.resolve(SttLanguageLock.AUTO, emptySet())
        assertThat(lang).isEqualTo("auto")
        assertThat(allowed).isEmpty()
    }

    @Test
    fun `single spoken language pins whisper without allow list`() {
        val (lang, allowed) = SttTranscribeHint.resolve(SttLanguageLock.AUTO, setOf("hi"))
        assertThat(lang).isEqualTo("hi")
        assertThat(allowed).isEmpty()
    }

    @Test
    fun `multiple spoken languages use constrained auto`() {
        val (lang, allowed) = SttTranscribeHint.resolve(SttLanguageLock.AUTO, setOf("hi", "en"))
        assertThat(lang).isEqualTo("auto")
        assertThat(allowed).isEqualTo("en,hi")
    }
}

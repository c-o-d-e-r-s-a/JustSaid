package com.justsaid.app.session

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionWavFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `deleteVerified succeeds when file is removed`() {
        val wav = tmp.newFile("justsaid_session_test.wav")
        assertThat(SessionWavFiles.deleteVerified(wav)).isTrue()
        assertThat(wav.exists()).isFalse()
    }

    @Test
    fun `deleteVerified succeeds when file is already absent`() {
        val wav = File(tmp.root, "missing.wav")
        assertThat(SessionWavFiles.deleteVerified(wav)).isTrue()
    }
}

package com.justsaid.app.stt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WhisperLanguageCatalogTest {

    @Test
    fun `lists every whisper language code`() {
        assertThat(WhisperLanguageCatalog.entries).hasSize(100)
        assertThat(WhisperLanguageCatalog.supportedCodes).contains("hi")
        assertThat(WhisperLanguageCatalog.supportedCodes).contains("yue")
    }
}

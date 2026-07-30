package com.justsaid.app.stt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WhisperJsonTest {

    @Test
    fun `parseChunk reads lang and segments`() {
        val json = """{"lang":"hi","segments":[{"t0":0,"t1":100,"text":"नमस्ते"}]}"""
        val chunk = WhisperJson.parseChunk(json)

        assertThat(chunk.detectedLanguage).isEqualTo("hi")
        assertThat(chunk.segments).hasSize(1)
        assertThat(chunk.segments.first().text).isEqualTo("नमस्ते")
    }

    @Test
    fun `auto lang is treated as unknown for pinning`() {
        val json = """{"lang":"auto","segments":[]}"""
        assertThat(WhisperJson.parseDetectedLanguage(json)).isNull()
    }
}

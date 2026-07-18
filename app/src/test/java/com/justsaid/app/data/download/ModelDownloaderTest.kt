package com.justsaid.app.data.download

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ModelDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var filesDir: File
    private lateinit var downloader: ModelDownloader

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        filesDir = createTempDir(prefix = "justsaid_test")
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        downloader = ModelDownloader(context, OkHttpClient(), Dispatchers.IO)
    }

    @After
    fun tearDown() {
        server.shutdown()
        filesDir.deleteRecursively()
    }

    @Test
    fun `downloads file, verifies checksum, emits progress and writes to private dir`() = runTest {
        val payload = randomBytes(4096)
        val spec = specFor(payload, sha256 = sha256Hex(payload))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val emissions = downloader.download(listOf(spec)).toList()

        val last = emissions.last()
        assertThat(last.overallState).isEqualTo(OverallState.SUCCESS)
        // Progress was observed climbing before completion.
        assertThat(emissions.any { it.overallState == OverallState.RUNNING }).isTrue()
        // Final file exists in the private models dir; the .part scratch file is gone.
        val finalFile = File(File(filesDir, ModelCatalog.MODELS_DIR), spec.fileName)
        assertThat(finalFile.exists()).isTrue()
        assertThat(finalFile.readBytes()).isEqualTo(payload)
        assertThat(File(File(filesDir, ModelCatalog.MODELS_DIR), spec.fileName + ".part").exists()).isFalse()
    }

    @Test
    fun `rejects file on checksum mismatch and deletes partial`() = runTest {
        val payload = randomBytes(4096)
        val spec = specFor(payload, sha256 = "0".repeat(64))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(payload)))

        val emissions = downloader.download(listOf(spec)).toList()

        val last = emissions.last()
        assertThat(last.overallState).isEqualTo(OverallState.FAILED)
        assertThat(last.error).contains("Checksum")
        val modelsDir = File(filesDir, ModelCatalog.MODELS_DIR)
        assertThat(File(modelsDir, spec.fileName).exists()).isFalse()
        assertThat(File(modelsDir, spec.fileName + ".part").exists()).isFalse()
    }

    @Test
    fun `resumes partial download using Range header`() = runTest {
        val payload = randomBytes(4096)
        val prefixLen = 1500
        val spec = specFor(payload, sha256 = sha256Hex(payload))

        // Simulate an interrupted prior attempt: a .part file holding the first bytes.
        val modelsDir = File(filesDir, ModelCatalog.MODELS_DIR).apply { mkdirs() }
        File(modelsDir, spec.fileName + ".part").writeBytes(payload.copyOfRange(0, prefixLen))

        // Server returns only the remaining bytes with 206 Partial Content.
        val remaining = payload.copyOfRange(prefixLen, payload.size)
        server.enqueue(MockResponse().setResponseCode(206).setBody(Buffer().write(remaining)))

        val emissions = downloader.download(listOf(spec)).toList()

        assertThat(emissions.last().overallState).isEqualTo(OverallState.SUCCESS)
        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Range")).isEqualTo("bytes=$prefixLen-")
        val finalFile = File(modelsDir, spec.fileName)
        assertThat(finalFile.readBytes()).isEqualTo(payload)
    }

    private fun specFor(payload: ByteArray, sha256: String) = ModelSpec(
        id = "test-model",
        fileName = "test-model.bin",
        url = server.url("/test-model.bin").toString(),
        sizeBytes = payload.size.toLong(),
        sha256 = sha256,
    )

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size) { (it % 251).toByte() }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

package com.justsaid.app.export

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.PromiseItem
import com.justsaid.app.core.Speaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 5 doc: text export carries the rendered summary; PDF export is a non-empty,
 * valid PDF file; both land in the FileProvider-scoped export cache dir. Uses a
 * plain [android.app.Application] — the real app's startup needs the AndroidKeyStore,
 * which does not exist under Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class SummaryExporterTest {

    private lateinit var context: Context
    private lateinit var exporter: SummaryExporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exporter = SummaryExporter(context, UnconfinedTestDispatcher())
        clearFileProviderCache()
    }

    /**
     * FileProvider statically caches its parsed roots per authority, but Robolectric
     * gives every test a fresh temp data dir — clear the cache so the roots are
     * re-resolved against this test's cacheDir.
     */
    private fun clearFileProviderCache() {
        val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }

    /** True when Robolectric's graphics runtime can actually create PDF pages here. */
    private fun pdfNativeAvailable(): Boolean = try {
        val doc = android.graphics.pdf.PdfDocument()
        val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(100, 100, 1).create())
        doc.finishPage(page)
        doc.close()
        true
    } catch (e: IllegalStateException) {
        false
    }

    private fun summary(name: String = "Ada") = CallSummary(
        id = 1L,
        contactName = name,
        phoneNumber = "+15555550123",
        createdAt = 1_752_986_000_000L,
        items = listOf(
            PromiseItem(
                task = "Buy milk",
                quantity = "2 liters",
                proofQuote = "I'll buy two liters of milk",
                attributedTo = Speaker.LOCAL,
                confirmed = true,
            ),
        ),
        fullTranscript = "You: I'll buy two liters of milk",
    )

    @Test
    fun textExport_containsRenderedSummary() = runTest {
        val result = exporter.exportText(listOf(summary()))

        val file = (result as JustSaidResult.Success).value
        val text = file.readText()
        assertThat(text).contains("Ada — ")
        assertThat(text).contains("• Buy milk [2 liters]")
        assertThat(text).contains("\"I'll buy two liters of milk\"")
        assertThat(file.parentFile?.name).isEqualTo("exports")
        assertThat(file.parentFile?.parentFile).isEqualTo(context.cacheDir)
    }

    @Test
    fun textExport_fullHistory_joinsAllSummaries() = runTest {
        val result = exporter.exportText(listOf(summary("Ada"), summary("Bob")))

        val text = (result as JustSaidResult.Success).value.readText()
        assertThat(text).contains("Ada — ")
        assertThat(text).contains("Bob — ")
    }

    @Test
    fun pdfExport_producesNonEmptyPdfFile() = runTest {
        assumeTrue(
            "Robolectric's native PdfDocument is unavailable on this host OS",
            pdfNativeAvailable(),
        )

        val result = exporter.exportPdf(listOf(summary()))

        if (result is JustSaidResult.Failure) {
            throw AssertionError("${result.reason}: ${result.cause}", result.cause)
        }
        val file = (result as JustSaidResult.Success).value
        assertThat(file.extension).isEqualTo("pdf")
        assertThat(file.length()).isGreaterThan(0L)
        // PDF magic header "%PDF"
        val header = file.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
        assertThat(String(header, Charsets.US_ASCII)).isEqualTo("%PDF")
    }

    @Test
    fun shareIntent_isActionSendWithReadGrant() = runTest {
        // FileProvider's path matching hardcodes '/' separators, so it cannot
        // resolve files on a Windows host JVM; runs for real on CI and devices.
        assumeTrue(
            "FileProvider path matching requires '/' file separators",
            java.io.File.separatorChar == '/',
        )
        val file = (exporter.exportText(listOf(summary())) as JustSaidResult.Success).value

        val chooser = exporter.shareIntent(file)
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        val inner = @Suppress("DEPRECATION") (chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertThat(inner!!.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(inner.type).isEqualTo("text/plain")
        assertThat(inner.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(inner.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)).isNotNull()
    }
}

package com.justsaid.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.justsaid.app.R
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.IoDispatcher
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.summary.SummaryMarkdown
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns saved summaries into shareable files (Text Lifecycle T4): plain `.txt`
 * or a large-font paginated `.pdf`, written to the private export cache dir the
 * manifest's `FileProvider` exposes. Sharing is always via [shareIntent]
 * (`ACTION_SEND` chooser) — nothing leaves the device without the user's tap.
 */
@Singleton
class SummaryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /** Writes [summaries] (one, or the full history) as plain text. */
    suspend fun exportText(summaries: List<CallSummary>): JustSaidResult<File> =
        withContext(dispatcher) {
            try {
                val file = newExportFile("txt")
                file.writeText(renderAll(summaries))
                JustSaidResult.Success(file)
            } catch (e: Exception) {
                JustSaidResult.Failure("Could not create the text file", e)
            }
        }

    /** Writes [summaries] as a paginated, large-font PDF. */
    suspend fun exportPdf(summaries: List<CallSummary>): JustSaidResult<File> =
        withContext(dispatcher) {
            try {
                val file = newExportFile("pdf")
                writePdf(renderAll(summaries), file)
                JustSaidResult.Success(file)
            } catch (e: Exception) {
                JustSaidResult.Failure("Could not create the PDF file", e)
            }
        }

    /** `ACTION_SEND` chooser for an exported file, granting the receiver read access. */
    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(if (file.extension == "pdf") "application/pdf" else "text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, context.getString(R.string.export_share_title))
    }

    private fun renderAll(summaries: List<CallSummary>): String {
        val unconfirmed = context.getString(R.string.summary_unconfirmed)
        val bodies = summaries.map { SummaryMarkdown.render(it, unconfirmed) }
        return if (summaries.size == 1) {
            bodies.single()
        } else {
            context.getString(R.string.export_history_heading) +
                "\n\n" + bodies.joinToString("\n\n----------\n\n")
        }
    }

    private fun newExportFile(extension: String): File {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        return File(dir, "justsaid-summary-${System.currentTimeMillis()}.$extension")
    }

    /** Simple word-wrapping layout: large text, new page when the current one fills. */
    private fun writePdf(text: String, target: File) {
        val paint = Paint().apply {
            typeface = Typeface.SANS_SERIF
            textSize = FONT_SIZE
            isAntiAlias = true
        }
        val document = PdfDocument()
        try {
            var pageNumber = 1
            var page = document.startPage(pageInfo(pageNumber))
            var y = MARGIN + FONT_SIZE

            for (line in text.lines().flatMap { wrap(it, paint) }) {
                if (y > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(pageInfo(pageNumber))
                    y = MARGIN + FONT_SIZE
                }
                page.canvas.drawText(line, MARGIN, y, paint)
                y += LINE_HEIGHT
            }
            document.finishPage(page)
            target.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private fun pageInfo(number: Int) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()

    private fun wrap(line: String, paint: Paint): List<String> {
        val maxWidth = PAGE_WIDTH - 2 * MARGIN
        if (line.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (word in line.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) out.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        // A4 at 72dpi with a font size well above print defaults (U2: large text).
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val FONT_SIZE = 16f
        const val LINE_HEIGHT = 24f
    }
}

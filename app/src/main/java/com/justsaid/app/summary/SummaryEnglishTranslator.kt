package com.justsaid.app.summary

import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.PromiseItem

/**
 * On-device English rendering for the summary screen (optional toggle). Proof quotes
 * stay verbatim from the original transcript (Constitution G1); only task titles and
 * the readable transcript body are translated.
 */
object SummaryEnglishTranslator {

    data class View(
        val transcript: String,
        /** English task title per [CallSummary.items] index; same size as items. */
        val taskTitles: List<String>,
    )

    fun buildLlmPrompt(summary: CallSummary): String {
        val body = buildPrompt(summary)
        return buildString {
            append("<|start_header_id|>system<|end_header_id|>\n\n")
            append("You translate call summaries to English. Follow the user instructions exactly.\n")
            append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n")
            append(body)
            append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    }

    fun buildPrompt(summary: CallSummary): String {
        val itemsBlock = summary.items.mapIndexed { i, item ->
            "${i + 1}. ${item.task}${item.quantity?.let { " [$it]" } ?: ""} | proof: \"${item.proofQuote}\""
        }.joinToString("\n")
        return """
            Translate this phone-call summary into clear English for a family reader.

            Rules:
            - Translate the transcript and each task title (the part before |).
            - Copy every proof quote EXACTLY as written — do not translate or fix proofs.
            - Keep speaker labels (You:, Them:, SPEAKER_?:) on transcript lines.
            - No preamble.

            Original transcript:
            ${summary.fullTranscript}

            Original tasks:
            $itemsBlock

            Respond in exactly this format:
            TRANSCRIPT:
            <english transcript>
            TASKS:
            1. <english task title only>
            2. ...
        """.trimIndent()
    }

    fun parse(raw: String, summary: CallSummary): View? {
        val transcriptMarker = "TRANSCRIPT:"
        val tasksMarker = "TASKS:"
        val tIdx = raw.indexOf(transcriptMarker, ignoreCase = true)
        val kIdx = raw.indexOf(tasksMarker, ignoreCase = true)
        if (tIdx < 0 || kIdx < 0 || kIdx <= tIdx) return null

        val transcript = raw.substring(tIdx + transcriptMarker.length, kIdx).trim()
        if (transcript.isEmpty()) return null

        val taskLines = raw.substring(kIdx + tasksMarker.length)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val titles = mutableListOf<String>()
        for (line in taskLines) {
            val stripped = line.replace(Regex("""^\d+\.\s*"""), "").trim()
            if (stripped.isNotEmpty()) titles += stripped
        }
        if (summary.items.isNotEmpty() && titles.size != summary.items.size) return null

        return View(transcript = transcript, taskTitles = titles)
    }
}

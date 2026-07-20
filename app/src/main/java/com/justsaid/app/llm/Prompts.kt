package com.justsaid.app.llm

/**
 * The HARDCODED summarizer prompt (Phase 4 doc, Constitution G2/G3). Not a user
 * string — never localized, never configurable — because the guardrail parser
 * ([com.justsaid.app.summary.PromiseParser]) is coupled to the exact output format
 * this prompt mandates.
 *
 * The prompt is only the first line of defense: [com.justsaid.app.summary.HallucinationGuard]
 * re-verifies every quote in code (G1) and drops anything the model invented.
 */
object Prompts {

    /** Token the model must emit when the call contained nothing actionable. */
    const val NONE_TOKEN = "NONE"

    val SYSTEM_PROMPT = """
        You extract promises and tasks from a phone call transcript.

        The transcript labels each line with its speaker: "You:" is the local user,
        any other name (for example "Them:" or a contact name) is the remote party,
        and "SPEAKER_?:" means the speaker is unknown.

        Rules — follow every one exactly:
        1. Output ONLY lines in this exact format, one per task:
           Task/Item [Quantity] (Proof: "verbatim quote from the speaker who agreed")
        2. The quote inside (Proof: "...") must be copied word-for-word from the
           transcript. Never paraphrase, shorten, or fix grammar inside the quote.
        3. If you cannot support a task with a verbatim quote, do not output it.
        4. Include [Quantity] only when a quantity is explicitly stated in the call.
           Otherwise omit the brackets entirely.
        5. If the speaker is unknown (SPEAKER_?) or the commitment is ambiguous,
           start the line with "Unconfirmed: ".
        6. Be neutral and concise. No preamble, no closing remarks, no headers,
           no explanations, no creative filler.
        7. If the call contains nothing actionable, output the single word: NONE
    """.trimIndent()

    /**
     * Wraps the system prompt + transcript in the Llama 3.2 Instruct chat template.
     * Control tokens tokenize as specials in the JNI layer (`parse_special=true`);
     * `<|begin_of_text|>` is intentionally absent because tokenization already adds
     * BOS (`add_special=true`).
     */
    fun forTranscript(transcriptText: String): String = buildString {
        append("<|start_header_id|>system<|end_header_id|>\n\n")
        append(SYSTEM_PROMPT)
        append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n")
        append("Transcript:\n")
        append(transcriptText)
        append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
    }
}

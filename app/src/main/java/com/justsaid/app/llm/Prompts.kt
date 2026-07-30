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
           Prefer the shortest contiguous phrase that still contains the commitment.
        3. If you cannot support a task with a verbatim quote, do not output it.
        4. Include [Quantity] only when a quantity is explicitly stated in the call.
           Otherwise omit the brackets entirely.
        5. If the speaker is unknown (SPEAKER_?) or the commitment is ambiguous,
           start the line with "Unconfirmed: ".
        6. Be neutral and concise. No preamble, no closing remarks, no headers,
           no explanations, no creative filler.
        7. Write each task description in the same language and spelling as the transcript.
           Do not add English words (for example "Buy", "Fix", "Call") unless those exact
           English words appear in the transcript. If the transcript uses romanized Hindi,
           Urdu, or Gujarati, the task line must stay in that same form — never switch to
           standard English headings.
        8. Never translate the transcript or the proof quotes. Keep the structural
           keywords exactly as shown (Task line format, Proof:, Unconfirmed:, NONE).
        9. If the call contains nothing actionable, output the single word: NONE

        Example — romanized Hindi transcript:
        SPEAKER_?: 5 litre dood leke aana aur 2 chappal leke aana.

        Example — correct output:
        Unconfirmed: 5 litre dood leke aana [5 litre] (Proof: "5 litre dood leke aana")
        Unconfirmed: 2 chappal leke aana (Proof: "2 chappal leke aana")

        Example — English transcript:
        SPEAKER_?: I will buy 2 liters of milk and I will fix the sink this weekend.

        Example — correct output:
        Unconfirmed: Buy milk [2 liters] (Proof: "I will buy 2 liters of milk")
        Unconfirmed: Fix the sink (Proof: "I will fix the sink this weekend")
    """.trimIndent()

    /**
     * Wraps the system prompt + transcript in the Llama 3.2 Instruct chat template.
     * Control tokens tokenize as specials in the JNI layer (`parse_special=true`);
     * `<|begin_of_text|>` is intentionally absent because tokenization already adds
     * BOS (`add_special=true`).
     */
    fun forTranscript(transcriptText: String, detectedLanguage: String? = null): String = buildString {
        append("<|start_header_id|>system<|end_header_id|>\n\n")
        append(SYSTEM_PROMPT)
        detectedLanguage?.let { code ->
            append("\n\nThe transcript language is ISO 639-1 \"")
            append(code)
            append(
                "\". Task descriptions must match that language and the transcript's " +
                    "spelling/script (including romanized text). Do not default to English.",
            )
        }
        append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n")
        append("Transcript:\n")
        append(transcriptText)
        append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
    }
}

package com.justsaid.app.summary

/**
 * A raw line parsed from the LLM output, before the [HallucinationGuard] has
 * verified the quote against the transcript. Never leaves the summary package.
 */
data class ParsedPromise(
    val task: String,
    val quantity: String?,
    val proofQuote: String,
    val markedUnconfirmed: Boolean,
)

/**
 * Parses the model's line format `Task/Item [Quantity] (Proof: "verbatim quote")`.
 * Anything that does not match the format is silently dropped — the model is not
 * trusted to follow instructions (Constitution G1/G2), so free-form prose, headers,
 * or partial lines must never turn into promise items.
 */
object PromiseParser {

    /** The single token the prompt mandates when the call had nothing actionable. */
    private const val NONE_TOKEN = "NONE"

    // Optional list bullet, optional "Unconfirmed" label, task text, optional
    // [quantity], then the mandatory (Proof: "...") tail.
    private val LINE = Regex(
        """^(?:[-*•]\s*)?(?:(Unconfirmed)\s*[:\-—]\s*)?(.+?)\s*(?:\[([^\]\[]+)])?\s*\(\s*Proof\s*:\s*"(.+?)"\s*\)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): List<ParsedPromise> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.equals(NONE_TOKEN, ignoreCase = true)) return emptyList()

        return trimmed.lines().mapNotNull { line ->
            val match = LINE.find(line.trim()) ?: return@mapNotNull null
            val (unconfirmed, task, quantity, quote) = match.destructured
            if (task.isBlank() || quote.isBlank()) return@mapNotNull null
            ParsedPromise(
                task = task.trim(),
                quantity = quantity.trim().ifEmpty { null },
                proofQuote = quote.trim(),
                markedUnconfirmed = unconfirmed.isNotEmpty(),
            )
        }
    }
}

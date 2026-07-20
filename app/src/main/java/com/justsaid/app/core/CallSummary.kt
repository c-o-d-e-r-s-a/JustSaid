package com.justsaid.app.core

/**
 * One extracted promise/task. [proofQuote] is guaranteed by [com.justsaid.app.summary.HallucinationGuard]
 * to be a verbatim substring of the transcript (Constitution G1) — items that fail
 * that check never become a [PromiseItem].
 */
data class PromiseItem(
    val task: String,
    val quantity: String?,       // null unless explicitly stated (G3)
    val proofQuote: String,
    val attributedTo: Speaker,   // UNKNOWN => rendered "Unconfirmed" (S2)
    val confirmed: Boolean,
)

/**
 * The validated call summary produced by Phase 4 and consumed by Phase 5
 * (history/SMS/export). Text only — never audio (Constitution A1/T1).
 */
data class CallSummary(
    val id: Long,
    val contactName: String?,    // null => show the raw number
    val phoneNumber: String,
    val createdAt: Long,
    val items: List<PromiseItem>,
    val fullTranscript: String,
)

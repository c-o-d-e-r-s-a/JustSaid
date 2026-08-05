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

 * The validated session summary produced by Phase 4 and consumed by Phase 5

 * (history/export). Text only — never audio (Constitution A1/T1). Companion

 * sessions carry an optional user-entered [sessionLabel], not queried phone metadata.

 */

data class CallSummary(

    val id: Long,

    val sessionLabel: String?,

    val createdAt: Long,

    val items: List<PromiseItem>,

    val fullTranscript: String,

) {

    /** UI-compat until summary screens adopt [sessionLabel]. */

    val contactName: String? get() = sessionLabel



    /** Legacy dialer field; companion sessions never store a queried number. */

    val phoneNumber: String get() = ""



    /**

     * Legacy constructor for dialer-era summaries and tests. [phoneNumber] is ignored

     * for companion persistence — only [contactName] seeds [sessionLabel].

     */

    constructor(

        id: Long,

        contactName: String?,

        phoneNumber: String,

        createdAt: Long,

        items: List<PromiseItem>,

        fullTranscript: String,

    ) : this(

        id = id,

        sessionLabel = contactName?.takeIf { it.isNotBlank() },

        createdAt = createdAt,

        items = items,

        fullTranscript = fullTranscript,

    )

}



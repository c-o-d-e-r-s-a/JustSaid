package com.justsaid.app.export

import android.content.Intent

/**
 * Builds (never sends) a generic text-share intent so the user picks any app
 * (SMS, email, etc.) without assuming a phone number.
 */
object SummaryShareIntentBuilder {

    fun build(body: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, body)
}

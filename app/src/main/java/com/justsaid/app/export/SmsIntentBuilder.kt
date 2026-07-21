package com.justsaid.app.export

import android.content.Intent
import android.net.Uri

/**
 * Builds (never sends) the SMS intent: `ACTION_SENDTO` + `smsto:` opens the
 * user's own messaging app with the number and summary text prefilled, so the
 * final send is always their explicit tap (Phase 5 doc, "never auto-send").
 */
object SmsIntentBuilder {

    fun build(number: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
            .putExtra("sms_body", body)
}

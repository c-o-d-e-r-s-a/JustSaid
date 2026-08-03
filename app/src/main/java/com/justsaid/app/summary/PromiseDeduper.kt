package com.justsaid.app.summary

import com.justsaid.app.core.PromiseItem

/** Merges chunk-level candidates, dropping duplicates by normalized proof quote. */
object PromiseDeduper {

    fun merge(items: List<PromiseItem>): List<PromiseItem> =
        items.distinctBy { normalize(it.proofQuote) }

    private val WHITESPACE = Regex("\\s+")

    private fun normalize(text: String): String =
        text.lowercase().replace(WHITESPACE, " ").trim()
}

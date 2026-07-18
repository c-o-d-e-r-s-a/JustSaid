package com.justsaid.app.core

/**
 * The single result type crossing layer boundaries (repos/engines).
 * Callers pattern-match instead of catching exceptions; see AGENTS.md E1/E2.
 *
 * Defined once here (Constitution "Shared Types"); later phases must not redefine it.
 */
sealed interface JustSaidResult<out T> {
    data class Success<T>(val value: T) : JustSaidResult<T>
    data class Failure(val reason: String, val cause: Throwable? = null) : JustSaidResult<Nothing>
}

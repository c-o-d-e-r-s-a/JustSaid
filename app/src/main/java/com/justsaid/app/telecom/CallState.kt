package com.justsaid.app.telecom

/**
 * One-directional call lifecycle owned by [CallStateHolder]. Mirrors the subset of
 * `android.telecom.Call` states JustSaid cares about. UI and capture logic are pure
 * functions of this state (AGENTS.md §4).
 *
 * The remote party's number is carried on the live states; use [CallState.phoneNumber]
 * to read it uniformly (empty when [Idle]).
 */
sealed interface CallState {

    data object Idle : CallState

    data class Ringing(val number: String) : CallState

    data class Active(val number: String) : CallState

    data class Held(val number: String) : CallState

    data class Disconnected(val number: String) : CallState
}

/** The remote number for the current call, or empty string when [CallState.Idle]. */
val CallState.phoneNumber: String
    get() = when (this) {
        is CallState.Idle -> ""
        is CallState.Ringing -> number
        is CallState.Active -> number
        is CallState.Held -> number
        is CallState.Disconnected -> number
    }

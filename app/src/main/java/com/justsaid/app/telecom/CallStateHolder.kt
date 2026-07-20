package com.justsaid.app.telecom

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the current call's lifecycle. Written only by
 * [JustSaidInCallService] (system callbacks); read by the in-call UI and the
 * capture controller. Deliberately Android-free so it is trivially unit-testable.
 */
@Singleton
class CallStateHolder @Inject constructor() {

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    /** Called by the InCallService as telecom callbacks fire. */
    fun update(state: CallState) {
        _state.value = state
    }

    fun reset() {
        _state.value = CallState.Idle
    }
}

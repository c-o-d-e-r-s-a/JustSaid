package com.justsaid.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.ModelPaths
import com.justsaid.app.data.repo.SettingsRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Where the app should start after the first-run checks. */
enum class StartRoute { LEGAL, DOWNLOAD, HOME }

/**
 * Computes the app's start destination once, off the main thread:
 * legal not accepted -> LEGAL; else models missing -> DOWNLOAD; else HOME.
 * Emits `null` while the check is in flight so the UI can show a splash.
 */
@HiltViewModel
class AppGateViewModel @Inject constructor(
    private val settingsRepo: SettingsRepo,
    private val modelPaths: ModelPaths,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _startRoute = MutableStateFlow<StartRoute?>(null)
    val startRoute: StateFlow<StartRoute?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            _startRoute.value = withContext(dispatcher) {
                when {
                    !settingsRepo.legalAccepted.first() -> StartRoute.LEGAL
                    !modelPaths.modelsReady() -> StartRoute.DOWNLOAD
                    else -> StartRoute.HOME
                }
            }
        }
    }
}

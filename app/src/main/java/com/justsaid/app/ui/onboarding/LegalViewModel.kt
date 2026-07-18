package com.justsaid.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justsaid.app.data.repo.SettingsRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Records the user's acceptance of the legal disclaimer. */
@HiltViewModel
class LegalViewModel @Inject constructor(
    private val settingsRepo: SettingsRepo,
) : ViewModel() {

    private val _accepted = MutableStateFlow(false)
    val accepted: StateFlow<Boolean> = _accepted.asStateFlow()

    fun onAgree() {
        viewModelScope.launch {
            settingsRepo.setLegalAccepted(true)
            _accepted.value = true
        }
    }
}

package com.appname.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appname.notes.core.settings.AppearanceSettings
import com.appname.notes.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    /** Settings not read yet — the splash screen stays up so the wrong theme never flashes. */
    data object Loading : MainUiState

    data class Ready(val appearance: AppearanceSettings) : MainUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // Eagerly: the splash screen reads `.value` before any UI subscribes.
    val uiState: StateFlow<MainUiState> = settingsRepository.appearance
        .map<AppearanceSettings, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}

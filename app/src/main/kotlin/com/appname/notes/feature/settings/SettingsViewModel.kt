package com.appname.notes.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.settings.AppearanceSettings
import com.appname.notes.core.settings.FingerMode
import com.appname.notes.core.settings.HoldDuration
import com.appname.notes.core.settings.InkSettings
import com.appname.notes.core.settings.SettingsRepository
import com.appname.notes.core.settings.ThemeMode
import com.appname.notes.feature.textindex.DocumentTextSwitch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Settings screen needs to draw itself. */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val appearance: AppearanceSettings,
        val ink: InkSettings = InkSettings(),
        /** "Find text in photos and PDFs". */
        val documentText: Boolean = true,
        /** True after a save failed, until the screen has shown the message. */
        val showSaveError: Boolean = false,
        /** True after the search index was rebuilt, until the screen has shown the confirmation. */
        val showIndexRebuilt: Boolean = false,
    ) : SettingsUiState
}

/** Rebuilds the full-text search index (SRS 12.1). A tiny interface so tests don't need a database. */
fun interface SearchIndexRebuilder {
    suspend fun rebuild(): AppResult<Unit>
}

/**
 * Unidirectional data flow: the screen calls the `on…` functions (events go up),
 * and renders [uiState] (state comes down). The screen never touches the repository.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val indexRebuilder: SearchIndexRebuilder,
    private val documentTextSwitch: DocumentTextSwitch,
) : ViewModel() {

    private val saveFailed = MutableStateFlow(false)
    private val indexRebuilt = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(repository.appearance, repository.ink, repository.documentText, saveFailed, indexRebuilt) { appearance, ink, docText, failed, rebuilt ->
            SettingsUiState.Ready(appearance, ink, documentText = docText, showSaveError = failed, showIndexRebuilt = rebuilt)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState.Loading,
        )

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { handle(repository.setThemeMode(mode)) }
    }

    fun onDynamicColorChanged(enabled: Boolean) {
        viewModelScope.launch { handle(repository.setDynamicColor(enabled)) }
    }

    fun onShapeCorrectionChanged(enabled: Boolean) {
        viewModelScope.launch { handle(repository.updateInk { it.copy(shapeCorrection = enabled) }) }
    }

    fun onHoldDurationSelected(duration: HoldDuration) {
        viewModelScope.launch { handle(repository.updateInk { it.copy(holdDuration = duration) }) }
    }

    fun onFingerModeSelected(mode: FingerMode) {
        viewModelScope.launch { handle(repository.updateInk { it.copy(fingerMode = mode) }) }
    }

    /** On: documents get indexed in the background. Off: all extracted text is deleted (NFR-12). */
    fun onDocumentTextChanged(enabled: Boolean) {
        viewModelScope.launch {
            val saved = repository.setDocumentText(enabled)
            handle(saved)
            if (saved is AppResult.Success) documentTextSwitch.onChanged(enabled)
        }
    }

    fun onSaveErrorShown() {
        saveFailed.value = false
    }

    fun onRebuildSearchIndex() {
        viewModelScope.launch {
            when (indexRebuilder.rebuild()) {
                is AppResult.Success -> indexRebuilt.value = true
                is AppResult.Failure -> saveFailed.value = true
            }
        }
    }

    fun onIndexRebuiltShown() {
        indexRebuilt.value = false
    }

    private fun handle(result: AppResult<Unit>) {
        if (result is AppResult.Failure) saveFailed.value = true
    }

    private companion object {
        /** Keep collecting briefly after the screen stops, so rotation doesn't restart the flow. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

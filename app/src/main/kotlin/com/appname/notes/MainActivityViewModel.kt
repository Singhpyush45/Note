package com.appname.notes

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.appname.notes.feature.pdf.PdfImport
import com.appname.notes.feature.pdf.PdfImporter
import com.appname.notes.feature.pdf.messageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

/** A PDF opened or shared from another app ("Open with", "Share"). */
sealed interface SharedPdf {
    data object Importing : SharedPdf
    data class Opened(val noteId: String) : SharedPdf
    data class Failed(@param:StringRes val message: Int) : SharedPdf
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val pdfImporter: PdfImporter,
) : ViewModel() {

    private val _sharedPdf = MutableStateFlow<SharedPdf?>(null)
    val sharedPdf: StateFlow<SharedPdf?> = _sharedPdf.asStateFlow()

    /**
     * Imports a PDF another app handed us and asks the UI to open it. Runs in the ViewModel so rotation
     * doesn't restart it; the read permission lasts as long as this activity.
     */
    fun importShared(uri: Uri) {
        if (_sharedPdf.value == SharedPdf.Importing) return
        _sharedPdf.value = SharedPdf.Importing
        viewModelScope.launch {
            _sharedPdf.value = when (val r = pdfImporter.import(uri, notebookId = null)) {
                is PdfImport.Created -> SharedPdf.Opened(r.noteId.value)
                is PdfImport.Failed -> SharedPdf.Failed(r.error.messageRes)
            }
        }
    }

    fun sharedPdfHandled() {
        if (_sharedPdf.value != SharedPdf.Importing) _sharedPdf.value = null
    }

    // Eagerly: the splash screen reads `.value` before any UI subscribes.
    val uiState: StateFlow<MainUiState> = settingsRepository.appearance
        .map<AppearanceSettings, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}

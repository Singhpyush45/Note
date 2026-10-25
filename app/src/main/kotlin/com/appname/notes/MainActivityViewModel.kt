package com.appname.notes

import com.appname.notes.core.designsystem.component.WallpaperSpec
import com.appname.notes.core.settings.WallpaperChoice
import com.appname.notes.feature.settings.WallpaperPhotos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.appname.notes.R
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.feature.attachments.Attacher
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

/** Content opened or shared from another app ("Open with", "Share"): a PDF, or photos. */
sealed interface SharedImport {
    data class Importing(@param:StringRes val message: Int) : SharedImport
    /** [notice]: something to tell the user anyway (e.g. one of several photos couldn't be read). */
    data class Opened(val noteId: String, val type: NoteType, @param:StringRes val notice: Int? = null) : SharedImport
    data class Failed(@param:StringRes val message: Int) : SharedImport
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val pdfImporter: PdfImporter,
    private val attacher: Attacher,
    private val wallpaperPhotos: WallpaperPhotos,
) : ViewModel() {

    private val _shared = MutableStateFlow<SharedImport?>(null)
    val shared: StateFlow<SharedImport?> = _shared.asStateFlow()

    /**
     * Imports a PDF another app handed us and asks the UI to open it. Runs in the ViewModel so rotation
     * doesn't restart it; the read permission lasts as long as this activity.
     */
    fun importSharedPdf(uri: Uri) {
        if (_shared.value is SharedImport.Importing) return
        _shared.value = SharedImport.Importing(R.string.pdf_importing)
        viewModelScope.launch {
            _shared.value = when (val r = pdfImporter.import(uri, notebookId = null)) {
                is PdfImport.Created -> SharedImport.Opened(r.noteId.value, NoteType.PDF)
                is PdfImport.Failed -> SharedImport.Failed(r.error.messageRes)
            }
        }
    }

    /** Photos shared from another app (Gallery, Camera, a chat): a new note with them attached (FR-009). */
    fun importSharedImages(uris: List<Uri>) {
        if (uris.isEmpty() || _shared.value is SharedImport.Importing) return
        _shared.value = SharedImport.Importing(R.string.attach_importing_shared)
        viewModelScope.launch {
            val (noteId, outcome) = attacher.createNote(notebookId = null, uris = uris)
            _shared.value = when {
                // Some of several photos may have failed: the note still opens, and the problem is said.
                noteId != null -> SharedImport.Opened(noteId.value, NoteType.TEXT, notice = outcome.errorMessage)
                else -> SharedImport.Failed(outcome.errorMessage ?: R.string.attach_error_storage)
            }
        }
    }

    /** Opens a note from outside the app's screens (a tapped reminder notification). */
    fun openNote(noteId: String, type: NoteType) {
        if (_shared.value is SharedImport.Importing) return
        _shared.value = SharedImport.Opened(noteId, type)
    }

    fun sharedHandled() {
        if (_shared.value !is SharedImport.Importing) _shared.value = null
    }


    /** What's drawn behind the screens (owner brief §19); a photo is loaded off the main thread. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val wallpaper: StateFlow<WallpaperSpec> = settingsRepository.appearance
        .map { it.wallpaper }
        .distinctUntilChanged()
        .mapLatest { choice ->
            when (choice) {
                WallpaperChoice.None -> WallpaperSpec.None
                is WallpaperChoice.Preset -> WallpaperSpec.Preset(choice.preset)
                is WallpaperChoice.Photo ->
                    wallpaperPhotos.load(choice.fileName)?.let { WallpaperSpec.Photo(it) }
                        ?: WallpaperSpec.None
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WallpaperSpec.None)

    // Eagerly: the splash screen reads `.value` before any UI subscribes.
    val uiState: StateFlow<MainUiState> = settingsRepository.appearance
        .map<AppearanceSettings, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}

package com.appname.notes.feature.settings

import android.net.Uri
import com.appname.notes.core.designsystem.theme.Accent
import com.appname.notes.core.designsystem.theme.UiDensity
import com.appname.notes.core.settings.WallpaperChoice
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.compose.ui.graphics.ImageBitmap
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
        /** True after a picked wallpaper photo couldn't be read, until shown. */
        val showPhotoFailed: Boolean = false,
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
    private val wallpaperPhotos: WallpaperPhotos,
) : ViewModel() {

    private val saveFailed = MutableStateFlow(false)
    private val indexRebuilt = MutableStateFlow(false)
    private val photoFailed = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            combine(repository.appearance, repository.ink, repository.documentText, ::Triple),
            saveFailed,
            indexRebuilt,
            photoFailed,
        ) { (appearance, ink, docText), failed, rebuilt, photo ->
            SettingsUiState.Ready(appearance, ink, documentText = docText, showSaveError = failed, showIndexRebuilt = rebuilt, showPhotoFailed = photo)
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

    // ------------------------------------------------------------------ appearance (owner brief §19)

    private fun appearance(change: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch { handle(repository.updateAppearance(change)) }
    }

    /** A preset accent; turns wallpaper colours off (the accent is what the user just chose to see). */
    fun onAccentSelected(accent: Accent) = appearance { it.copy(accent = accent, dynamicColor = false) }

    fun onCustomHueSelected(hue: Float) = appearance { it.copy(accent = null, customHue = hue, dynamicColor = false) }

    fun onGlassIntensityChanged(value: Int) = appearance { it.copy(glassIntensity = value) }

    fun onBlurIntensityChanged(value: Int) = appearance { it.copy(blurIntensity = value) }

    fun onCornerRadiusChanged(value: Int) = appearance { it.copy(cornerRadius = value) }

    fun onDensitySelected(density: UiDensity) = appearance { it.copy(density = density) }

    /** A built-in wallpaper or none; a previously picked photo is deleted. */
    fun onWallpaperSelected(choice: WallpaperChoice) {
        viewModelScope.launch {
            val saved = repository.updateAppearance { it.copy(wallpaper = choice) }
            handle(saved)
            if (saved is AppResult.Success) wallpaperPhotos.deleteAllExcept((choice as? WallpaperChoice.Photo)?.fileName)
        }
    }

    /** A photo from the photo picker becomes the wallpaper. */
    fun onWallpaperPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val name = wallpaperPhotos.import(uri)
            if (name == null) {
                photoFailed.value = true
                return@launch
            }
            onWallpaperSelected(WallpaperChoice.Photo(name))
        }
    }

    fun onPhotoFailedShown() {
        photoFailed.value = false
    }

    fun onResetAppearance() {
        viewModelScope.launch {
            val reset = repository.resetAppearance()
            handle(reset)
            if (reset is AppResult.Success) wallpaperPhotos.deleteAllExcept(null)
        }
    }

    /** The wallpaper photo, for its thumbnail; null when none is chosen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val wallpaperPhoto: StateFlow<ImageBitmap?> = repository.appearance
        .map { (it.wallpaper as? WallpaperChoice.Photo)?.fileName }
        .distinctUntilChanged()
        .mapLatest { name -> name?.let { wallpaperPhotos.load(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

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

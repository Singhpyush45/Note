package com.appname.notes.core.settings

import com.appname.notes.core.domain.model.NoteSort

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User-chosen look of the app. Defaults are used until the user changes something. */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)

/** Library preferences remembered across launches. */
data class LibrarySettings(
    val sort: NoteSort = NoteSort.MODIFIED,
)

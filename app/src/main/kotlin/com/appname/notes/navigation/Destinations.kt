package com.appname.notes.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.appname.notes.R
import kotlinx.serialization.Serializable

/*
 * Every screen has a key. Keys are @Serializable so the back stack can be saved in the Bundle and
 * restored after rotation or process death.
 */

/** The notes library (list pane on tablets). */
@Serializable
data object HomeKey : NavKey

@Serializable
data object SettingsKey : NavKey

/** The editor for one note (detail pane on tablets). */
@Serializable
data class NoteEditorKey(val noteId: String) : NavKey

/** A handwritten note. Full screen on every device: writing needs the space (not a detail pane). */
@Serializable
data class InkEditorKey(val noteId: String) : NavKey

@Serializable
data object SearchKey : NavKey

@Serializable
data object NotebooksKey : NavKey

@Serializable
data object TagsKey : NavKey

/** Destinations shown in the bottom bar / navigation rail. */
enum class TopLevelDestination(
    val key: NavKey,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    HOME(HomeKey, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    SETTINGS(SettingsKey, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings"),
}

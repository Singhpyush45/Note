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
 * restored after rotation or process death. Screens that need arguments become data classes later,
 * e.g. `data class NoteEditorKey(val noteId: String) : NavKey`.
 */

@Serializable
data object HomeKey : NavKey

@Serializable
data object SettingsKey : NavKey

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

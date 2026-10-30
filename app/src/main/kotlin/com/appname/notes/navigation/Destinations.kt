package com.appname.notes.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
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

/** Monthly calendar and integrated To-do List (Argon 4 §4–5). */
@Serializable
data object CalendarKey : NavKey

/** Direct route into the To-do List view of the planner. */
@Serializable
data object TasksKey : NavKey

@Serializable
data object SettingsKey : NavKey

/** The editor for one note (detail pane on tablets). */
@Serializable
data class NoteEditorKey(val noteId: String) : NavKey

/** A handwritten note. Full screen on every device: writing needs the space (not a detail pane). */
@Serializable
/** [page]: a PDF page to show first (0-based PDF page index), e.g. from a search hit; null = the top. */
data class InkEditorKey(val noteId: String, val page: Int? = null) : NavKey

@Serializable
data object SearchKey : NavKey

@Serializable
data object NotebooksKey : NavKey

@Serializable
data object TagsKey : NavKey

/**
 * Choose template (owner brief §4). New notes go into [notebookId]/[tagId] ("" = none), the collection the
 * library was showing. [manage]: opened from Settings to manage the user's templates.
 */
@Serializable
data class TemplatesKey(val notebookId: String = "", val tagId: String = "", val manage: Boolean = false) : NavKey

/** Make ([templateId] "") or edit a custom template. */
@Serializable
data class TemplateEditorKey(val templateId: String = "") : NavKey

/** Destinations shown in the bottom bar / navigation rail in exact order: Notes · Calendar · To-do. */
enum class TopLevelDestination(
    val key: NavKey,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    HOME(HomeKey, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    CALENDAR(CalendarKey, R.string.nav_calendar, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_calendar"),
    TODO(TasksKey, R.string.todo_title, Icons.Filled.Checklist, Icons.Outlined.Checklist, "nav_todo"),
}

package com.appname.notes.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.appname.notes.R
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.feature.editor.NoteEditorRoute
import com.appname.notes.feature.editor.NoteEditorViewModel
import com.appname.notes.feature.ink.InkEditorRoute
import com.appname.notes.feature.ink.InkEditorViewModel
import com.appname.notes.feature.library.LibraryRoute
import com.appname.notes.feature.organize.NotebooksRoute
import com.appname.notes.feature.organize.TagsRoute
import com.appname.notes.feature.search.SearchRoute
import com.appname.notes.feature.settings.SettingsRoute

/**
 * App shell. Phones: one screen at a time with a bottom bar (hidden while editing to give the note the
 * whole screen). Tablets/landscape: navigation rail plus a list–detail layout — the library or search
 * results on the left and the open note on the right (SRS 8.1).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NotesApp(
    modifier: Modifier = Modifier,
    /** A note to open now (e.g. a PDF shared from another app); [onOpenNoteRequestHandled] is called after. */
    openNoteRequest: String? = null,
    onOpenNoteRequestHandled: () -> Unit = {},
) {
    // Saved across rotation and process death because every key is @Serializable.
    val backStack = rememberNavBackStack(HomeKey)
    LaunchedEffect(openNoteRequest) {
        if (openNoteRequest != null) {
            backStack.openNote(openNoteRequest, NoteType.PDF)
            onOpenNoteRequestHandled()
        }
    }
    val currentTopLevel = backStack.currentTopLevel()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isWide = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    // Phones: editors get the whole screen. Handwriting gets it on every device.
    val editingFullScreen = (!isWide && backStack.lastOrNull() !in topLevelKeys) || backStack.lastOrNull() is InkEditorKey

    val listDetail = rememberListDetailSceneStrategy<NavKey>()
    val openNote: (NoteId, NoteType) -> Unit = { id, type -> backStack.openNote(id.value, type) }
    val selectedNoteId = (backStack.lastOrNull() as? NoteEditorKey)?.noteId

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = if (editingFullScreen) {
            NavigationSuiteType.None
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
        },
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = destination == currentTopLevel
                item(
                    selected = selected,
                    onClick = { backStack.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null, // the label below already names it
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                    modifier = Modifier.testTag(destination.testTag),
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.pop() },
            sceneStrategies = listOf(listDetail),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(), // keeps scroll/text state per screen
                rememberViewModelStoreNavEntryDecorator(), // each screen gets its own ViewModels
            ),
            entryProvider = entryProvider {
                entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPlaceholder() })) {
                    LibraryRoute(
                        onOpenNote = openNote,
                        onOpenSearch = { backStack.push(SearchKey) },
                        onManageNotebooks = { backStack.push(NotebooksKey) },
                        onManageTags = { backStack.push(TagsKey) },
                        selectedNoteId = selectedNoteId,
                    )
                }
                entry<SearchKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPlaceholder() })) {
                    SearchRoute(onBack = { backStack.pop() }, onOpenNote = openNote)
                }
                entry<NoteEditorKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    val viewModel = hiltViewModel<NoteEditorViewModel, NoteEditorViewModel.Factory>(
                        creationCallback = { factory -> factory.create(key.noteId) },
                    )
                    NoteEditorRoute(
                        viewModel = viewModel,
                        onClose = { backStack.closeNote(key.noteId) },
                        onOpenNote = { openNote(it, NoteType.TEXT) },
                    )
                }
                entry<InkEditorKey> { key ->
                    val viewModel = hiltViewModel<InkEditorViewModel, InkEditorViewModel.Factory>(
                        creationCallback = { factory -> factory.create(key.noteId) },
                    )
                    InkEditorRoute(viewModel = viewModel, onClose = { backStack.closeNote(key.noteId) })
                }
                entry<NotebooksKey> { NotebooksRoute(onBack = { backStack.pop() }) }
                entry<TagsKey> { TagsRoute(onBack = { backStack.pop() }) }
                entry<SettingsKey> { SettingsRoute() }
            },
        )
    }
}

private val topLevelKeys: Set<NavKey> = TopLevelDestination.entries.map { it.key }.toSet()

/** Shown in the detail pane on tablets until a note is opened. */
@Composable
private fun DetailPlaceholder() {
    Column(
        Modifier.fillMaxSize().testTag("detail_placeholder"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
        Text(
            stringResource(R.string.select_note_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

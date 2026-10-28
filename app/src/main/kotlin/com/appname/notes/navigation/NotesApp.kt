package com.appname.notes.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.appname.notes.R
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.feature.calendar.PlannerRoute
import com.appname.notes.feature.calendar.PlannerSection
import com.appname.notes.feature.editor.NoteEditorRoute
import com.appname.notes.feature.editor.NoteEditorViewModel
import com.appname.notes.feature.ink.InkEditorRoute
import com.appname.notes.feature.ink.InkEditorViewModel
import com.appname.notes.feature.library.LibraryRoute
import com.appname.notes.feature.organize.NotebooksRoute
import com.appname.notes.feature.organize.TagsRoute
import com.appname.notes.feature.search.SearchRoute
import com.appname.notes.feature.settings.SettingsRoute
import com.appname.notes.feature.templates.TemplateEditorRoute
import com.appname.notes.feature.templates.TemplateEditorViewModel
import com.appname.notes.feature.templates.TemplatesRoute
import com.appname.notes.feature.templates.TemplatesViewModel

/**
 * App shell.
 * - Portrait phones: one screen at a time with a slim, floating, horizontally centered pill bottom navigation bar
 *   (`Notes · Calendar · Settings`), hidden while editing a note so the editor gets the whole screen.
 * - Tablets / landscape phones: navigation rail on the left plus a list–detail layout on tablets (SRS 8.1).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NotesApp(
    modifier: Modifier = Modifier,
    /** A note to open now (e.g. a PDF or photos shared from another app); [onOpenNoteRequestHandled] is called after. */
    openNoteRequest: Pair<String, NoteType>? = null,
    onOpenNoteRequestHandled: () -> Unit = {},
) {
    // Saved across rotation and process death because every key is @Serializable.
    val backStack = rememberNavBackStack(HomeKey)
    LaunchedEffect(openNoteRequest) {
        if (openNoteRequest != null) {
            backStack.openNote(openNoteRequest.first, openNoteRequest.second)
            onOpenNoteRequestHandled()
        }
    }
    val currentTopLevel = if (backStack.lastOrNull() == TasksKey) TopLevelDestination.CALENDAR else backStack.currentTopLevel()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val sizes = adaptiveInfo.windowSizeClass
    // List and note side by side only on big screens (tablets); a phone turned sideways is wide but short, and
    // there the note gets the whole screen too (owner request: no bottom bar over the editor in landscape).
    val isWide = sizes.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) &&
        sizes.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val shortScreen = !sizes.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    // Phones: editors get the whole screen. Handwriting gets it on every device.
    val editingFullScreen = (!isWide && backStack.lastOrNull() !in topLevelKeys) || backStack.lastOrNull() is InkEditorKey
    val useRail = !editingFullScreen && (isWide || shortScreen)
    val showFloatingBottomPill = !editingFullScreen && !useRail

    val listDetail = rememberListDetailSceneStrategy<NavKey>()
    val openNote: (NoteId, NoteType) -> Unit = { id, type -> backStack.openNote(id.value, type) }
    val selectedNoteId = (backStack.lastOrNull() as? NoteEditorKey)?.noteId

    NavigationSuiteScaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = Color.Transparent,
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
        ),
        layoutType = if (useRail) NavigationSuiteType.NavigationRail else NavigationSuiteType.None,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = destination == currentTopLevel
                item(
                    selected = selected,
                    onClick = { backStack.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                    modifier = Modifier.testTag(destination.testTag),
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.pop() },
                sceneStrategies = listOf(listDetail),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPlaceholder() })) {
                        LibraryRoute(
                            onOpenNote = openNote,
                            onOpenSearch = { backStack.push(SearchKey) },
                            onManageNotebooks = { backStack.push(NotebooksKey) },
                            onManageTags = { backStack.push(TagsKey) },
                            onChooseTemplate = { notebook, tag -> backStack.push(TemplatesKey(notebook.orEmpty(), tag.orEmpty())) },
                            onOpenTodoList = { backStack.push(TasksKey) },
                            selectedNoteId = selectedNoteId,
                        )
                    }
                    entry<CalendarKey> {
                        PlannerRoute(
                            onOpenNote = openNote,
                            initialSection = PlannerSection.CALENDAR,
                        )
                    }
                    entry<TasksKey> {
                        PlannerRoute(
                            onOpenNote = openNote,
                            initialSection = PlannerSection.TASKS,
                        )
                    }
                    entry<SearchKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPlaceholder() })) {
                        SearchRoute(onBack = { backStack.pop() }, onOpenNote = { id, type, page -> backStack.openNote(id.value, type, page) })
                    }
                    entry<NoteEditorKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                        val viewModel = hiltViewModel<NoteEditorViewModel, NoteEditorViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key.noteId) },
                        )
                        NoteEditorRoute(
                            viewModel = viewModel,
                            onClose = { backStack.closeNote(key.noteId) },
                            onOpenNote = { openNote(it, NoteType.TEXT) },
                            onOpenPdfNote = { openNote(it, NoteType.PDF) },
                        )
                    }
                    entry<InkEditorKey> { key ->
                        val viewModel = hiltViewModel<InkEditorViewModel, InkEditorViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key.noteId, key.page ?: -1) },
                        )
                        InkEditorRoute(viewModel = viewModel, onClose = { backStack.closeNote(key.noteId) })
                    }
                    entry<NotebooksKey> { NotebooksRoute(onBack = { backStack.pop() }) }
                    entry<TagsKey> { TagsRoute(onBack = { backStack.pop() }) }
                    entry<SettingsKey> { SettingsRoute(onManageTemplates = { backStack.push(TemplatesKey(manage = true)) }) }
                    entry<TemplatesKey> { key ->
                        val viewModel = hiltViewModel<TemplatesViewModel, TemplatesViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key.notebookId, key.tagId) },
                        )
                        TemplatesRoute(
                            viewModel = viewModel,
                            manage = key.manage,
                            onBack = { backStack.pop() },
                            onNoteCreated = { id -> backStack.pop(); openNote(id, NoteType.INK) },
                            onEdit = { id -> backStack.push(TemplateEditorKey(id)) },
                            onNewTemplate = { backStack.push(TemplateEditorKey()) },
                        )
                    }
                    entry<TemplateEditorKey> { key ->
                        val viewModel = hiltViewModel<TemplateEditorViewModel, TemplateEditorViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key.templateId) },
                        )
                        TemplateEditorRoute(viewModel = viewModel, onClose = { backStack.pop() })
                    }
                },
            )

            if (showFloatingBottomPill) {
                FloatingPillBottomBar(
                    current = currentTopLevel,
                    onSelect = { backStack.navigateToTopLevel(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * Slim, floating, horizontally centered pill/capsule bottom navigation bar (Argon 4 §11).
 *
 * Uses rounded outer capsule corners, balanced side margins, subtle surface contrast matching the Notes theme,
 * and a restrained rounded pill highlight for the selected tab (`Notes · Calendar · Settings`).
 */
@Composable
private fun FloatingPillBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f)
    }
    val borderStroke = BorderStroke(
        width = 0.75.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.38f else 0.50f),
    )
    Surface(
        shape = CircleShape,
        color = containerColor,
        border = borderStroke,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .widthIn(max = 336.dp)
            .fillMaxWidth()
            .height(54.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = destination == current
                val label = stringResource(destination.label)
                val pillBg = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.78f else 0.90f)
                } else {
                    Color.Transparent
                }
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(50))
                        .background(pillBg, RoundedCornerShape(50))
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(destination) },
                        )
                        .testTag(destination.testTag),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(19.dp),
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val topLevelKeys: Set<NavKey> = TopLevelDestination.entries.map { it.key }.toSet() + TasksKey

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

package com.appname.notes.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.component.liquidGlassPill
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
 * - Portrait phones: one screen at a time with a Liquid Glass floating capsule bottom navigation bar
 *   (`Notes · Calendar · To-do`) featuring a smooth sliding selection pill, hidden while editing a note or inside Settings.
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
    val currentTopLevel = backStack.currentTopLevel()
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
    val onSettingsScreen = backStack.lastOrNull() == SettingsKey
    val showFloatingBottomPill = !editingFullScreen && !useRail && !onSettingsScreen

    val listDetail = rememberListDetailSceneStrategy<NavKey>()
    val openNote: (NoteId, NoteType) -> Unit = { id, type -> backStack.openNote(id.value, type) }
    val openSettings: () -> Unit = { backStack.push(SettingsKey) }
    val selectedNoteId = (backStack.lastOrNull() as? NoteEditorKey)?.noteId

    AppLaunchContainer(modifier = modifier) {
        NavigationSuiteScaffold(
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
                                onOpenSettings = openSettings,
                                selectedNoteId = selectedNoteId,
                            )
                        }
                        entry<CalendarKey> {
                            PlannerRoute(
                                onOpenNote = openNote,
                                initialSection = PlannerSection.CALENDAR,
                                onSwitchTopLevel = { sec ->
                                    backStack.navigateToTopLevel(
                                        if (sec == PlannerSection.TASKS) TopLevelDestination.TODO else TopLevelDestination.CALENDAR,
                                    )
                                },
                                onOpenSettings = openSettings,
                            )
                        }
                        entry<TasksKey> {
                            PlannerRoute(
                                onOpenNote = openNote,
                                initialSection = PlannerSection.TASKS,
                                onSwitchTopLevel = { sec ->
                                    backStack.navigateToTopLevel(
                                        if (sec == PlannerSection.TASKS) TopLevelDestination.TODO else TopLevelDestination.CALENDAR,
                                    )
                                },
                                onOpenSettings = openSettings,
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
                        entry<SettingsKey> {
                            SettingsRoute(
                                onBack = { backStack.pop() },
                                onManageTemplates = { backStack.push(TemplatesKey(manage = true)) },
                            )
                        }
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
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * Liquid Glass floating capsule bottom navigation bar (`Notes · Calendar · To-do`).
 *
 * Features a physical frosted glass outer capsule and a smooth-sliding Liquid Glass indicator pill
 * that glides horizontally between tabs with spring physics when switching between Notes, Calendar, and To-do.
 */
@Composable
private fun FloatingPillBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val entries = TopLevelDestination.entries
    val selectedIndex = entries.indexOf(current).coerceAtLeast(0)

    GlassSurface(
        shape = CircleShape,
        tint = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        elevation = 6.dp,
        modifier = modifier
            .widthIn(max = 352.dp)
            .fillMaxWidth()
            .height(58.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 5.dp)
                .selectableGroup(),
        ) {
            val tabCount = entries.size
            val spacing = 4.dp
            val tabWidth = (maxWidth - spacing * (tabCount - 1)) / tabCount
            val targetOffset = (tabWidth + spacing) * selectedIndex

            val slidingOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "bottomBarSlidingPill",
            )

            // Smoothly sliding Liquid Glass active pill
            Box(
                modifier = Modifier
                    .offset(x = slidingOffset)
                    .width(tabWidth)
                    .height(48.dp)
                    .clip(CircleShape)
                    .liquidGlassPill(
                        selected = true,
                        dark = isDark,
                        accentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        cornerRadius = 50.dp,
                    ),
            )

            // Foreground tab icons & labels
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                entries.forEach { destination ->
                    val selected = destination == current
                    val label = stringResource(destination.label)
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "tabColor",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.04f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "tabScale",
                    )
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelect(destination) },
                            )
                            .testTag(destination.testTag),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
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

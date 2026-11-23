package com.appname.notes.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.designsystem.theme.NoRippleIndication
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
 * Zero-Jitter 120Hz App Shell:
 * - Keeps all 3 top-level destinations (Notes, Calendar, To-do) pre-warmed & alive in a GPU-driven
 *   HorizontalPager(beyondViewportPageCount = 2) so switching tabs never tears down or cold-composes
 *   heavy grids/calendars mid-animation.
 * - Synchronizes the floating Liquid Glass bottom pill 1:1 with the pager's continuous scroll offset.
 */
@Composable
fun NotesApp(
    modifier: Modifier = Modifier,
    openNoteRequest: Pair<String, NoteType>? = null,
    onOpenNoteRequestHandled: () -> Unit = {},
) {
    val isDark = LocalDarkTheme.current
    var launchReady by remember { mutableStateOf(value = false) }
    LaunchedEffect(Unit) { launchReady = true }
    val launchAlpha by animateFloatAsState(
        targetValue = if (launchReady) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "appLaunchFade",
    )
    val launchScale by animateFloatAsState(
        targetValue = if (launchReady) 1f else 0.985f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1050f),
        label = "appLaunchScale",
    )

    val backStack = rememberNavBackStack(HomeKey)
    LaunchedEffect(openNoteRequest) {
        if (openNoteRequest != null) {
            backStack.openNote(openNoteRequest.first, openNoteRequest.second)
            onOpenNoteRequestHandled()
        }
    }
    val currentTopLevel = backStack.currentTopLevel()
    val showFloatingBottomPill = backStack.lastOrNull() in topLevelKeys
    val openNote: (NoteId, NoteType) -> Unit = { id, type -> backStack.openNote(id.value, type) }
    val openSettings: () -> Unit = { backStack.push(SettingsKey) }
    val selectedNoteId = (backStack.lastOrNull() as? NoteEditorKey)?.noteId

    // Persistent 3-tab HorizontalPager keeps Notes, Calendar, and To-do warm in memory (zero cold-compose jitter)
    val topLevelEntries = TopLevelDestination.entries
    val targetTabIndex = topLevelEntries.indexOf(currentTopLevel).coerceIn(0, topLevelEntries.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = targetTabIndex,
        pageCount = { topLevelEntries.size },
    )
    LaunchedEffect(targetTabIndex) {
        if (pagerState.currentPage != targetTabIndex) {
            pagerState.animateScrollToPage(
                page = targetTabIndex,
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = 460f,
                ),
            )
        }
    }

    val editorialBackgroundBrush = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF141518),
                    Color(0xFF101114),
                    Color(0xFF0C0D10),
                ),
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFF8F5EE),
                    Color(0xFFF3EFE6),
                    Color(0xFFEDE8DD),
                ),
            )
        }
    }

    CompositionLocalProvider(
        LocalIndication provides NoRippleIndication,
        LocalRippleConfiguration provides null,
    ) {
        AppLaunchContainer(modifier = modifier) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(editorialBackgroundBrush)
                    .graphicsLayer {
                        alpha = launchAlpha
                        scaleX = launchScale
                        scaleY = launchScale
                    },
            ) {
                // Layer 1: Always-warm 3-tab HorizontalPager (Notes · Calendar · To-do)
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 2,
                    userScrollEnabled = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (!showFloatingBottomPill) Modifier.clearAndSetSemantics { } else Modifier),
                ) { pageIndex ->
                    when (topLevelEntries[pageIndex]) {
                        TopLevelDestination.HOME -> {
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
                        TopLevelDestination.CALENDAR -> {
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
                        TopLevelDestination.TODO -> {
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
                    }
                }

                // Layer 2: Detail routes (Editors, Settings, Search, Notebooks, Tags, Templates) via NavDisplay
                // Top-level entries render empty transparent passthrough so Layer 1 stays visible & warm with zero teardown.
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.pop() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<HomeKey> { }
                        entry<CalendarKey> { }
                        entry<TasksKey> { }
                        entry<SearchKey> {
                            Box(Modifier.fillMaxSize().background(editorialBackgroundBrush)) {
                                SearchRoute(onBack = { backStack.pop() }, onOpenNote = { id, type, page -> backStack.openNote(id.value, type, page) })
                            }
                        }
                        entry<NoteEditorKey> { key ->
                            val viewModel = hiltViewModel<NoteEditorViewModel, NoteEditorViewModel.Factory>(
                                creationCallback = { factory -> factory.create(key.noteId) },
                            )
                            Box(Modifier.fillMaxSize().background(editorialBackgroundBrush)) {
                                NoteEditorRoute(
                                    viewModel = viewModel,
                                    onClose = { backStack.closeNote(key.noteId) },
                                    onOpenNote = { openNote(it, NoteType.TEXT) },
                                    onOpenPdfNote = { openNote(it, NoteType.PDF) },
                                )
                            }
                        }
                        entry<InkEditorKey> { key ->
                            val viewModel = hiltViewModel<InkEditorViewModel, InkEditorViewModel.Factory>(
                                creationCallback = { factory -> factory.create(key.noteId, key.page ?: -1) },
                            )
                            Box(Modifier.fillMaxSize().background(editorialBackgroundBrush)) {
                                InkEditorRoute(viewModel = viewModel, onClose = { backStack.closeNote(key.noteId) })
                            }
                        }
                        entry<NotebooksKey> {
                            Box(Modifier.fillMaxSize().background(editorialBackgroundBrush)) {
                                NotebooksRoute(onBack = { backStack.pop() })
                            }
                        }
                        entry<TagsKey> {
                            Box(Modifier.fillMaxSize().background(editorialBackgroundBrush)) {
                                TagsRoute(onBack = { backStack.pop() })
                            }
                        }
                        entry<SettingsKey> {
                            Box(Modifier.fillMaxSize().background(editorialBackgroundBrush)) {
                                SettingsRoute(
                                    onBack = { backStack.pop() },
                                    onManageTemplates = { backStack.push(TemplatesKey(manage = true)) },
                                )
                            }
                        }
                        entry<TemplatesKey> { key ->
                            val viewModel = hiltViewModel<TemplatesViewModel, TemplatesViewModel.Factory>(
                                creationCallback = { factory -> factory.create(key.notebookId, key.tagId) },
                            )
                            Box(Modifier.fillMaxSize().background(editorialBackgroundBrush)) {
                                TemplatesRoute(
                                    viewModel = viewModel,
                                    manage = key.manage,
                                    onBack = { backStack.pop() },
                                    onNoteCreated = { id -> backStack.pop(); openNote(id, NoteType.INK) },
                                    onEdit = { id -> backStack.push(TemplateEditorKey(id)) },
                                    onNewTemplate = { backStack.push(TemplateEditorKey()) },
                                )
                            }
                        }
                        entry<TemplateEditorKey> { key ->
                            val viewModel = hiltViewModel<TemplateEditorViewModel, TemplateEditorViewModel.Factory>(
                                creationCallback = { factory -> factory.create(key.templateId) },
                            )
                            Box(Modifier.fillMaxSize().background(editorialBackgroundBrush)) {
                                TemplateEditorRoute(viewModel = viewModel, onClose = { backStack.pop() })
                            }
                        }
                    },
                )

                // Layer 3: Floating Liquid Glass Bottom Pill synced 1:1 with the GPU pager offset
                if (showFloatingBottomPill) {
                    val continuousPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        .coerceIn(0f, (topLevelEntries.size - 1).toFloat())
                    FloatingPillBottomBar(
                        current = currentTopLevel,
                        continuousPosition = continuousPage,
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

@Composable
private fun FloatingPillBottomBar(
    current: TopLevelDestination,
    continuousPosition: Float,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val entries = TopLevelDestination.entries

    GlassSurface(
        shape = CircleShape,
        tint = if (isDark) Color(0xFF17181C) else Color(0xFFFAF7F2),
        elevation = 6.dp,
        modifier = modifier
            .widthIn(max = 352.dp)
            .fillMaxWidth()
            .height(56.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .selectableGroup(),
        ) {
            val tabCount = entries.size
            val spacing = 4.dp
            val tabWidth = (maxWidth - (spacing * (tabCount - 1))) / tabCount
            val step = tabWidth + spacing

            val pillFill = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFF1E3F32).copy(alpha = 0.11f)
            val pillBorder = if (isDark) Color.White.copy(alpha = 0.24f) else Color(0xFF1E3F32).copy(alpha = 0.22f)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = step.toPx() * continuousPosition
                    }
                    .width(tabWidth)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(pillFill, CircleShape)
                    .border(0.8.dp, pillBorder, CircleShape),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                entries.forEach { destination ->
                    val selected = destination == current
                    val label = stringResource(destination.label)
                    val contentColor by animateColorAsState(
                        targetValue = when {
                            selected && isDark -> Color(0xFFF6F3EC)
                            selected && !isDark -> Color(0xFF1B3B2F)
                            isDark -> Color(0xFFA39E93)
                            else -> Color(0xFF6E665C)
                        },
                        animationSpec = tween(durationMillis = 120),
                        label = "tabColor",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.03f else 1.0f,
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

private val topLevelKeys: Set<NavKey> = TopLevelDestination.entries.asSequence().map { it.key }.toSet()

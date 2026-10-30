package com.appname.notes.feature.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Settings
import com.appname.notes.core.designsystem.component.liquidGlassPill
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.component.backdropSource
import com.appname.notes.core.designsystem.component.rememberBackdrop
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.domain.model.CalendarEvent
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.TaskFilter
import com.appname.notes.core.domain.model.TodoTask
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.ui.platform.LocalContext
import com.appname.notes.feature.library.ConfirmDialog
import com.appname.notes.feature.pro.FocusTimerChip
import com.appname.notes.feature.pro.FocusTimerDialog
import com.appname.notes.feature.pro.NoteProStore
import com.appname.notes.feature.pro.SubtaskItem
import com.appname.notes.feature.pro.TaskPriority
import com.appname.notes.feature.pro.TaskProMeta
import com.appname.notes.feature.pro.TaskRecurrence
import java.util.UUID
import com.appname.notes.ui.common.MessageEffect
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

object PlannerTestTags {
    const val SCREEN = "screen_planner"
    const val TAB_CALENDAR = "planner_tab_calendar"
    const val TAB_TASKS = "planner_tab_tasks"
    const val PREV_MONTH = "calendar_prev_month"
    const val NEXT_MONTH = "calendar_next_month"
    const val TODAY_BUTTON = "calendar_today"
    const val MONTH_LABEL = "calendar_month_label"
    const val FAB_ADD = "planner_fab_add"
    const val ADD_TASK_BUTTON = "planner_add_task"
    const val ADD_EVENT_BUTTON = "planner_add_event"
    const val FILTER_PENDING = "todo_filter_pending"
    const val FILTER_COMPLETED = "todo_filter_completed"
    const val FILTER_ALL = "todo_filter_all"
    const val TASK_DIALOG = "todo_task_dialog"
    const val TASK_TITLE_FIELD = "todo_task_title"
    const val TASK_DESC_FIELD = "todo_task_desc"
    const val TASK_SAVE = "todo_task_save"
    const val EVENT_DIALOG = "calendar_event_dialog"
    const val EVENT_TITLE_FIELD = "calendar_event_title"
    const val EVENT_NOTES_FIELD = "calendar_event_notes"
    const val EVENT_SAVE = "calendar_event_save"

    fun dayCell(date: LocalDate) = "calendar_day_$date"
    fun taskItem(id: String) = "todo_task_$id"
    fun taskCheck(id: String) = "todo_check_$id"
    fun eventItem(id: String) = "calendar_event_$id"
}

@Composable
fun PlannerRoute(
    onOpenNote: (NoteId, NoteType) -> Unit,
    initialSection: PlannerSection? = null,
    onSwitchTopLevel: ((PlannerSection) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: PlannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(initialSection) {
        if (initialSection != null) viewModel.setSection(initialSection)
    }
    PlannerScreen(
        state = state,
        onSectionChange = { sec ->
            viewModel.setSection(sec)
            onSwitchTopLevel?.invoke(sec)
        },
        onFilterChange = viewModel::setTaskFilter,
        onSelectDate = viewModel::selectDate,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onToday = viewModel::goToToday,
        onSaveTask = viewModel::saveTask,
        onToggleTask = viewModel::toggleTaskCompleted,
        onDeleteTask = viewModel::deleteTask,
        onSaveEvent = viewModel::saveEvent,
        onDeleteEvent = viewModel::deleteEvent,
        onOpenNote = onOpenNote,
        onOpenSettings = onOpenSettings,
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    state: PlannerUiState,
    onSectionChange: (PlannerSection) -> Unit,
    onFilterChange: (TaskFilter) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSaveTask: (
        editingId: com.appname.notes.core.domain.model.TaskId?,
        title: String,
        description: String,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        enableReminder: Boolean,
        sourceNoteId: NoteId?,
        sourceNoteTitle: String?,
        proMeta: TaskProMeta?,
    ) -> Unit,
    onToggleTask: (TodoTask) -> Unit,
    onDeleteTask: (com.appname.notes.core.domain.model.TaskId) -> Unit,
    onSaveEvent: (
        editingId: com.appname.notes.core.domain.model.CalendarEventId?,
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        notes: String,
        alsoCreateTask: Boolean,
        sourceNoteId: NoteId?,
    ) -> Unit,
    onDeleteEvent: (com.appname.notes.core.domain.model.CalendarEventId) -> Unit,
    onOpenNote: (NoteId, NoteType) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, onMessageShown)

    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TodoTask?>(null) }
    var defaultTaskDate by remember { mutableStateOf<LocalDate?>(null) }
    var confirmDeleteTask by remember { mutableStateOf<TodoTask?>(null) }

    var showEventDialog by rememberSaveable { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var confirmDeleteEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showFocusTimer by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { NoteProStore.ensureInit(context) }
    if (showFocusTimer) {
        FocusTimerDialog(onDismiss = { showFocusTimer = false })
    }

    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    val backdrop = rememberBackdrop()

    Scaffold(
        modifier = Modifier.testTag(PlannerTestTags.SCREEN),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (state.section == PlannerSection.TASKS) {
                        editingTask = null
                        defaultTaskDate = null
                        showTaskDialog = true
                    } else {
                        editingEvent = null
                        showEventDialog = true
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 72.dp)
                    .size(60.dp)
                    .testTag(PlannerTestTags.FAB_ADD),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(
                        if (state.section == PlannerSection.TASKS) R.string.todo_new_task else R.string.calendar_new_event,
                    ),
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                when (state.section) {
                    PlannerSection.CALENDAR -> CalendarContent(
                        state = state,
                        topPadding = headerHeight,
                        onSelectDate = onSelectDate,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                        onToday = onToday,
                        onAddEventForDate = {
                            editingEvent = null
                            showEventDialog = true
                        },
                        onAddTaskForDate = {
                            editingTask = null
                            defaultTaskDate = state.selectedDate
                            showTaskDialog = true
                        },
                        onEditEvent = {
                            editingEvent = it
                            showEventDialog = true
                        },
                        onDeleteEvent = { confirmDeleteEvent = it },
                        onToggleTask = onToggleTask,
                        onEditTask = {
                            editingTask = it
                            showTaskDialog = true
                        },
                        onDeleteTask = { confirmDeleteTask = it },
                        onOpenNote = onOpenNote,
                        notes = state.notes,
                    )
                    PlannerSection.TASKS -> TodoListContent(
                        state = state,
                        topPadding = headerHeight,
                        onFilterChange = onFilterChange,
                        onToggleTask = onToggleTask,
                        onEditTask = {
                            editingTask = it
                            showTaskDialog = true
                        },
                        onDeleteTask = { confirmDeleteTask = it },
                        onOpenNote = { noteId ->
                            val type = state.notes.firstOrNull { it.id == noteId }?.type ?: NoteType.TEXT
                            onOpenNote(noteId, type)
                        },
                    )
                }
            }

            // Floating Liquid Glass Header with smooth sliding Calendar | To-do List switcher
            val isDark = LocalDarkTheme.current
            GlassSurface(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = 4.dp,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { with(density) { headerHeight = it.height.toDp() } }
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val badge = if (state.pendingTaskCount > 0) " (${state.pendingTaskCount})" else ""
                    val tabs = listOf(
                        Triple(PlannerSection.CALENDAR, stringResource(R.string.nav_calendar), Icons.Outlined.CalendarMonth to PlannerTestTags.TAB_CALENDAR),
                        Triple(PlannerSection.TASKS, stringResource(R.string.todo_title) + badge, Icons.Outlined.Checklist to PlannerTestTags.TAB_TASKS),
                    )
                    val selectedIndex = if (state.section == PlannerSection.TASKS) 1 else 0

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(50))
                            .liquidGlassPill(
                                selected = false,
                                dark = isDark,
                                accentColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                cornerRadius = 50.dp,
                            )
                            .padding(4.dp)
                            .selectableGroup(),
                    ) {
                        val spacing = 4.dp
                        val tabW = (maxWidth - spacing) / 2
                        val targetX = (tabW + spacing) * selectedIndex
                        val slideX by animateDpAsState(
                            targetValue = targetX,
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                            label = "plannerHeaderSlide",
                        )

                        Box(
                            modifier = Modifier
                                .offset(x = slideX)
                                .width(tabW)
                                .height(36.dp)
                                .clip(RoundedCornerShape(50))
                                .liquidGlassPill(
                                    selected = true,
                                    dark = isDark,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    cornerRadius = 50.dp,
                                ),
                        )

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                        ) {
                            tabs.forEach { (sec, label, iconAndTag) ->
                                val selected = state.section == sec
                                val contentColor by animateColorAsState(
                                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    animationSpec = tween(180),
                                    label = "plannerTabText",
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(50))
                                        .selectable(
                                            selected = selected,
                                            role = Role.Tab,
                                            onClick = { onSectionChange(sec) },
                                        )
                                        .padding(horizontal = 10.dp)
                                        .testTag(iconAndTag.second),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(iconAndTag.first, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))
                    FocusTimerChip(onClick = { showFocusTimer = true })
                    if (state.section == PlannerSection.CALENDAR && (state.selectedDate != state.today || state.currentMonth != YearMonth.from(state.today))) {
                        IconButton(onClick = onToday, modifier = Modifier.size(36.dp).testTag(PlannerTestTags.TODAY_BUTTON)) {
                            Icon(Icons.Outlined.Today, stringResource(R.string.calendar_today), modifier = Modifier.size(20.dp))
                        }
                    }
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp).testTag("planner_settings_btn")) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.nav_settings), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        TaskEditorDialog(
            initial = editingTask,
            defaultDate = defaultTaskDate,
            notes = state.notes,
            onSave = { title, desc, date, time, remind, noteId, noteTitle, proMeta ->
                onSaveTask(editingTask?.id, title, desc, date, time, remind, noteId, noteTitle, proMeta)
                showTaskDialog = false
                editingTask = null
                defaultTaskDate = null
            },
            onDelete = editingTask?.let { task ->
                {
                    showTaskDialog = false
                    editingTask = null
                    confirmDeleteTask = task
                }
            },
            onDismiss = {
                showTaskDialog = false
                editingTask = null
                defaultTaskDate = null
            },
        )
    }

    if (showEventDialog) {
        EventEditorDialog(
            initial = editingEvent,
            defaultDate = state.selectedDate,
            notes = state.notes,
            onSave = { title, date, start, end, notesText, alsoTask, sourceNoteId ->
                onSaveEvent(editingEvent?.id, title, date, start, end, notesText, alsoTask, sourceNoteId)
                showEventDialog = false
                editingEvent = null
            },
            onDelete = editingEvent?.let { ev ->
                {
                    showEventDialog = false
                    editingEvent = null
                    confirmDeleteEvent = ev
                }
            },
            onDismiss = {
                showEventDialog = false
                editingEvent = null
            },
        )
    }

    confirmDeleteTask?.let { task ->
        ConfirmDialog(
            title = stringResource(R.string.todo_delete_confirm_title),
            body = stringResource(R.string.todo_delete_confirm_body, task.title),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                onDeleteTask(task.id)
                confirmDeleteTask = null
            },
            onDismiss = { confirmDeleteTask = null },
        )
    }

    confirmDeleteEvent?.let { event ->
        ConfirmDialog(
            title = stringResource(R.string.calendar_delete_confirm_title),
            body = stringResource(R.string.calendar_delete_confirm_body, event.title),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                onDeleteEvent(event.id)
                confirmDeleteEvent = null
            },
            onDismiss = { confirmDeleteEvent = null },
        )
    }
}

@Composable
private fun CalendarContent(
    state: PlannerUiState,
    topPadding: androidx.compose.ui.unit.Dp,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onAddEventForDate: () -> Unit,
    onAddTaskForDate: () -> Unit,
    onEditEvent: (CalendarEvent) -> Unit,
    onDeleteEvent: (CalendarEvent) -> Unit,
    onToggleTask: (TodoTask) -> Unit,
    onEditTask: (TodoTask) -> Unit,
    onDeleteTask: (TodoTask) -> Unit,
    onOpenNote: (NoteId, NoteType) -> Unit,
    notes: List<NoteSummary>,
) {
    val spacing = AppTheme.spacing
    val activeDates = remember(state.currentMonth, state.allTasks, state.events, state.notes) {
        state.datesWithItems()
    }
    val dateEvents = state.eventsForSelectedDate
    val dateTasks = state.tasksForSelectedDate
    val dateReminders = remember(state.selectedDate, state.notes) {
        state.notesWithReminderOn(state.selectedDate)
    }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = topPadding + spacing.sm,
            bottom = 148.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "calendar_card") {
            var weekMode by rememberSaveable { mutableStateOf(false) }
            GlassSurface(
                shape = MaterialTheme.shapes.large,
                elevation = 2.dp,
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            ) {
                Column(Modifier.padding(spacing.md)) {
                    MonthHeaderRow(
                        month = state.currentMonth,
                        weekMode = weekMode,
                        onToggleWeekMode = { weekMode = !weekMode },
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                    )
                    Spacer(Modifier.height(8.dp))
                    WeekdayHeaderRow()
                    Spacer(Modifier.height(4.dp))
                    if (weekMode) {
                        WeekGrid(
                            selectedDate = state.selectedDate,
                            today = state.today,
                            datesWithItems = activeDates,
                            onSelectDate = onSelectDate,
                        )
                    } else {
                        MonthGrid(
                            yearMonth = state.currentMonth,
                            selectedDate = state.selectedDate,
                            today = state.today,
                            datesWithItems = activeDates,
                            onSelectDate = onSelectDate,
                        )
                    }
                }
            }
        }

        item(key = "selected_date_header") {
            Row(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.selectedDate.format(dateFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (state.selectedDate == state.today) {
                        Text(
                            text = stringResource(R.string.calendar_today),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAddTaskForDate,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag(PlannerTestTags.ADD_TASK_BUTTON),
                    ) {
                        Icon(Icons.Outlined.Checklist, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.todo_add_short), style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onAddEventForDate,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag(PlannerTestTags.ADD_EVENT_BUTTON),
                    ) {
                        Icon(Icons.Outlined.Event, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.calendar_add_short), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (dateEvents.isEmpty() && dateTasks.isEmpty() && dateReminders.isEmpty()) {
            item(key = "empty_date") {
                GlassSurface(
                    shape = MaterialTheme.shapes.large,
                    elevation = 1.dp,
                    modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.calendar_empty_day_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.calendar_empty_day_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        } else {
            items(dateEvents, key = { "ev_" + it.id.value }) { event ->
                Box(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                    CalendarEventCard(
                        event = event,
                        onEdit = { onEditEvent(event) },
                        onDelete = { onDeleteEvent(event) },
                        onOpenSourceNote = event.sourceNoteId?.let { noteId ->
                            {
                                val type = notes.firstOrNull { it.id == noteId }?.type ?: NoteType.TEXT
                                onOpenNote(noteId, type)
                            }
                        },
                    )
                }
            }
            items(dateTasks, key = { "tk_" + it.id.value }) { task ->
                Box(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                    TaskCard(
                        task = task,
                        onToggle = { onToggleTask(task) },
                        onEdit = { onEditTask(task) },
                        onDelete = { onDeleteTask(task) },
                        onOpenNote = task.sourceNoteId?.let { noteId ->
                            {
                                val type = notes.firstOrNull { it.id == noteId }?.type ?: NoteType.TEXT
                                onOpenNote(noteId, type)
                            }
                        },
                    )
                }
            }
            items(dateReminders, key = { "rem_" + it.id.value }) { note ->
                Box(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                    NoteReminderItemCard(note = note, onClick = { onOpenNote(note.id, note.type) })
                }
            }
        }
    }
}

@Composable
private fun MonthHeaderRow(
    month: YearMonth,
    weekMode: Boolean = false,
    onToggleWeekMode: () -> Unit = {},
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPreviousMonth, modifier = Modifier.testTag(PlannerTestTags.PREV_MONTH)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.calendar_prev_month))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = month.atDay(1).format(formatter),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(PlannerTestTags.MONTH_LABEL).semantics { heading() },
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 2.dp).clip(CircleShape).clickable(onClick = onToggleWeekMode),
            ) {
                Text(
                    text = stringResource(if (weekMode) R.string.calendar_view_week else R.string.calendar_view_month),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        IconButton(onClick = onNextMonth, modifier = Modifier.testTag(PlannerTestTags.NEXT_MONTH)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.calendar_next_month))
        }
    }
}

@Composable
private fun WeekGrid(
    selectedDate: LocalDate,
    today: LocalDate,
    datesWithItems: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val monday = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 7) {
            val date = monday.plusDays(i.toLong())
            DayCell(
                date = date,
                isSelected = date == selectedDate,
                isToday = date == today,
                hasItems = date in datesWithItems,
                onClick = { onSelectDate(date) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val locale = LocalLocale.current.platformLocale
    val days = remember {
        listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        )
    }
    Row(Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    datesWithItems: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val firstDay = yearMonth.atDay(1)
    // Monday = 0 .. Sunday = 6
    val leadingEmpty = firstDay.dayOfWeek.value - 1
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = ((leadingEmpty + daysInMonth + 6) / 7) * 7
    val rows = totalCells / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val cellIndex = r * 7 + c
                    val dayNumber = cellIndex - leadingEmpty + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = yearMonth.atDay(dayNumber)
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val hasItems = date in datesWithItems
                        DayCell(
                            date = date,
                            isSelected = isSelected,
                            isToday = isToday,
                            hasItems = hasItems,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasItems: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val bgColor = when {
        isSelected -> colors.primary
        isToday -> colors.primaryContainer.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> colors.onPrimary
        isToday -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val dotColor = if (isSelected) colors.onPrimary else colors.primary
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .then(
                if (isToday && !isSelected) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = date.toString()
                this.selected = isSelected
            }
            .testTag(PlannerTestTags.dayCell(date)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
            if (hasItems) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(5.dp)
                        .background(dotColor, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun TodoListContent(
    state: PlannerUiState,
    topPadding: androidx.compose.ui.unit.Dp,
    onFilterChange: (TaskFilter) -> Unit,
    onToggleTask: (TodoTask) -> Unit,
    onEditTask: (TodoTask) -> Unit,
    onDeleteTask: (TodoTask) -> Unit,
    onOpenNote: (NoteId) -> Unit,
) {
    val spacing = AppTheme.spacing
    LazyColumn(
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = topPadding + spacing.sm,
            bottom = 148.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "filter_row") {
            Row(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Triple(TaskFilter.PENDING, stringResource(R.string.todo_filter_pending, state.pendingTaskCount), PlannerTestTags.FILTER_PENDING),
                    Triple(TaskFilter.COMPLETED, stringResource(R.string.todo_filter_completed, state.completedTaskCount), PlannerTestTags.FILTER_COMPLETED),
                    Triple(TaskFilter.ALL, stringResource(R.string.todo_filter_all, state.allTasks.size), PlannerTestTags.FILTER_ALL),
                ).forEach { (filter, label, tag) ->
                    val selected = state.taskFilter == filter
                    Box(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                                RoundedCornerShape(50),
                            )
                            .border(
                                0.75.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(50),
                            )
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onFilterChange(filter) },
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag(tag),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (state.filteredTasks.isEmpty()) {
            item(key = "empty_tasks") {
                Box(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(top = 48.dp)) {
                    val (titleRes, bodyRes) = when (state.taskFilter) {
                        TaskFilter.PENDING -> R.string.todo_empty_pending_title to R.string.todo_empty_pending_body
                        TaskFilter.COMPLETED -> R.string.todo_empty_completed_title to R.string.todo_empty_completed_body
                        TaskFilter.ALL -> R.string.todo_empty_all_title to R.string.todo_empty_all_body
                    }
                    EmptyState(
                        title = stringResource(titleRes),
                        body = stringResource(bodyRes),
                        icon = Icons.Outlined.CheckCircle,
                    )
                }
            }
        } else {
            items(state.filteredTasks, key = { it.id.value }) { task ->
                Box(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                    TaskCard(
                        task = task,
                        onToggle = { onToggleTask(task) },
                        onEdit = { onEditTask(task) },
                        onDelete = { onDeleteTask(task) },
                        onOpenNote = task.sourceNoteId?.let { noteId -> { onOpenNote(noteId) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TodoTask,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenNote: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val taskMetas by NoteProStore.taskMetas.collectAsStateWithLifecycle()
    val proMeta = taskMetas[task.id.value] ?: TaskProMeta()
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    GlassSurface(
        shape = MaterialTheme.shapes.large,
        elevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onEdit)
            .testTag(PlannerTestTags.taskItem(task.id.value)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag(PlannerTestTags.taskCheck(task.id.value)),
            )
            Column(
                modifier = Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (proMeta.subtasks.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        proMeta.subtasks.forEach { st ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { NoteProStore.toggleSubtask(context, task.id.value, st.id) }
                                    .padding(vertical = 2.dp, horizontal = 4.dp),
                            ) {
                                Icon(
                                    imageVector = if (st.done) Icons.Outlined.CheckCircle else Icons.Outlined.Checklist,
                                    contentDescription = null,
                                    tint = if (st.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = st.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (st.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (st.done) TextDecoration.LineThrough else null,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (task.dueDate != null || task.reminderAt != null || task.sourceNoteId != null || proMeta.priority != TaskPriority.NONE || proMeta.recurrence != TaskRecurrence.NONE) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (proMeta.priority != TaskPriority.NONE) {
                            val (pLabel, pColor) = when (proMeta.priority) {
                                TaskPriority.HIGH -> stringResource(R.string.todo_priority_high) to Color(0xFFD93025)
                                TaskPriority.MEDIUM -> stringResource(R.string.todo_priority_medium) to Color(0xFFE8710A)
                                TaskPriority.LOW -> stringResource(R.string.todo_priority_low) to Color(0xFF1A73E8)
                                TaskPriority.NONE -> "" to Color.Transparent
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = pColor.copy(alpha = 0.15f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Flag, null, Modifier.size(12.dp), tint = pColor)
                                    Text(
                                        text = pLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = pColor,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        }
                        if (proMeta.recurrence != TaskRecurrence.NONE) {
                            val rLabel = when (proMeta.recurrence) {
                                TaskRecurrence.DAILY -> stringResource(R.string.todo_recurrence_daily)
                                TaskRecurrence.WEEKLY -> stringResource(R.string.todo_recurrence_weekly)
                                TaskRecurrence.MONTHLY -> stringResource(R.string.todo_recurrence_monthly)
                                TaskRecurrence.NONE -> ""
                            }
                            SmallBadge(icon = Icons.Outlined.Repeat, text = rLabel)
                        }
                        task.dueDate?.let { date ->
                            val timeStr = task.dueTime?.format(timeFormatter)?.let { " · $it" }.orEmpty()
                            SmallBadge(
                                icon = if (task.reminderAt != null) Icons.Outlined.Alarm else Icons.Outlined.CalendarMonth,
                                text = date.format(dateFormatter) + timeStr,
                            )
                        }
                        if (task.sourceNoteId != null && onOpenNote != null) {
                            Surface(
                                onClick = onOpenNote,
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Description, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        text = task.sourceNoteTitle?.ifBlank { stringResource(R.string.untitled) } ?: stringResource(R.string.todo_linked_note),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalendarEventCard(
    event: CalendarEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenSourceNote: (() -> Unit)? = null,
) {
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val start = event.startTime
    val end = event.endTime
    val timeText = when {
        start != null && end != null ->
            "${start.format(timeFormatter)} – ${end.format(timeFormatter)}"
        start != null -> start.format(timeFormatter)
        else -> stringResource(R.string.calendar_all_day)
    }
    GlassSurface(
        shape = MaterialTheme.shapes.large,
        elevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onEdit)
            .testTag(PlannerTestTags.eventItem(event.id.value)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (event.notes.isNotBlank()) {
                    Text(
                        text = event.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onOpenSourceNote != null) {
                IconButton(onClick = onOpenSourceNote) {
                    Icon(Icons.Outlined.Description, stringResource(R.string.todo_linked_note), tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoteReminderItemCard(note: NoteSummary, onClick: () -> Unit) {
    GlassSurface(
        shape = MaterialTheme.shapes.large,
        elevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Alarm, null, tint = MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f)) {
                Text(
                    text = note.title.ifBlank { note.preview.lineSequence().firstOrNull().orEmpty() }.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.calendar_note_reminder),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SmallBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorDialog(
    initial: TodoTask?,
    defaultDate: LocalDate?,
    notes: List<NoteSummary>,
    onSave: (
        title: String,
        description: String,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        enableReminder: Boolean,
        sourceNoteId: NoteId?,
        sourceNoteTitle: String?,
        proMeta: TaskProMeta?,
    ) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var dueDate by remember { mutableStateOf(initial?.dueDate ?: defaultDate) }
    var dueTime by remember { mutableStateOf(initial?.dueTime) }
    var enableReminder by rememberSaveable { mutableStateOf(initial?.reminderAt != null) }
    var linkedNoteId by rememberSaveable { mutableStateOf(initial?.sourceNoteId?.value) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showNotePicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val initialMeta = remember(initial?.id) {
        initial?.id?.value?.let { NoteProStore.getTaskMeta(context, it) } ?: TaskProMeta()
    }
    var priority by remember { mutableStateOf(initialMeta.priority) }
    var recurrence by remember { mutableStateOf(initialMeta.recurrence) }
    var subtasks by remember { mutableStateOf(initialMeta.subtasks) }
    var newSubtaskTitle by rememberSaveable { mutableStateOf("") }

    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val linkedNote = notes.firstOrNull { it.id.value == linkedNoteId }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PlannerTestTags.TASK_DIALOG),
        title = { Text(stringResource(if (initial == null) R.string.todo_new_task else R.string.todo_edit_task)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    label = { Text(stringResource(R.string.todo_task_title_label)) },
                    isError = titleError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag(PlannerTestTags.TASK_TITLE_FIELD),
                )
                if (titleError) {
                    Text(
                        stringResource(R.string.todo_error_empty_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.todo_task_desc_label)) },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag(PlannerTestTags.TASK_DESC_FIELD),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            dueDate?.format(dateFormatter) ?: stringResource(R.string.todo_pick_due_date),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (dueDate != null) {
                        OutlinedButton(onClick = { showTimePicker = true }) {
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(dueTime?.format(timeFormatter) ?: stringResource(R.string.todo_pick_time))
                        }
                    }
                }
                if (dueDate != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.todo_remind_me), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = enableReminder, onCheckedChange = { enableReminder = it })
                    }
                    TextButton(onClick = { dueDate = null; dueTime = null; enableReminder = false }) {
                        Text(stringResource(R.string.todo_clear_due_date))
                    }
                }
                // Priority selector
                Text(stringResource(R.string.todo_priority_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                        .padding(3.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf(
                        TaskPriority.NONE to R.string.todo_priority_none,
                        TaskPriority.LOW to R.string.todo_priority_low,
                        TaskPriority.MEDIUM to R.string.todo_priority_medium,
                        TaskPriority.HIGH to R.string.todo_priority_high,
                    ).forEach { (p, labelRes) ->
                        val selected = priority == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 32.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { priority = p })
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Recurrence selector
                Text(stringResource(R.string.todo_recurrence_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                        .padding(3.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf(
                        TaskRecurrence.NONE to R.string.todo_recurrence_none,
                        TaskRecurrence.DAILY to R.string.todo_recurrence_daily,
                        TaskRecurrence.WEEKLY to R.string.todo_recurrence_weekly,
                        TaskRecurrence.MONTHLY to R.string.todo_recurrence_monthly,
                    ).forEach { (r, labelRes) ->
                        val selected = recurrence == r
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 32.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { recurrence = r })
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Subtasks / Steps builder
                Text(stringResource(R.string.todo_subtasks_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                subtasks.forEach { st ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = st.done,
                            onCheckedChange = { checked ->
                                subtasks = subtasks.map { if (it.id == st.id) it.copy(done = checked) else it }
                            },
                        )
                        Text(st.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { subtasks = subtasks.filterNot { it.id == st.id } }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = newSubtaskTitle,
                        onValueChange = { newSubtaskTitle = it },
                        placeholder = { Text(stringResource(R.string.todo_subtask_add_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = newSubtaskTitle.isNotBlank(),
                        onClick = {
                            val clean = newSubtaskTitle.trim()
                            if (clean.isNotEmpty()) {
                                subtasks = subtasks + SubtaskItem(UUID.randomUUID().toString(), clean, false)
                                newSubtaskTitle = ""
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                }

                if (notes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showNotePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        val noteName = linkedNote?.title?.ifBlank { linkedNote.preview.lineSequence().firstOrNull().orEmpty() }
                            ?: initial?.sourceNoteTitle
                        Text(
                            noteName?.let { stringResource(R.string.todo_linked_to_note, it) }
                                ?: stringResource(R.string.todo_link_note),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                    } else {
                        val noteTitle = linkedNote?.title?.ifBlank { linkedNote.preview.lineSequence().firstOrNull().orEmpty() }
                            ?: initial?.sourceNoteTitle
                        val pendingSubtask = newSubtaskTitle.trim()
                        val finalSubtasks = if (pendingSubtask.isNotEmpty()) {
                            subtasks + SubtaskItem(UUID.randomUUID().toString(), pendingSubtask, false)
                        } else {
                            subtasks
                        }
                        onSave(
                            title,
                            description,
                            dueDate,
                            dueTime,
                            enableReminder,
                            linkedNoteId?.let(::NoteId),
                            noteTitle,
                            TaskProMeta(priority = priority, recurrence = recurrence, subtasks = finalSubtasks),
                        )
                    }
                },
                modifier = Modifier.testTag(PlannerTestTags.TASK_SAVE),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )

    if (showDatePicker) {
        val initialMillis = (dueDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { ms ->
                            dueDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val base = dueTime ?: LocalTime.of(9, 0)
        val timeState = rememberTimePickerState(initialHour = base.hour, initialMinute = base.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dueTime = LocalTime.of(timeState.hour, timeState.minute)
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }

    if (showNotePicker) {
        AlertDialog(
            onDismissRequest = { showNotePicker = false },
            title = { Text(stringResource(R.string.todo_link_note)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = { linkedNoteId = null; showNotePicker = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.todo_no_linked_note))
                    }
                    notes.take(30).forEach { n ->
                        val label = n.title.ifBlank { n.preview.lineSequence().firstOrNull().orEmpty() }.ifBlank { stringResource(R.string.untitled) }
                        TextButton(
                            onClick = { linkedNoteId = n.id.value; showNotePicker = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorDialog(
    initial: CalendarEvent?,
    defaultDate: LocalDate,
    notes: List<NoteSummary>,
    onSave: (
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        notes: String,
        alsoCreateTask: Boolean,
        sourceNoteId: NoteId?,
    ) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var eventNotes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    var startTime by remember { mutableStateOf(initial?.startTime) }
    var endTime by remember { mutableStateOf(initial?.endTime) }
    var alsoCreateTask by rememberSaveable { mutableStateOf(false) }
    var linkedNoteId by rememberSaveable { mutableStateOf(initial?.sourceNoteId?.value) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PlannerTestTags.EVENT_DIALOG),
        title = { Text(stringResource(if (initial == null) R.string.calendar_new_event else R.string.calendar_edit_event)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorRes = null },
                    label = { Text(stringResource(R.string.calendar_event_title_label)) },
                    isError = errorRes == R.string.calendar_error_empty_title,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag(PlannerTestTags.EVENT_TITLE_FIELD),
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(date.format(dateFormatter))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { pickingStart = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(startTime?.format(timeFormatter) ?: stringResource(R.string.calendar_start_time))
                    }
                    OutlinedButton(
                        onClick = { pickingEnd = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(endTime?.format(timeFormatter) ?: stringResource(R.string.calendar_end_time))
                    }
                }
                if (startTime != null || endTime != null) {
                    TextButton(onClick = { startTime = null; endTime = null; errorRes = null }) {
                        Text(stringResource(R.string.calendar_all_day))
                    }
                }
                errorRes?.let { res ->
                    Text(
                        stringResource(res),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = eventNotes,
                    onValueChange = { eventNotes = it },
                    label = { Text(stringResource(R.string.calendar_event_notes_label)) },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag(PlannerTestTags.EVENT_NOTES_FIELD),
                )
                if (initial == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.calendar_also_add_task), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = alsoCreateTask, onCheckedChange = { alsoCreateTask = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        title.isBlank() -> errorRes = R.string.calendar_error_empty_title
                        startTime != null && endTime != null && endTime!!.isBefore(startTime) ->
                            errorRes = R.string.calendar_error_time_order
                        else -> onSave(title, date, startTime, endTime, eventNotes, alsoCreateTask, linkedNoteId?.let(::NoteId))
                    }
                },
                modifier = Modifier.testTag(PlannerTestTags.EVENT_SAVE),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )

    if (showDatePicker) {
        val initialMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { ms ->
                            date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickingStart || pickingEnd) {
        val base = if (pickingStart) (startTime ?: LocalTime.of(9, 0)) else (endTime ?: startTime?.plusHours(1) ?: LocalTime.of(10, 0))
        val timeState = rememberTimePickerState(initialHour = base.hour, initialMinute = base.minute)
        AlertDialog(
            onDismissRequest = { pickingStart = false; pickingEnd = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = LocalTime.of(timeState.hour, timeState.minute)
                        if (pickingStart) {
                            startTime = picked
                            if (endTime != null && endTime!!.isBefore(picked)) endTime = picked.plusHours(1)
                        } else {
                            endTime = picked
                        }
                        pickingStart = false
                        pickingEnd = false
                        errorRes = null
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = false; pickingEnd = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

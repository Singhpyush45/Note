package com.appname.notes.feature.calendar

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.appname.notes.feature.pro.NoteProStore
import com.appname.notes.feature.pro.TaskProMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appname.notes.R
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.AppClock
import com.appname.notes.core.domain.model.CalendarEvent
import com.appname.notes.core.domain.model.CalendarEventId
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.ReminderRepeat
import com.appname.notes.core.domain.model.TaskFilter
import com.appname.notes.core.domain.model.TaskId
import com.appname.notes.core.domain.model.TodoTask
import com.appname.notes.core.domain.repository.NoteRepository
import com.appname.notes.core.domain.repository.PlannerRepository
import com.appname.notes.core.domain.repository.ReminderRepository
import com.appname.notes.ui.common.UserMessage
import com.appname.notes.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlannerSection {
    CALENDAR,
    TASKS,
}

data class PlannerUiState(
    val section: PlannerSection = PlannerSection.CALENDAR,
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val taskFilter: TaskFilter = TaskFilter.PENDING,
    val allTasks: List<TodoTask> = emptyList(),
    val filteredTasks: List<TodoTask> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val notes: List<NoteSummary> = emptyList(),
    val message: UserMessage? = null,
) {
    /** Tasks due on [selectedDate]. */
    val tasksForSelectedDate: List<TodoTask>
        get() = allTasks.filter { it.dueDate == selectedDate }

    /** Calendar events scheduled on [selectedDate], ordered by start time. */
    val eventsForSelectedDate: List<CalendarEvent>
        get() = events.filter { it.date == selectedDate }

    /** Notes with a pending reminder on [selectedDate]. */
    fun notesWithReminderOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<NoteSummary> =
        notes.filter { n ->
            n.nextReminderAt?.let { LocalDateTime.ofInstant(it, zone).toLocalDate() == date } == true
        }

    /** Dates in [currentMonth] that have tasks, events, or note reminders. */
    fun datesWithItems(zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> {
        val result = HashSet<LocalDate>()
        allTasks.forEach { t -> t.dueDate?.takeIf { YearMonth.from(it) == currentMonth }?.let(result::add) }
        events.forEach { e -> if (YearMonth.from(e.date) == currentMonth) result.add(e.date) }
        notes.forEach { n ->
            n.nextReminderAt?.let { LocalDateTime.ofInstant(it, zone).toLocalDate() }
                ?.takeIf { YearMonth.from(it) == currentMonth }
                ?.let(result::add)
        }
        return result
    }

    val pendingTaskCount: Int get() = allTasks.count { !it.isCompleted }
    val completedTaskCount: Int get() = allTasks.count { it.isCompleted }
}

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val planner: PlannerRepository,
    private val notesRepository: NoteRepository,
    private val reminders: ReminderRepository,
    private val clock: AppClock,
    private val savedState: SavedStateHandle,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val initialToday: LocalDate get() = LocalDateTime.ofInstant(clock.now(), zone).toLocalDate()

    private val section = MutableStateFlow(
        savedState.get<String>(KEY_SECTION)?.let { name -> PlannerSection.entries.firstOrNull { it.name == name } }
            ?: PlannerSection.CALENDAR,
    )
    private val currentMonth = MutableStateFlow(
        savedState.get<String>(KEY_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?: YearMonth.from(initialToday),
    )
    private val selectedDate = MutableStateFlow(
        savedState.get<String>(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: initialToday,
    )
    private val taskFilter = MutableStateFlow(
        savedState.get<String>(KEY_FILTER)?.let { name -> TaskFilter.entries.firstOrNull { it.name == name } }
            ?: TaskFilter.PENDING,
    )
    private val message = MutableStateFlow<UserMessage?>(null)

    private val navState = combine(section, currentMonth, selectedDate, taskFilter) { s, m, d, f ->
        NavSelection(s, m, d, f)
    }

    val uiState: StateFlow<PlannerUiState> = combine(
        navState,
        planner.observeTasks(TaskFilter.ALL),
        planner.observeEvents(),
        notesRepository.observeNotes(NoteCollection.All, NoteSort.MODIFIED),
        message,
    ) { nav, tasks, events, noteList, msg ->
        val filtered = when (nav.filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.PENDING -> tasks.filter { !it.isCompleted }
            TaskFilter.COMPLETED -> tasks.filter { it.isCompleted }
        }
        PlannerUiState(
            section = nav.section,
            currentMonth = nav.month,
            selectedDate = nav.date,
            today = LocalDateTime.ofInstant(clock.now(), zone).toLocalDate(),
            taskFilter = nav.filter,
            allTasks = tasks,
            filteredTasks = filtered,
            events = events,
            notes = noteList,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerUiState(today = initialToday, currentMonth = YearMonth.from(initialToday), selectedDate = initialToday))

    fun setSection(value: PlannerSection) {
        section.value = value
        savedState[KEY_SECTION] = value.name
    }

    fun setTaskFilter(filter: TaskFilter) {
        taskFilter.value = filter
        savedState[KEY_FILTER] = filter.name
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        savedState[KEY_DATE] = date.toString()
        val ym = YearMonth.from(date)
        if (ym != currentMonth.value) {
            currentMonth.value = ym
            savedState[KEY_MONTH] = ym.toString()
        }
    }

    fun previousMonth() {
        val next = currentMonth.value.minusMonths(1)
        currentMonth.value = next
        savedState[KEY_MONTH] = next.toString()
    }

    fun nextMonth() {
        val next = currentMonth.value.plusMonths(1)
        currentMonth.value = next
        savedState[KEY_MONTH] = next.toString()
    }

    fun goToToday() {
        val today = LocalDateTime.ofInstant(clock.now(), zone).toLocalDate()
        selectDate(today)
    }

    fun saveTask(
        editingId: TaskId?,
        title: String,
        description: String,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        enableReminder: Boolean,
        sourceNoteId: NoteId?,
        sourceNoteTitle: String?,
        proMeta: TaskProMeta? = null,
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            message.value = UserMessage(R.string.todo_error_empty_title)
            return
        }
        val reminderAt: Instant? = if (enableReminder && dueDate != null) {
            val time = dueTime ?: LocalTime.of(9, 0)
            LocalDateTime.of(dueDate, time).atZone(zone).toInstant()
        } else {
            null
        }
        viewModelScope.launch {
            val result = if (editingId == null) {
                planner.createTask(cleanTitle, description, dueDate, dueTime, reminderAt, sourceNoteId, sourceNoteTitle)
            } else {
                planner.updateTask(editingId, cleanTitle, description, dueDate, dueTime, reminderAt, sourceNoteId, sourceNoteTitle)
            }
            when (result) {
                is AppResult.Success -> {
                    val savedId = (result.value as? TaskId)?.value ?: editingId?.value
                    if (savedId != null && proMeta != null) {
                        NoteProStore.saveTaskMeta(appContext, savedId, proMeta)
                    }
                    // If the task is linked to a note and has a reminder time in the future, sync a note reminder too.
                    if (sourceNoteId != null && enableReminder && dueDate != null) {
                        val at = LocalDateTime.of(dueDate, dueTime ?: LocalTime.of(9, 0))
                        if (at.atZone(zone).toInstant().isAfter(clock.now())) {
                            reminders.add(sourceNoteId, null, at, ReminderRepeat.NONE, zone)
                        }
                    }
                    message.value = UserMessage(if (editingId == null) R.string.todo_msg_created else R.string.todo_msg_updated)
                }
                is AppResult.Failure -> message.value = result.error.toUserMessage()
            }
        }
    }

    fun toggleTaskCompleted(task: TodoTask) {
        viewModelScope.launch {
            val r = planner.setTaskCompleted(task.id, !task.isCompleted)
            if (r is AppResult.Failure) message.value = r.error.toUserMessage()
        }
    }

    fun deleteTask(id: TaskId) {
        viewModelScope.launch {
            when (val r = planner.deleteTask(id)) {
                is AppResult.Success -> message.value = UserMessage(R.string.todo_msg_deleted)
                is AppResult.Failure -> message.value = r.error.toUserMessage()
            }
        }
    }

    fun saveEvent(
        editingId: CalendarEventId?,
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        notes: String,
        alsoCreateTask: Boolean = false,
        sourceNoteId: NoteId? = null,
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            message.value = UserMessage(R.string.calendar_error_empty_title)
            return
        }
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            message.value = UserMessage(R.string.calendar_error_time_order)
            return
        }
        viewModelScope.launch {
            var linkedTaskId: TaskId? = null
            if (editingId == null && alsoCreateTask) {
                val createdTask = planner.createTask(
                    title = cleanTitle,
                    description = notes,
                    dueDate = date,
                    dueTime = startTime,
                    sourceNoteId = sourceNoteId,
                )
                if (createdTask is AppResult.Success) linkedTaskId = createdTask.value.id
            }
            val result = if (editingId == null) {
                planner.createEvent(cleanTitle, date, startTime, endTime, notes, linkedTaskId, sourceNoteId)
            } else {
                val current = planner.getEvent(editingId)
                planner.updateEvent(editingId, cleanTitle, date, startTime, endTime, notes, current?.linkedTaskId, current?.sourceNoteId ?: sourceNoteId)
            }
            when (result) {
                is AppResult.Success -> message.value = UserMessage(if (editingId == null) R.string.calendar_msg_created else R.string.calendar_msg_updated)
                is AppResult.Failure -> message.value = result.error.toUserMessage()
            }
        }
    }

    fun deleteEvent(id: CalendarEventId) {
        viewModelScope.launch {
            when (val r = planner.deleteEvent(id)) {
                is AppResult.Success -> message.value = UserMessage(R.string.calendar_msg_deleted)
                is AppResult.Failure -> message.value = r.error.toUserMessage()
            }
        }
    }

    fun onMessageShown() {
        message.value = null
    }

    private data class NavSelection(
        val section: PlannerSection,
        val month: YearMonth,
        val date: LocalDate,
        val filter: TaskFilter,
    )

    companion object {
        private const val KEY_SECTION = "planner_section"
        private const val KEY_MONTH = "planner_month"
        private const val KEY_DATE = "planner_date"
        private const val KEY_FILTER = "planner_filter"
    }
}

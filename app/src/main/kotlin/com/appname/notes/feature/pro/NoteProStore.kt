package com.appname.notes.feature.pro

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class TaskPriority(val level: Int) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
}

enum class TaskRecurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
}

data class SubtaskItem(
    val id: String,
    val title: String,
    val done: Boolean = false,
)

data class TaskProMeta(
    val priority: TaskPriority = TaskPriority.NONE,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val subtasks: List<SubtaskItem> = emptyList(),
)

enum class FocusPreset(val label: String, val minutes: Int) {
    POMODORO("Focus 25m", 25),
    DEEP_WORK("Deep 45m", 45),
    SHORT_BREAK("Break 5m", 5),
    LONG_BREAK("Break 15m", 15),
}

data class FocusTimerState(
    val preset: FocusPreset = FocusPreset.POMODORO,
    val remainingSeconds: Int = FocusPreset.POMODORO.minutes * 60,
    val totalSeconds: Int = FocusPreset.POMODORO.minutes * 60,
    val running: Boolean = false,
    val completedSessions: Int = 0,
) {
    val formattedTime: String
        get() {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            return "%02d:%02d".format(mins, secs)
        }

    val progress: Float
        get() = if (totalSeconds > 0) (totalSeconds - remainingSeconds).toFloat() / totalSeconds else 0f
}

/**
 * Persists extended Pro features (Per-Note Lock, Page Bookmarks, Locked Canvas Objects,
 * Task Priority/Subtasks/Recurrence, and Focus/Pomodoro Study Timer) without altering
 * the Room v10 SQLite schema, guaranteeing 100% backward compatibility and smooth APK updates.
 */
object NoteProStore {
    private const val PREFS = "note_pro_store_v1"
    private const val KEY_LOCKED_NOTES = "locked_notes"
    private const val KEY_FALLBACK_PIN = "fallback_pin"
    private const val PREFIX_BOOKMARKS = "bookmarks_"
    private const val PREFIX_LOCKED_ITEMS = "locked_items_"
    private const val PREFIX_TASK_META = "task_meta_"

    private var initialized = false
    private val _lockedNotes = MutableStateFlow<Set<String>>(emptySet())
    val lockedNotes: StateFlow<Set<String>> = _lockedNotes.asStateFlow()

    private val _taskMetas = MutableStateFlow<Map<String, TaskProMeta>>(emptyMap())
    val taskMetas: StateFlow<Map<String, TaskProMeta>> = _taskMetas.asStateFlow()

    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null
    private val _timer = MutableStateFlow(FocusTimerState())
    val timer: StateFlow<FocusTimerState> = _timer.asStateFlow()

    fun ensureInit(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _lockedNotes.value = prefs.getStringSet(KEY_LOCKED_NOTES, emptySet())?.toSet().orEmpty()
        val loadedMetas = HashMap<String, TaskProMeta>()
        for ((k, v) in prefs.all) {
            if (k.startsWith(PREFIX_TASK_META) && v is String) {
                val taskId = k.removePrefix(PREFIX_TASK_META)
                decodeTaskMeta(v)?.let { loadedMetas[taskId] = it }
            }
        }
        _taskMetas.value = loadedMetas
    }

    // ---------------------------------------------------------------- Per-Note Lock

    fun isNoteLocked(context: Context, noteId: String): Boolean {
        ensureInit(context)
        return noteId in _lockedNotes.value
    }

    fun setNoteLocked(context: Context, noteId: String, locked: Boolean) {
        ensureInit(context)
        val next = if (locked) _lockedNotes.value + noteId else _lockedNotes.value - noteId
        _lockedNotes.value = next
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOCKED_NOTES, next)
            .apply()
    }

    fun getFallbackPin(context: Context): String? {
        ensureInit(context)
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FALLBACK_PIN, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setFallbackPin(context: Context, pin: String) {
        ensureInit(context)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FALLBACK_PIN, pin.trim())
            .apply()
    }

    // ---------------------------------------------------------------- Page Bookmarks

    fun getBookmarkedPages(context: Context, noteId: String): Set<String> {
        ensureInit(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(PREFIX_BOOKMARKS + noteId, emptySet())?.toSet().orEmpty()
    }

    fun togglePageBookmark(context: Context, noteId: String, pageId: String): Set<String> {
        val current = getBookmarkedPages(context, noteId)
        val next = if (pageId in current) current - pageId else current + pageId
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PREFIX_BOOKMARKS + noteId, next)
            .apply()
        return next
    }

    // ---------------------------------------------------------------- Locked Canvas Objects

    fun getLockedItems(context: Context, noteId: String): Set<String> {
        ensureInit(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(PREFIX_LOCKED_ITEMS + noteId, emptySet())?.toSet().orEmpty()
    }

    fun setLockedItems(context: Context, noteId: String, ids: Set<String>) {
        ensureInit(context)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PREFIX_LOCKED_ITEMS + noteId, ids)
            .apply()
    }

    // ---------------------------------------------------------------- Task Priority, Subtasks & Recurrence

    fun getTaskMeta(context: Context, taskId: String): TaskProMeta {
        ensureInit(context)
        return _taskMetas.value[taskId] ?: TaskProMeta()
    }

    fun saveTaskMeta(context: Context, taskId: String, meta: TaskProMeta) {
        ensureInit(context)
        _taskMetas.update { it + (taskId to meta) }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFIX_TASK_META + taskId, encodeTaskMeta(meta))
            .apply()
    }

    fun toggleSubtask(context: Context, taskId: String, subtaskId: String) {
        val current = getTaskMeta(context, taskId)
        val updated = current.copy(
            subtasks = current.subtasks.map { if (it.id == subtaskId) it.copy(done = !it.done) else it },
        )
        saveTaskMeta(context, taskId, updated)
    }

    private fun encodeTaskMeta(meta: TaskProMeta): String = JSONObject().apply {
        put("priority", meta.priority.name)
        put("recurrence", meta.recurrence.name)
        val arr = JSONArray()
        meta.subtasks.forEach { st ->
            arr.put(
                JSONObject().apply {
                    put("id", st.id)
                    put("title", st.title)
                    put("done", st.done)
                },
            )
        }
        put("subtasks", arr)
    }.toString()

    private fun decodeTaskMeta(json: String): TaskProMeta? = runCatching {
        val obj = JSONObject(json)
        val priority = TaskPriority.entries.firstOrNull { it.name == obj.optString("priority") } ?: TaskPriority.NONE
        val recurrence = TaskRecurrence.entries.firstOrNull { it.name == obj.optString("recurrence") } ?: TaskRecurrence.NONE
        val arr = obj.optJSONArray("subtasks")
        val subtasks = ArrayList<SubtaskItem>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                if (title.isNotEmpty()) {
                    subtasks.add(SubtaskItem(item.optString("id", "$i"), title, item.optBoolean("done", false)))
                }
            }
        }
        TaskProMeta(priority, recurrence, subtasks)
    }.getOrNull()

    // ---------------------------------------------------------------- Focus / Pomodoro Study Timer

    fun selectPreset(preset: FocusPreset) {
        timerJob?.cancel()
        val secs = preset.minutes * 60
        _timer.update {
            it.copy(
                preset = preset,
                remainingSeconds = secs,
                totalSeconds = secs,
                running = false,
            )
        }
    }

    fun toggleTimer() {
        val current = _timer.value
        if (current.running) {
            timerJob?.cancel()
            _timer.update { it.copy(running = false) }
        } else {
            if (current.remainingSeconds <= 0) {
                val secs = current.preset.minutes * 60
                _timer.update { it.copy(remainingSeconds = secs, totalSeconds = secs) }
            }
            _timer.update { it.copy(running = true) }
            timerJob?.cancel()
            timerJob = timerScope.launch {
                while (_timer.value.running && _timer.value.remainingSeconds > 0) {
                    delay(1_000L)
                    _timer.update { st ->
                        if (!st.running) return@update st
                        val next = (st.remainingSeconds - 1).coerceAtLeast(0)
                        if (next == 0) {
                            st.copy(
                                remainingSeconds = 0,
                                running = false,
                                completedSessions = st.completedSessions + 1,
                            )
                        } else {
                            st.copy(remainingSeconds = next)
                        }
                    }
                }
            }
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        _timer.update {
            val secs = it.preset.minutes * 60
            it.copy(remainingSeconds = secs, totalSeconds = secs, running = false)
        }
    }
}

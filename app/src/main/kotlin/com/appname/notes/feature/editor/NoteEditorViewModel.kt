package com.appname.notes.feature.editor

import android.net.Uri
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appname.notes.R
import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.isBlank
import com.appname.notes.core.domain.editor.EditorState
import com.appname.notes.core.domain.markdown.ExportResult
import com.appname.notes.core.domain.markdown.Markdown
import com.appname.notes.core.domain.model.Note
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.model.Tag
import com.appname.notes.core.domain.model.TagId
import com.appname.notes.core.domain.repository.NoteRepository
import com.appname.notes.core.domain.repository.NotebookRepository
import com.appname.notes.core.domain.repository.TagRepository
import com.appname.notes.core.files.DocumentFiles
import com.appname.notes.di.ApplicationScope
import com.appname.notes.ui.common.UserMessage
import com.appname.notes.ui.common.toUserMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the save indicator shows (FR-002: save state visible when useful). */
enum class SaveStatus { SAVED, UNSAVED, SAVING, FAILED }

/** Why a note can't be edited right now. */
enum class ReadOnlyReason { NEWER_VERSION, IN_TRASH }

sealed interface EditorLoad {
    data object Loading : EditorLoad
    data object NotFound : EditorLoad
    data class Ready(val controller: EditorController) : EditorLoad
}

/** Note metadata shown around the editor; updates live when changed from anywhere. */
data class NoteMeta(
    val isPinned: Boolean,
    val isFavourite: Boolean,
    val isArchived: Boolean,
    val inTrash: Boolean,
    val notebookId: NotebookId?,
    val tags: List<Tag>,
)

/**
 * One open note. The editor's text is the source of truth while open; it is saved to the database
 * after a short pause in typing, when the app goes to the background, and when the editor closes.
 * Saves run in the application scope so they finish even if this ViewModel is destroyed (FR-002/FR-008).
 */
@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = NoteEditorViewModel.Factory::class)
class NoteEditorViewModel @AssistedInject constructor(
    // A plain String: Dagger can't use Kotlin value classes in assisted factories (name mangling).
    @Assisted noteIdValue: String,
    private val notes: NoteRepository,
    notebooks: NotebookRepository,
    private val tagRepository: TagRepository,
    private val ids: IdGenerator,
    private val files: DocumentFiles,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(noteId: String): NoteEditorViewModel
    }

    /** Declared first: other properties below use it while initialising. */
    val noteId = NoteId(noteIdValue)

    private val _load = MutableStateFlow<EditorLoad>(EditorLoad.Loading)
    val load: StateFlow<EditorLoad> = _load.asStateFlow()

    private val _readOnly = MutableStateFlow<ReadOnlyReason?>(null)
    val readOnly: StateFlow<ReadOnlyReason?> = _readOnly.asStateFlow()

    private val _saveStatus = MutableStateFlow(SaveStatus.SAVED)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    val meta: StateFlow<NoteMeta?> = notes.observeNote(noteId)
        .map { it?.toMeta() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val notebooks: StateFlow<List<Notebook>> =
        notebooks.observeNotebooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags: StateFlow<List<Tag>> =
        tagRepository.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class Snapshot(val title: String, val document: NoteDocument)

    private val saveMutex = Mutex()
    private var lastSaved: Snapshot? = null
    private var retryJob: Job? = null

    private val controller: EditorController? get() = (_load.value as? EditorLoad.Ready)?.controller

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val note = notes.getNote(noteId)
        if (note == null) {
            _load.value = EditorLoad.NotFound
            return
        }
        val editable = note.document.isFullyEditable
        val state = if (editable) {
            EditorState.fromDocument(note.document, ids::newId)
        } else {
            // Show the readable text; the stored document is never overwritten (saves are disabled).
            EditorState.fromDocument(note.document.copy(blocks = note.document.textBlocks), ids::newId)
        }
        val created = EditorController(state, note.title, ids::newId)
        lastSaved = Snapshot(note.title, created.state.toDocument())
        _readOnly.value = when {
            !editable -> ReadOnlyReason.NEWER_VERSION
            note.trashedAt != null -> ReadOnlyReason.IN_TRASH
            else -> null
        }
        _load.value = EditorLoad.Ready(created)

        // Autosave: mark unsaved immediately, save after a pause in typing.
        viewModelScope.launch {
            snapshotFlow { created.version }.drop(1).collect {
                if (_saveStatus.value != SaveStatus.SAVING) _saveStatus.value = SaveStatus.UNSAVED
            }
        }
        viewModelScope.launch {
            snapshotFlow { created.version }.drop(1).debounce(AUTOSAVE_DELAY_MS).collect { save() }
        }
    }

    private fun snapshot(): Snapshot? = controller?.let { Snapshot(it.title, it.state.toDocument()) }

    /** Saves if anything changed. Returns true when the stored note matches what is on screen. */
    suspend fun save(): Boolean {
        if (_readOnly.value != null) return true
        val snap = snapshot() ?: return true
        return saveSnapshot(snap)
    }

    private suspend fun saveSnapshot(snap: Snapshot): Boolean = saveMutex.withLock {
        if (snap == lastSaved) {
            if (snapshot() == snap) _saveStatus.value = SaveStatus.SAVED
            return@withLock true
        }
        _saveStatus.value = SaveStatus.SAVING
        // Run the write in the app scope: it completes even if the user leaves mid-save.
        val result = appScope.async { notes.saveContent(noteId, snap.title, snap.document) }.await()
        when (result) {
            is AppResult.Success -> {
                lastSaved = snap
                retryJob?.cancel()
                _saveStatus.value = if (snapshot() == snap) SaveStatus.SAVED else SaveStatus.UNSAVED
                true
            }
            is AppResult.Failure -> {
                _saveStatus.value = SaveStatus.FAILED
                scheduleRetry()
                false
            }
        }
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        retryJob = viewModelScope.launch {
            var wait = 1_000L
            while (_saveStatus.value == SaveStatus.FAILED) {
                delay(wait)
                save()
                wait = (wait * 2).coerceAtMost(30_000L)
            }
        }
    }

    /** App went to the background: save right away (process may be killed next). */
    fun flush() {
        if (_readOnly.value != null) return
        val snap = snapshot() ?: return
        appScope.launch { saveSnapshot(snap) }
    }

    override fun onCleared() {
        val snap = if (_readOnly.value == null) snapshot() else null
        appScope.launch {
            if (snap != null) saveSnapshot(snap)
            // A note the user opened but never typed into shouldn't clutter the list.
            if (snap == null || (snap.title.isBlank() && snap.document.isBlank())) notes.discardIfBlank(noteId)
        }
    }

    // ------------------------------------------------------------------ note actions

    fun setPinned(value: Boolean) = act { notes.setPinned(setOf(noteId), value) }
    fun setFavourite(value: Boolean) = act { notes.setFavourite(setOf(noteId), value) }
    fun setArchived(value: Boolean) = act(UserMessage(if (value) R.string.msg_archived else R.string.msg_unarchived)) {
        notes.setArchived(setOf(noteId), value)
    }

    fun moveToNotebook(id: NotebookId?) = act(UserMessage(R.string.msg_moved)) { notes.moveToNotebook(setOf(noteId), id) }

    suspend fun moveToTrash(): Boolean {
        save()
        return notes.moveToTrash(setOf(noteId)) is AppResult.Success
    }

    fun restore() = act(UserMessage(R.string.msg_restored)) {
        notes.restoreFromTrash(setOf(noteId)).also { if (it is AppResult.Success) _readOnly.value = null }
    }

    suspend fun deleteForever(): Boolean = notes.deletePermanently(setOf(noteId)) is AppResult.Success

    suspend fun duplicate(): NoteId? {
        save()
        return when (val r = notes.duplicate(noteId)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> { _message.value = r.error.toUserMessage(); null }
        }
    }

    fun setTag(tag: Tag, attached: Boolean) {
        val current = meta.value?.tags.orEmpty().map { it.id }.toSet()
        val updated: Set<TagId> = if (attached) current + tag.id else current - tag.id
        act { notes.setTags(noteId, updated) }
    }

    fun createAndAttachTag(name: String) {
        viewModelScope.launch {
            when (val created = tagRepository.createOrGet(name)) {
                is AppResult.Success -> setTag(created.value, true)
                is AppResult.Failure -> _message.value = created.error.toUserMessage()
            }
        }
    }

    fun markdownExport(): ExportResult? = snapshot()?.let { Markdown.export(it.title, it.document) }

    fun shareText(): String? = snapshot()?.let { Markdown.plainText(it.title, it.document) }

    fun suggestedFileName(): String =
        (controller?.title?.trim()?.takeIf { it.isNotEmpty() } ?: "note").replace(Regex("""[\\/:*?"<>|]"""), "_") + ".md"

    fun writeExport(uri: Uri, text: String) {
        viewModelScope.launch {
            _message.value = when (files.writeText(uri, text)) {
                is AppResult.Success -> UserMessage(R.string.msg_exported)
                is AppResult.Failure -> UserMessage(R.string.msg_export_failed)
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    private fun act(success: UserMessage? = null, block: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            when (val r = block()) {
                is AppResult.Success -> if (success != null) _message.value = success
                is AppResult.Failure -> _message.value = r.error.toUserMessage()
            }
        }
    }

    private fun Note.toMeta() = NoteMeta(
        isPinned = isPinned,
        isFavourite = isFavourite,
        isArchived = isArchived,
        inTrash = trashedAt != null,
        notebookId = notebookId,
        tags = tags,
    )

    companion object {
        const val AUTOSAVE_DELAY_MS = 700L
    }
}

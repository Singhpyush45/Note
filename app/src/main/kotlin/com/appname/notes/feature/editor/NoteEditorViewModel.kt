package com.appname.notes.feature.editor

import android.net.Uri
import java.io.File
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
import com.appname.notes.core.domain.markdown.LostFeature
import com.appname.notes.core.domain.markdown.Markdown
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AudioMarker
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.feature.reminders.NoteReminders
import com.appname.notes.core.settings.SettingsRepository
import com.appname.notes.feature.voice.LiveTranscriber
import com.appname.notes.feature.voice.VoiceFormat
import com.appname.notes.feature.voice.VoiceLanguage
import com.appname.notes.feature.voice.VoicePacks
import com.appname.notes.feature.voice.VoiceSource
import com.appname.notes.feature.audio.AudioPlayback
import com.appname.notes.feature.audio.RecordingController
import com.appname.notes.feature.audio.RecordingError
import com.appname.notes.feature.audio.RecordingState
import com.appname.notes.core.domain.repository.AttachmentRepository
import com.appname.notes.feature.attachments.AttachmentFileStore
import com.appname.notes.feature.attachments.AttachmentIntents
import com.appname.notes.feature.attachments.AttachmentPreviews
import com.appname.notes.feature.attachments.Attacher
import com.appname.notes.feature.attachments.StorageCleanup
import com.appname.notes.feature.pdf.PdfImport
import com.appname.notes.feature.pdf.PdfImporter
import com.appname.notes.feature.pdf.messageRes
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val attachmentRepository: AttachmentRepository,
    private val attacher: Attacher,
    private val pdfImporter: PdfImporter,
    private val attachmentStore: AttachmentFileStore,
    private val storageCleanup: StorageCleanup,
    val previews: AttachmentPreviews,
    val attachmentIntents: AttachmentIntents,
    private val recordings: RecordingController,
    reminderFactory: NoteReminders.Factory,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val settings: SettingsRepository,
    private val planner: com.appname.notes.core.domain.repository.PlannerRepository,
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

    /** Files attached to this note (FR-009), in order; updates live. */
    val attachments: StateFlow<List<Attachment>> =
        attachmentRepository.observe(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** This note's reminders (FR-010). Setting one on a checklist item saves the note first, so the task's text is stored. */
    val reminders = reminderFactory.create(noteId, viewModelScope, beforeSave = { if (it != null) flush() }) { _message.value = it }

    /** The recording in progress anywhere in the app (only one at a time), FR-012. */
    val recording: StateFlow<RecordingState> = recordings.state

    /** Plays this note's recordings; marker taps seek here. */
    val playback = AudioPlayback(viewModelScope, { attachmentStore.file(it.fileName) }) {
        _message.value = UserMessage(R.string.audio_cant_play)
    }

    /** How many files are being copied in right now (shows progress). */
    private val _attaching = MutableStateFlow(0)
    val attaching: StateFlow<Int> = _attaching.asStateFlow()

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
        cleared = true
        playback.release()
        if (voiceOwner) LiveTranscriber.stop(appContext)
        val snap = if (_readOnly.value == null) snapshot() else null
        appScope.launch {
            if (snap != null) saveSnapshot(snap)
            // A note the user opened but never typed into shouldn't clutter the list. Files still being
            // attached count as content: wait for them first, or the note would be discarded under them.
            if (snap == null || (snap.title.isBlank() && snap.document.isBlank())) {
                _attaching.first { it == 0 }
                // A recording into this note is content too (it is attached when it stops).
                val recordingHere = (recordings.state.value as? RecordingState.Recording)?.recording?.noteId == noteId
                if (!recordingHere) notes.discardIfBlank(noteId)
            }
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

    suspend fun deleteForever(): Boolean = (notes.deletePermanently(setOf(noteId)) is AppResult.Success).also { deleted ->
        if (deleted) appScope.launch { storageCleanup.run() }
    }

    // ------------------------------------------------------------------ attachments (FR-009)

    /**
     * Copies the picked files into app storage and attaches them. Runs in the app scope: a large file
     * still finishes attaching if the user leaves the note meanwhile.
     */
    fun attach(uris: List<Uri>) {
        if (uris.isEmpty() || _readOnly.value != null) return
        _attaching.update { it + uris.size }
        appScope.launch {
            val outcome = attacher.attach(noteId, uris)
            _attaching.update { it - uris.size }
            outcome.errorMessage?.let { _message.value = UserMessage(it) }
        }
    }

    /**
     * "Clip page" on a link (8c): the page's title and summary go under the line as a quote, its preview picture
     * becomes an attachment. Only on the user's tap; nothing is fetched otherwise.
     */
    fun clipLink(url: String) {
        if (_readOnly.value != null || controller == null) return
        viewModelScope.launch {
            _message.value = UserMessage(R.string.clip_working)
            val clip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { WebClipper.fetch(url) }.getOrNull() }
            if (clip == null) {
                _message.value = UserMessage(R.string.clip_failed)
                return@launch
            }
            val lines = buildList {
                add(
                    com.appname.notes.core.domain.document.BlockKind.QUOTE to com.appname.notes.core.domain.document.RichText(
                        clip.title,
                        listOf(com.appname.notes.core.domain.document.StyleSpan(0, clip.title.length, com.appname.notes.core.domain.document.SpanType.BOLD)),
                    ),
                )
                clip.description?.takeIf { it.isNotBlank() }?.let { add(com.appname.notes.core.domain.document.BlockKind.QUOTE to com.appname.notes.core.domain.document.RichText(it)) }
            }
            controller?.insertLines(lines)
            _message.value = UserMessage(R.string.clip_done)
            val image = clip.imageUrl ?: return@launch
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { WebClipper.download(image, File(appContext.cacheDir, "clips"), clip.title) }.getOrNull()
            } ?: return@launch
            val outcome = attacher.attach(noteId, listOf(Uri.fromFile(file)))
            file.delete()
            outcome.errorMessage?.let { _message.value = UserMessage(it) }
        }
    }

    /** Removes an attachment; the snackbar offers Undo (the file stays until cleanup, so undo is instant). */
    fun renameAttachment(attachment: Attachment, name: String) {
        if (_readOnly.value != null || name.isBlank() || name == attachment.displayName) return
        act { attachmentRepository.rename(attachment.id, name) }
    }

    fun removeAttachment(attachment: Attachment) {
        if (_readOnly.value != null) return
        viewModelScope.launch {
            when (val r = attachmentRepository.remove(attachment.id)) {
                is AppResult.Success -> _message.value = UserMessage(
                    R.string.attach_removed,
                    listOf(attachment.displayName),
                    undo = { viewModelScope.launch { attachmentRepository.restore(r.value) } },
                )
                is AppResult.Failure -> _message.value = r.error.toUserMessage()
            }
        }
    }

    /** "Annotate": an attached PDF becomes its own PDF note to write on (the attachment stays as it is). */
    suspend fun annotatePdf(attachment: Attachment): NoteId? {
        val notebook = meta.value?.notebookId
        return when (val r = pdfImporter.import(Uri.fromFile(attachmentStore.file(attachment.fileName)), notebook)) {
            is PdfImport.Created -> r.noteId
            is PdfImport.Failed -> { _message.value = UserMessage(r.error.messageRes); null }
        }
    }

    /** The camera app finished: attach the photo if one was taken, and remove the temporary file either way. */
    fun attachCameraPhoto(path: String, taken: Boolean) {
        val file = File(path)
        if (!taken || _readOnly.value != null) {
            file.delete()
            return
        }
        _attaching.update { it + 1 }
        appScope.launch {
            val outcome = attacher.attach(noteId, listOf(attachmentIntents.uriFor(file)))
            file.delete()
            _attaching.update { it - 1 }
            outcome.errorMessage?.let { _message.value = UserMessage(it) }
        }
    }

    /** A scan from the document scanner: attach the PDF, then remove the temporary copy. */
    fun attachScan(file: File) {
        if (_readOnly.value != null) {
            file.delete()
            return
        }
        _attaching.update { it + 1 }
        appScope.launch {
            val outcome = attacher.attach(noteId, listOf(attachmentIntents.uriFor(file)))
            file.delete()
            _attaching.update { it - 1 }
            outcome.errorMessage?.let { _message.value = UserMessage(it) }
        }
    }

    fun scannerUnavailable() {
        _message.value = UserMessage(R.string.scan_unavailable)
    }

    fun cameraUnavailable() {
        _message.value = UserMessage(R.string.attach_camera_unavailable)
    }

    // ------------------------------------------------------------------ audio (FR-012)

    /** The microphone permission was granted: start recording into this note. */
    fun startRecording() {
        if (_readOnly.value != null) return
        if (recordings.start(noteId)) viewModelScope.launch { watchRecordingErrors() }
    }

    private suspend fun watchRecordingErrors() {
        recordings.errors.first { it != null }?.let { error ->
            _message.value = UserMessage(
                when (error) {
                    RecordingError.ALREADY_RECORDING -> R.string.audio_already_recording
                    RecordingError.MICROPHONE_UNAVAILABLE -> R.string.audio_mic_unavailable
                    RecordingError.STORAGE -> R.string.audio_save_failed
                },
            )
            recordings.errorShown()
        }
    }

    fun stopRecording() = recordings.stop()

    fun microphoneDenied() {
        _message.value = UserMessage(R.string.audio_permission_denied)
    }

    /** Writes a timestamp of the running recording into the text at the cursor. */
    fun addMarker() {
        val active = (recordings.state.value as? RecordingState.Recording)?.recording ?: return
        if (active.noteId != noteId) return
        val marker = AudioMarker(active.attachmentId, recordings.elapsedMs())
        controller?.insertLink(marker.label, marker.url)
        _message.value = UserMessage(R.string.audio_marker_added, listOf(marker.label))
    }

    /** Plays the recording a marker belongs to, from the marker's time. */
    fun playMarker(marker: AudioMarker) {
        val a = attachments.value.firstOrNull { it.id == marker.attachmentId } ?: return
        playback.play(a, marker.positionMs)
    }

    /** Markers in the text for [attachment], in time order (shown as chips on its player). */
    fun markersFor(attachment: Attachment): List<AudioMarker> =
        controller?.links.orEmpty().mapNotNull { AudioMarker.parse(it) }.filter { it.attachmentId == attachment.id }
            .distinct().sortedBy { it.positionMs }

    // ------------------------------------------------------------------ live transcription (owner request)

    /** The voice-typing choices: language and sound source. */
    data class VoiceChoice(val language: VoiceLanguage, val source: VoiceSource)

    private val _voice = MutableStateFlow(VoiceChoice(VoiceLanguage.defaultFor(java.util.Locale.getDefault()), VoiceSource.MICROPHONE))
    val voice: StateFlow<VoiceChoice> = _voice.asStateFlow()

    /** The language was chosen before (the dialog then isn't needed for a downloaded pack). */
    private var voiceLanguageSaved = false

    val voiceStatus = LiveTranscriber.status
    val voiceLevel = LiveTranscriber.level
    val voiceDownloads = VoicePacks.progress

    /** Bumped when a voice pack is downloaded or removed, so the screen re-checks what is installed. */
    private val _packsChanged = MutableStateFlow(0)
    val packsChanged: StateFlow<Int> = _packsChanged.asStateFlow()

    /** This note started the transcriber (its words go into this note). */
    private var voiceOwner = false
    @Volatile private var cleared = false

    // Sentence tracking: words being heard are an open piece; a long enough pause before a new piece ends a sentence.
    private var voicePieceOpen = false
    private var voicePieceEnd: String? = null
    private var voicePartial = ""
    private var voiceLastFinalAt = 0L

    init {
        viewModelScope.launch {
            val saved = VoiceLanguage.fromTag(settings.voiceLanguage.first())
            if (saved != null) {
                voiceLanguageSaved = true
                _voice.value = _voice.value.copy(language = saved)
            }
        }
        viewModelScope.launch {
            LiveTranscriber.events.collect { e -> if (voiceOwner) onVoiceEvent(e) }
        }
        viewModelScope.launch {
            LiveTranscriber.problem.collect { p -> if (p != null && voiceOwner) onVoiceProblem(p) }
        }
        viewModelScope.launch {
            // Stopped without a last result (an error): keep what was heard so far.
            LiveTranscriber.status.collect { st ->
                if (st == LiveTranscriber.Status.IDLE && voiceOwner && voicePieceOpen) {
                    controller?.voiceCommit(voicePartial, voicePieceEnd)
                    voicePieceOpen = false
                }
            }
        }
    }

    private fun onVoiceEvent(e: LiveTranscriber.Event) {
        val c = controller ?: return
        if (_readOnly.value != null) return
        val language = _voice.value.language
        if (!voicePieceOpen) {
            if (e is LiveTranscriber.Event.Partial && e.text.isBlank()) return
            voicePieceOpen = true
            val at = when (e) {
                is LiveTranscriber.Event.Partial -> e.at
                is LiveTranscriber.Event.Final -> e.at
            }
            voicePieceEnd = if (voiceLastFinalAt != 0L && at - voiceLastFinalAt > VoiceFormat.PAUSE_NANOS) VoiceFormat.stop(language) else null
        }
        when (e) {
            is LiveTranscriber.Event.Partial -> {
                voicePartial = VoiceFormat.partial(e.text, language)
                c.voiceLive(voicePartial, voicePieceEnd)
            }
            is LiveTranscriber.Event.Final -> {
                val text = VoiceFormat.final(e.text, language, end = e.last)
                c.voiceCommit(text, voicePieceEnd)
                if (e.last && text.isBlank()) c.voiceFinish(VoiceFormat.stop(language))
                voicePieceOpen = false
                voicePartial = ""
                if (text.isNotBlank()) voiceLastFinalAt = e.at
                if (e.last) voiceLastFinalAt = 0L
            }
        }
    }

    private fun onVoiceProblem(p: LiveTranscriber.Problem) {
        _message.value = UserMessage(
            when (p) {
                LiveTranscriber.Problem.AUDIO -> R.string.voice_problem_audio
                LiveTranscriber.Problem.MODEL -> {
                    // A damaged download: remove it, so the next tap downloads it again.
                    VoicePacks.delete(appContext, _voice.value.language.pack)
                    _packsChanged.value++
                    R.string.voice_problem_model
                }
                LiveTranscriber.Problem.SILENT_PHONE_AUDIO -> R.string.voice_problem_silent
                LiveTranscriber.Problem.STOPPED -> R.string.voice_problem_stopped
            },
        )
    }

    fun packReady(language: VoiceLanguage) = VoicePacks.installed(appContext, language.pack)

    /** The mic is tapped and the language's pack is missing (or no language was ever chosen): ask first. */
    fun voiceNeedsChoice(): Boolean = !voiceLanguageSaved || !packReady(_voice.value.language)

    /** Starts listening (microphone permission already granted; [projection] = phone-audio permission). */
    fun startVoice(projection: Pair<Int, android.content.Intent>? = null) {
        val choice = _voice.value
        if (_readOnly.value != null || !packReady(choice.language)) return
        voiceOwner = true
        voicePieceOpen = false
        voiceLastFinalAt = 0L
        LiveTranscriber.start(appContext, choice.language.pack, if (projection != null) VoiceSource.PHONE_AUDIO else VoiceSource.MICROPHONE, projection)
    }

    fun stopVoice() {
        LiveTranscriber.stop(appContext)
    }

    /** A language picked (menu or first-use dialog); remembered for next time. Returns whether to restart. */
    fun setVoiceLanguage(language: VoiceLanguage): Boolean {
        voiceLanguageSaved = true
        viewModelScope.launch { settings.setVoiceLanguage(language.key) }
        if (_voice.value.language == language) return false
        val wasOn = LiveTranscriber.status.value != LiveTranscriber.Status.IDLE
        // A new language needs its own model: stop; the screen starts again with it.
        stopVoice()
        _voice.value = _voice.value.copy(language = language)
        return wasOn
    }

    /** A sound source picked; returns whether listening was on (the screen then starts again with it). */
    fun setVoiceSource(source: VoiceSource): Boolean {
        if (_voice.value.source == source) return false
        val wasOn = LiveTranscriber.status.value != LiveTranscriber.Status.IDLE
        stopVoice()
        _voice.value = _voice.value.copy(source = source)
        return wasOn
    }

    /**
     * One-time download of the offline model for [language] (runs on even if the note is closed); listening starts
     * by itself when it is done and the microphone may be used.
     */
    fun downloadVoicePack(language: VoiceLanguage) {
        if (voiceDownloads.value.containsKey(language.pack)) return
        appScope.launch {
            val ok = VoicePacks.download(appContext, language.pack)
            _packsChanged.value++
            if (!ok) {
                _message.value = UserMessage(R.string.voice_download_failed)
                return@launch
            }
            val choice = _voice.value
            val micAllowed = androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!cleared && choice.language.pack == language.pack && choice.source == VoiceSource.MICROPHONE && micAllowed &&
                LiveTranscriber.status.value == LiveTranscriber.Status.IDLE
            ) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { startVoice() }
            }
        }
    }

    fun voicePermissionDenied() {
        _message.value = UserMessage(R.string.voice_mic_denied)
    }

    fun voiceProjectionDenied() {
        _message.value = UserMessage(R.string.voice_phone_audio_denied)
    }

    // ------------------------------------------------------------------ reminders (FR-010)

    /** The checklist item under the cursor, for "Remind me about this task"; null elsewhere. */
    fun taskAtCursor(): String? {
        val state = controller?.state ?: return null
        val meta = state.paragraphs.getOrNull(state.lineOf(state.selection.min)) ?: return null
        return meta.id.takeIf { meta.kind == BlockKind.CHECKLIST }
    }

    /** The current text of the checklist item [blockId]; null if it's no longer in the note. */
    fun taskText(blockId: String): String? {
        val state = controller?.state ?: return null
        val line = state.paragraphs.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return null
        return state.lineText(line).trim().ifEmpty { null }
    }

    /**
     * Creates a To-do List task from the currently selected text, the current line under the cursor,
     * or the note's title, and links the new task to this note.
     */
    fun createTaskFromNote() {
        val c = controller ?: return
        val state = c.state
        val selectedText = if (!state.selection.collapsed) {
            state.text.substring(state.selection.min, state.selection.max)
                .lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
        } else {
            null
        }
        val cursorLineText = state.lineText(state.lineOf(state.selection.min)).trim().takeIf { it.isNotEmpty() }
        val noteTitle = c.title.trim()
        val taskTitle = selectedText
            ?: cursorLineText
            ?: noteTitle.ifEmpty { appContext.getString(R.string.note_untitled) }
        val taskDescription = if (noteTitle.isNotEmpty() && noteTitle != taskTitle) noteTitle else ""
        act(UserMessage(R.string.todo_msg_created)) {
            planner.createTask(
                title = taskTitle,
                description = taskDescription,
                dueDate = null,
                dueTime = null,
                reminderAt = null,
                sourceNoteId = noteId,
                sourceNoteTitle = noteTitle.ifEmpty { null },
            )
        }
    }

    fun noAppToOpen() {
        _message.value = UserMessage(R.string.attach_no_app)
    }

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

    fun markdownExport(): ExportResult? = snapshot()?.let {
        val export = Markdown.export(it.title, it.document)
        // Markdown text can't carry files: say so before exporting (FR-007).
        if (attachments.value.isNotEmpty()) export.copy(lost = export.lost + LostFeature.ATTACHMENTS) else export
    }

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

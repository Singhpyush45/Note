package com.appname.notes.feature.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.feature.attachments.AttachmentIntents
import com.appname.notes.feature.attachments.AttachmentPreviews
import com.appname.notes.feature.attachments.Attacher
import java.io.File
import com.appname.notes.feature.attachments.StorageCleanup
import com.appname.notes.feature.pdf.PdfImport
import com.appname.notes.feature.pdf.PdfImporter
import com.appname.notes.feature.pdf.PdfThumbnails
import com.appname.notes.feature.pdf.messageRes
import androidx.lifecycle.viewModelScope
import com.appname.notes.R
import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.markdown.Markdown
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.model.Tag
import com.appname.notes.core.domain.model.TagId
import com.appname.notes.core.domain.repository.InkRepository
import com.appname.notes.core.domain.repository.NoteRepository
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.ui.common.InkThumbnail
import com.appname.notes.core.domain.repository.NotebookRepository
import com.appname.notes.core.domain.repository.TagRepository
import com.appname.notes.core.files.DocumentFiles
import com.appname.notes.core.settings.SettingsRepository
import com.appname.notes.ui.common.UserMessage
import com.appname.notes.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val collection: NoteCollection = NoteCollection.All,
    /** Title of the current notebook/tag collection, or null for the built-in ones. */
    val collectionName: String? = null,
    val sort: NoteSort = NoteSort.MODIFIED,
    val pinned: List<NoteSummary> = emptyList(),
    val others: List<NoteSummary> = emptyList(),
    val notebooks: List<Notebook> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val loading: Boolean = true,
    val message: UserMessage? = null,
) {
    val isEmpty: Boolean get() = !loading && pinned.isEmpty() && others.isEmpty()
    val canCreate: Boolean get() = collection != NoteCollection.Archive && collection != NoteCollection.Trash
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val notes: NoteRepository,
    private val ink: InkRepository,
    notebooks: NotebookRepository,
    tags: TagRepository,
    private val settings: SettingsRepository,
    private val files: DocumentFiles,
    private val ids: IdGenerator,
    private val savedState: SavedStateHandle,
    private val pdfImporter: PdfImporter,
    private val pdfThumbnails: PdfThumbnails,
    private val storageCleanup: StorageCleanup,
    private val attachmentPreviews: AttachmentPreviews,
    private val attacher: Attacher,
    private val attachmentIntents: AttachmentIntents,
) : ViewModel() {

    // The chosen collection survives rotation and process death via SavedStateHandle.
    private val collection = MutableStateFlow(decode(savedState.get<String>(KEY_COLLECTION)))
    private val message = MutableStateFlow<UserMessage?>(null)

    private val noteList = combine(collection, settings.library) { c, lib -> c to lib.sort }
        .flatMapLatest { (c, sort) -> notes.observeNotes(c, sort).map { Triple(c, sort, it) } }

    val uiState: StateFlow<LibraryUiState> = combine(
        noteList,
        notebooks.observeNotebooks(),
        tags.observeTags(),
        message,
    ) { (c, sort, list), books, tagList, msg ->
        val showPinnedSection = c != NoteCollection.Trash
        LibraryUiState(
            collection = c,
            collectionName = when (c) {
                is NoteCollection.InNotebook -> books.firstOrNull { it.id == c.id }?.title
                is NoteCollection.WithTag -> tagList.firstOrNull { it.id == c.id }?.name
                else -> null
            },
            sort = sort,
            pinned = if (showPinnedSection) list.filter { it.isPinned } else emptyList(),
            others = if (showPinnedSection) list.filterNot { it.isPinned } else list,
            notebooks = books,
            tags = tagList,
            loading = false,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun selectCollection(value: NoteCollection) {
        collection.value = value
        savedState[KEY_COLLECTION] = encode(value)
    }

    /** If the shown notebook/tag gets deleted, fall back to All notes. */
    fun onCollectionMissing() = selectCollection(NoteCollection.All)

    fun setSort(sort: NoteSort) {
        viewModelScope.launch { settings.setLibrarySort(sort).reportFailure() }
    }

    /** Creates a note in the current notebook (if any) and returns its id, or null on failure. */
    /** New handwritten note in the shown notebook/tag, like [createNote]. */
    suspend fun createInkNote(): NoteId? {
        val notebook = (collection.value as? NoteCollection.InNotebook)?.id
        return when (val result = ink.createInkNote(notebook, PageTemplate.LINED)) {
            is AppResult.Success -> {
                (collection.value as? NoteCollection.WithTag)?.let { notes.setTags(result.value, setOf(it.id)) }
                result.value
            }
            is AppResult.Failure -> { message.value = result.error.toUserMessage(); null }
        }
    }

    /** Recently drawn thumbnails, keyed by note and its modified time, so scrolling doesn't reload them. */
    private val thumbnails = object : LinkedHashMap<String, InkThumbnail>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, InkThumbnail>?) = size > THUMBNAIL_CACHE
    }

    /** A scan from the document scanner becomes a new note in the shown notebook (FR-011). */
    suspend fun createNoteFromScan(file: File): NoteId? {
        val notebook = (collection.value as? NoteCollection.InNotebook)?.id
        val (id, outcome) = attacher.createNote(notebook, listOf(attachmentIntents.uriFor(file)))
        file.delete()
        outcome.errorMessage?.let { message.value = UserMessage(it) }
        if (id != null) (collection.value as? NoteCollection.WithTag)?.let { notes.setTags(id, setOf(it.id)) }
        return id
    }

    fun scannerUnavailable() {
        message.value = UserMessage(R.string.scan_unavailable)
    }

    /** Cover photo for a text note's card (its first attached image), decoded at card size and cached. */
    suspend fun cover(fileName: String): Bitmap? = attachmentPreviews.load(fileName, AttachmentKind.IMAGE, COVER_PX)

    suspend fun inkThumbnail(note: NoteSummary): InkThumbnail? {
        val key = "${note.id.value}@${note.updatedAt.toEpochMilli()}"
        synchronized(thumbnails) { thumbnails[key] }?.let { return it }
        val page = (ink.firstPage(note.id) as? AppResult.Success)?.value ?: return null
        val pdfPage = page.info.pdfPageIndex?.let { pdfThumbnails.render(note.id, it) }
        val thumb = InkThumbnail(page.info.width, page.info.height, page.info.template, page.items, pdfPage)
        synchronized(thumbnails) { thumbnails[key] = thumb }
        return thumb
    }

    suspend fun createNote(): NoteId? {
        val notebook = (collection.value as? NoteCollection.InNotebook)?.id
        return when (val result = notes.createNote(notebook)) {
            is AppResult.Success -> {
                (collection.value as? NoteCollection.WithTag)?.let { notes.setTags(result.value, setOf(it.id)) }
                result.value
            }
            is AppResult.Failure -> { message.value = result.error.toUserMessage(); null }
        }
    }

    // Block bodies with explicit Unit: some actions refer to each other (undo), which needs declared types.
    fun setPinned(id: NoteId, pinned: Boolean) {
        launchAction { notes.setPinned(setOf(id), pinned) }
    }

    fun setFavourite(id: NoteId, favourite: Boolean) {
        launchAction { notes.setFavourite(setOf(id), favourite) }
    }

    fun setArchived(id: NoteId, archived: Boolean) {
        launchAction(UserMessage(if (archived) R.string.msg_archived else R.string.msg_unarchived, undo = { setArchived(id, !archived) })) {
            notes.setArchived(setOf(id), archived)
        }
    }

    fun moveToTrash(id: NoteId) {
        launchAction(UserMessage(R.string.msg_trashed, undo = { restore(id) })) { notes.moveToTrash(setOf(id)) }
    }

    fun restore(id: NoteId) {
        launchAction(UserMessage(R.string.msg_restored)) { notes.restoreFromTrash(setOf(id)) }
    }

    fun deleteForever(id: NoteId) {
        launchAction(UserMessage(R.string.msg_deleted)) { notes.deletePermanently(setOf(id)).also { cleanUpPdfFiles() } }
    }

    fun emptyTrash() {
        launchAction(UserMessage(R.string.msg_trash_emptied)) { notes.emptyTrash().also { cleanUpPdfFiles() } }
    }

    /** Deleted notes leave their PDF/attachment files behind (copies may share them); remove what nothing uses. */
    private suspend fun cleanUpPdfFiles() {
        storageCleanup.run()
    }

    private val _importingPdf = MutableStateFlow(false)
    val importingPdf: StateFlow<Boolean> = _importingPdf

    /** Imports a PDF as a new note in the shown notebook (SRS 7.1); returns its id, or null with a message. */
    suspend fun importPdf(uri: Uri): NoteId? {
        if (_importingPdf.value) return null
        _importingPdf.value = true
        try {
            val notebook = (collection.value as? NoteCollection.InNotebook)?.id
            return when (val r = pdfImporter.import(uri, notebook)) {
                is PdfImport.Created -> {
                    (collection.value as? NoteCollection.WithTag)?.let { notes.setTags(r.noteId, setOf(it.id)) }
                    r.noteId
                }
                is PdfImport.Failed -> { message.value = UserMessage(r.error.messageRes); null }
            }
        } finally {
            _importingPdf.value = false
        }
    }

    fun duplicate(id: NoteId) {
        launchAction(UserMessage(R.string.msg_duplicated)) { notes.duplicate(id) }
    }

    fun moveToNotebook(id: NoteId, notebook: NotebookId?) {
        launchAction(UserMessage(R.string.msg_moved)) { notes.moveToNotebook(setOf(id), notebook) }
    }

    /** Imports a Markdown/text file as a new note; returns the new note's id. */
    suspend fun import(uri: Uri): NoteId? {
        val picked = when (val read = files.readText(uri)) {
            is AppResult.Success -> read.value
            is AppResult.Failure -> { message.value = UserMessage(R.string.msg_import_failed); return null }
        }
        val parsed = Markdown.import(picked.text, picked.displayName, ids::newId)
        val notebook = (collection.value as? NoteCollection.InNotebook)?.id
        return when (val created = notes.createNote(parsed.title, parsed.document, notebook)) {
            is AppResult.Success -> {
                message.value = UserMessage(R.string.msg_imported, listOf(parsed.title.ifBlank { picked.displayName }))
                created.value
            }
            is AppResult.Failure -> { message.value = created.error.toUserMessage(); null }
        }
    }

    fun messageShown() {
        message.value = null
    }

    private fun launchAction(success: UserMessage? = null, action: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> if (success != null) message.value = success
                is AppResult.Failure -> message.value = result.error.toUserMessage()
            }
        }
    }

    private fun AppResult<*>.reportFailure() {
        if (this is AppResult.Failure) message.value = error.toUserMessage()
    }

    companion object {
        private const val KEY_COLLECTION = "collection"
        private const val THUMBNAIL_CACHE = 60
        private const val COVER_PX = 480

        fun encode(c: NoteCollection): String = when (c) {
            NoteCollection.All -> "all"
            NoteCollection.Favourites -> "favourites"
            NoteCollection.Archive -> "archive"
            NoteCollection.Trash -> "trash"
            is NoteCollection.InNotebook -> "notebook:${c.id.value}"
            is NoteCollection.WithTag -> "tag:${c.id.value}"
        }

        fun decode(s: String?): NoteCollection = when {
            s == null -> NoteCollection.All
            s == "favourites" -> NoteCollection.Favourites
            s == "archive" -> NoteCollection.Archive
            s == "trash" -> NoteCollection.Trash
            s.startsWith("notebook:") -> NoteCollection.InNotebook(NotebookId(s.removePrefix("notebook:")))
            s.startsWith("tag:") -> NoteCollection.WithTag(TagId(s.removePrefix("tag:")))
            else -> NoteCollection.All
        }
    }
}

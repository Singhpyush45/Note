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
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.model.FolderTree
import com.appname.notes.core.domain.model.NotebookDeleteMode
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val collection: NoteCollection = NoteCollection.All,
    /** Title of the current notebook/tag collection, or null for the built-in ones. */
    val collectionName: String? = null,
    val sort: NoteSort = NoteSort.MODIFIED,
    val cardSize: com.appname.notes.core.settings.CardSize = com.appname.notes.core.settings.CardSize.BIG,
    val pinned: List<NoteSummary> = emptyList(),
    val others: List<NoteSummary> = emptyList(),
    val notebooks: List<Notebook> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val loading: Boolean = true,
    val message: UserMessage? = null,
) {
    /** The folder being shown, if any. */
    val currentFolder: NotebookId? get() = (collection as? NoteCollection.InNotebook)?.id

    /** Folders inside the shown folder (shown above its notes). */
    val subfolders: List<Notebook> get() = currentFolder?.let { FolderTree.children(notebooks, it) }.orEmpty()

    /** The folder the shown folder is inside (for "up"), if any. */
    val parentFolder: Notebook?
        get() = currentFolder?.let { id -> notebooks.firstOrNull { it.id == id }?.parentId }?.let { p -> notebooks.firstOrNull { it.id == p } }

    val isEmpty: Boolean get() = !loading && pinned.isEmpty() && others.isEmpty() && subfolders.isEmpty()
    val canCreate: Boolean get() = collection != NoteCollection.Archive && collection != NoteCollection.Trash
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val notes: NoteRepository,
    private val ink: InkRepository,
    private val folders: NotebookRepository,
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

    private val noteList = combine(collection, settings.library) { c, lib -> Triple(c, lib.sort, lib.cardSize) }
        .flatMapLatest { (c, sort, size) -> notes.observeNotes(c, sort).map { NoteListData(c, sort, size, it) } }

    val uiState: StateFlow<LibraryUiState> = combine(
        noteList,
        folders.observeNotebooks(),
        tags.observeTags(),
        message,
    ) { (c, sort, size, list), books, tagList, msg ->
        val showPinnedSection = c != NoteCollection.Trash
        LibraryUiState(
            collection = c,
            collectionName = when (c) {
                is NoteCollection.InNotebook -> books.firstOrNull { it.id == c.id }?.title
                is NoteCollection.WithTag -> tagList.firstOrNull { it.id == c.id }?.name
                else -> null
            },
            sort = sort,
            cardSize = size,
            pinned = if (showPinnedSection) list.filter { it.isPinned } else emptyList(),
            others = if (showPinnedSection) list.filterNot { it.isPinned } else list,
            notebooks = books,
            tags = tagList,
            loading = false,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    // ------------------------------------------------------------ folders inside folders

    /** A new folder inside the shown folder (or at the top level). */
    fun createFolder(title: String) {
        val parent = (collection.value as? NoteCollection.InNotebook)?.id
        viewModelScope.launch { folders.create(title, parent).reportFailure() }
    }

    fun renameFolder(id: NotebookId, title: String) {
        viewModelScope.launch { folders.rename(id, title).reportFailure() }
    }

    fun moveFolder(id: NotebookId, parent: NotebookId?) {
        viewModelScope.launch { folders.move(id, parent).reportFailure() }
    }

    /** Deletes the folder and its subfolders; their notes move to "No notebook" (nothing is lost). */
    fun deleteFolder(id: NotebookId) {
        viewModelScope.launch { folders.delete(id, NotebookDeleteMode.KEEP_NOTES).reportFailure() }
    }

    fun selectCollection(value: NoteCollection) {
        collection.value = value
        savedState[KEY_COLLECTION] = encode(value)
    }

    /** If the shown notebook/tag gets deleted, fall back to All notes. */
    fun onCollectionMissing() = selectCollection(NoteCollection.All)

    fun setSort(sort: NoteSort) {
        viewModelScope.launch { settings.setLibrarySort(sort).reportFailure() }
    }

    fun setCardSize(size: com.appname.notes.core.settings.CardSize) {
        viewModelScope.launch { settings.setLibraryCardSize(size).reportFailure() }
    }

    /** New handwritten note with [template] pages in the shown notebook/tag, like [createNote]. */
    suspend fun createInkNote(template: PageTemplate = PageTemplate.LINED): NoteId? {
        val notebook = (collection.value as? NoteCollection.InNotebook)?.id
        return when (val result = ink.createInkNote(notebook, template)) {
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
        val thumb = InkThumbnail(page.info.width, page.info.height, page.info.template, page.items, pdfPage, page.info.layout)
        synchronized(thumbnails) { thumbnails[key] = thumb }
        return thumb
    }

    /**
     * "Blank note" from the + menu: typed or handwritten, as set in Settings › Note defaults; a handwritten one
     * starts with the default page style, size, paper and arrangement. Returns the note to open.
     */
    suspend fun createBlankNote(): Pair<NoteId, NoteType>? {
        val defaults = settings.noteDefaults.first()
        if (defaults.blankNote == com.appname.notes.core.settings.BlankNoteKind.TYPED) return createNote()?.let { it to NoteType.TEXT }
        val notebook = (collection.value as? NoteCollection.InNotebook)?.id
        return when (val r = ink.createInkNote(notebook, PageTemplate.BLANK, defaults.newPageLayout(), defaults.arrangement)) {
            is AppResult.Success -> {
                (collection.value as? NoteCollection.WithTag)?.let { notes.setTags(r.value, setOf(it.id)) }
                r.value to NoteType.INK
            }
            is AppResult.Failure -> { message.value = r.error.toUserMessage(); null }
        }
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

    // ------------------------------------------------------------------ owner brief §2–3: home actions

    /** Renames a note (blank names are allowed: the card then shows the first line). */
    fun rename(id: NoteId, title: String) {
        launchAction(UserMessage(R.string.msg_renamed)) { notes.setTitle(id, title.trim()) }
    }

    /** Several selected notes at once (long-press to select). */
    fun setPinned(ids: Set<NoteId>, pinned: Boolean) {
        launchAction(many(ids, if (pinned) R.string.msg_pinned_many else R.string.msg_unpinned_many, if (pinned) R.string.msg_pinned else R.string.msg_unpinned)) {
            notes.setPinned(ids, pinned)
        }
    }

    fun setFavourite(ids: Set<NoteId>, favourite: Boolean) {
        launchAction { notes.setFavourite(ids, favourite) }
    }

    fun setArchived(ids: Set<NoteId>, archived: Boolean) {
        launchAction(
            many(
                ids,
                if (archived) R.string.msg_archived_many else R.string.msg_unarchived_many,
                if (archived) R.string.msg_archived else R.string.msg_unarchived,
                undo = { setArchived(ids, !archived) },
            ),
        ) { notes.setArchived(ids, archived) }
    }

    fun moveToTrash(ids: Set<NoteId>) {
        launchAction(many(ids, R.string.msg_trashed_many, R.string.msg_trashed, undo = { restore(ids) })) { notes.moveToTrash(ids) }
    }

    fun restore(ids: Set<NoteId>) {
        launchAction(many(ids, R.string.msg_restored_many, R.string.msg_restored)) { notes.restoreFromTrash(ids) }
    }

    fun deleteForever(ids: Set<NoteId>) {
        launchAction(many(ids, R.string.msg_deleted_many, R.string.msg_deleted)) {
            notes.deletePermanently(ids).also { cleanUpPdfFiles() }
        }
    }

    fun moveToNotebook(ids: Set<NoteId>, notebook: NotebookId?) {
        launchAction(UserMessage(R.string.msg_moved)) { notes.moveToNotebook(ids, notebook) }
    }

    /**
     * "Import file" from the + menu: a PDF becomes a PDF note, Markdown/text a text note, and anything else
     * (photos, recordings, documents) a new note with the file attached. Returns the note to open.
     */
    suspend fun importFile(uri: Uri): Pair<NoteId, NoteType>? {
        val (mimeType, displayName) = files.typeAndName(uri)
        return importFile(uri, mimeType, displayName)
    }

    internal suspend fun importFile(uri: Uri, mimeType: String?, displayName: String?): Pair<NoteId, NoteType>? {
        val name = displayName.orEmpty().lowercase()
        return when {
            mimeType == "application/pdf" || name.endsWith(".pdf") -> importPdf(uri)?.let { it to NoteType.PDF }
            mimeType in TEXT_TYPES || name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt") ->
                import(uri)?.let { it to NoteType.TEXT }
            else -> {
                val notebook = (collection.value as? NoteCollection.InNotebook)?.id
                val (id, outcome) = attacher.createNote(notebook, listOf(uri))
                outcome.errorMessage?.let { message.value = UserMessage(it) }
                if (id != null) (collection.value as? NoteCollection.WithTag)?.let { notes.setTags(id, setOf(it.id)) }
                id?.let { it to NoteType.TEXT }
            }
        }
    }

    /** "3 notes archived", or the one-note wording when only one was selected. */
    private fun many(ids: Set<NoteId>, manyRes: Int, oneRes: Int, undo: (() -> Unit)? = null): UserMessage =
        if (ids.size == 1) UserMessage(oneRes, undo = undo) else UserMessage(manyRes, listOf(ids.size.toString()), undo = undo)

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
        private val TEXT_TYPES = setOf("text/markdown", "text/x-markdown", "text/plain")

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

private data class NoteListData(
    val collection: NoteCollection,
    val sort: NoteSort,
    val size: com.appname.notes.core.settings.CardSize,
    val notes: List<NoteSummary>,
)

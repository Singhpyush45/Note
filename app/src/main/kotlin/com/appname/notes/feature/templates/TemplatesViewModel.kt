package com.appname.notes.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appname.notes.R
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.model.TagId
import com.appname.notes.core.domain.repository.InkRepository
import com.appname.notes.core.domain.repository.NoteRepository
import com.appname.notes.core.domain.repository.TemplateRepository
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.template.BuiltInTemplates
import com.appname.notes.core.ink.template.TemplateCategory
import com.appname.notes.core.ink.template.TemplateDefinition
import com.appname.notes.ui.common.UserMessage
import com.appname.notes.ui.common.toUserMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One group on the screen: its templates, in order. */
data class TemplateSection(val category: TemplateCategory, val templates: List<TemplateDefinition>)

data class TemplatesUiState(
    val sections: List<TemplateSection> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val message: UserMessage? = null,
) {
    val isEmptySearch: Boolean get() = !loading && query.isNotBlank() && sections.all { it.templates.isEmpty() }
}

/**
 * The Choose-template screen (owner brief §4): built-in templates by category plus the user's own, search
 * by name, and making a note from a template. New notes go into the notebook/tag the library was showing.
 */
@HiltViewModel(assistedFactory = TemplatesViewModel.Factory::class)
class TemplatesViewModel @AssistedInject constructor(
    // Plain Strings ("" = none): the safest shape for Dagger's assisted factories.
    @Assisted("notebook") private val notebookId: String,
    @Assisted("tag") private val tagId: String,
    private val templates: TemplateRepository,
    private val ink: InkRepository,
    private val notes: NoteRepository,
    private val settings: com.appname.notes.core.settings.SettingsRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("notebook") notebookId: String, @Assisted("tag") tagId: String): TemplatesViewModel
    }

    private val query = MutableStateFlow("")
    private val message = MutableStateFlow<UserMessage?>(null)

    val uiState: StateFlow<TemplatesUiState> = combine(templates.observeCustom(), query, message) { custom, q, msg ->
        val all = BuiltInTemplates.all + custom
        val needle = q.trim()
        TemplatesUiState(
            sections = TemplateCategory.entries.map { c ->
                TemplateSection(c, all.filter { it.category == c && (needle.isEmpty() || it.name.contains(needle, ignoreCase = true)) })
            },
            query = q,
            loading = false,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplatesUiState())

    fun onQueryChange(value: String) {
        query.value = value.take(MAX_QUERY)
    }

    /** Makes a handwritten note from [template] and returns it to open; null (with a message) if that failed. */
    suspend fun use(template: TemplateDefinition): NoteId? {
        val notebook = notebookId.ifEmpty { null }?.let(::NotebookId)
        // The template sets the page size and design; the arrangement (split/infinite, direction) is the default.
        val arrangement = settings.noteDefaults.first().arrangement
        return when (val r = ink.createInkNote(notebook, PageTemplate.BLANK, template.layout, arrangement)) {
            is AppResult.Success -> {
                tagId.ifEmpty { null }?.let { notes.setTags(r.value, setOf(TagId(it))) }
                r.value
            }
            is AppResult.Failure -> { message.value = r.error.toUserMessage(); null }
        }
    }

    /** A custom copy of any template; returns its id (to edit it), or null with a message. */
    suspend fun duplicate(template: TemplateDefinition): String? =
        when (val r = templates.duplicate(template.id)) {
            is AppResult.Success -> { message.value = UserMessage(R.string.templates_msg_duplicated); r.value }
            is AppResult.Failure -> { message.value = r.error.toUserMessage(); null }
        }

    fun rename(template: TemplateDefinition, name: String) {
        viewModelScope.launch {
            when (val r = templates.rename(template.id, name)) {
                is AppResult.Success -> message.value = UserMessage(R.string.templates_msg_renamed)
                is AppResult.Failure -> message.value = r.error.toUserMessage()
            }
        }
    }

    /** Deletes a custom template. Notes made from it keep their pages exactly as they are. */
    fun delete(template: TemplateDefinition) {
        viewModelScope.launch {
            when (val r = templates.delete(template.id)) {
                is AppResult.Success -> message.value = UserMessage(R.string.templates_msg_deleted, listOf(template.name))
                is AppResult.Failure -> message.value = r.error.toUserMessage()
            }
        }
    }

    fun messageShown() {
        message.value = null
    }

    private companion object {
        const val MAX_QUERY = 60
    }
}

package com.appname.notes.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.domain.repository.TemplateRepository
import com.appname.notes.core.ink.template.TemplateDraft
import com.appname.notes.core.ink.template.TemplateJson
import com.appname.notes.ui.common.UserMessage
import com.appname.notes.ui.common.toUserMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplateEditorUiState(
    val loading: Boolean = true,
    /** Editing an existing template (vs making a new one). */
    val existing: Boolean = false,
    val name: String = "",
    val draft: TemplateDraft = TemplateDraft.NEW,
    val saving: Boolean = false,
    /** Set once saved: the screen closes. */
    val saved: Boolean = false,
    /** The template no longer exists (deleted elsewhere). */
    val missing: Boolean = false,
    /** Something was changed and not saved yet. */
    val dirty: Boolean = false,
    val message: UserMessage? = null,
) {
    val canSave: Boolean get() = !loading && !saving && TemplateRepository.cleanName(name) != null
}

/** Creates or edits one custom template (owner brief §4). [templateId] "" = a new template. */
@HiltViewModel(assistedFactory = TemplateEditorViewModel.Factory::class)
class TemplateEditorViewModel @AssistedInject constructor(
    @Assisted private val templateId: String,
    private val templates: TemplateRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(templateId: String): TemplateEditorViewModel
    }

    private val _ui = MutableStateFlow(TemplateEditorUiState())
    val uiState: StateFlow<TemplateEditorUiState> = _ui.asStateFlow()

    init {
        // Unsaved changes survive rotation and process death.
        val restored = savedState.contains(KEY_LAYOUT) || savedState.contains(KEY_NAME)
        val restoredDraft = savedState.get<String>(KEY_LAYOUT)?.let { TemplateJson.decodeOrNull(it) }?.let { TemplateDraft.from(it) }
        viewModelScope.launch {
            if (templateId.isEmpty()) {
                _ui.value = TemplateEditorUiState(
                    loading = false,
                    name = savedState[KEY_NAME] ?: "",
                    draft = restoredDraft ?: TemplateDraft.NEW,
                    dirty = restored,
                )
                return@launch
            }
            val t = templates.get(templateId)
            _ui.value = if (t == null || !t.isCustom) {
                TemplateEditorUiState(loading = false, missing = true)
            } else {
                TemplateEditorUiState(
                    loading = false,
                    existing = true,
                    name = savedState[KEY_NAME] ?: t.name,
                    draft = restoredDraft ?: TemplateDraft.from(t.layout),
                    dirty = restored,
                )
            }
        }
    }

    fun onNameChange(value: String) {
        val name = value.take(TemplateRepository.MAX_NAME)
        savedState[KEY_NAME] = name
        _ui.update { it.copy(name = name, dirty = true) }
    }

    fun onDraftChange(draft: TemplateDraft) {
        savedState[KEY_LAYOUT] = TemplateJson.encode(draft.toLayout())
        _ui.update { it.copy(draft = draft, dirty = true) }
    }

    fun save() {
        val s = _ui.value
        if (!s.canSave) return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val layout = s.draft.toLayout()
            val result = if (s.existing) templates.update(templateId, s.name, layout) else templates.create(s.name, layout)
            _ui.update {
                when (result) {
                    is AppResult.Success -> it.copy(saving = false, saved = true)
                    is AppResult.Failure -> it.copy(saving = false, message = result.error.toUserMessage())
                }
            }
        }
    }

    fun messageShown() {
        _ui.update { it.copy(message = null) }
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_LAYOUT = "layout"
    }
}

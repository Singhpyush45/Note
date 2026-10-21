package com.appname.notes.feature.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appname.notes.R
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookDeleteMode
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.model.Tag
import com.appname.notes.core.domain.model.TagId
import com.appname.notes.core.domain.repository.NotebookRepository
import com.appname.notes.core.domain.repository.TagRepository
import com.appname.notes.ui.common.UserMessage
import com.appname.notes.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NotebooksViewModel @Inject constructor(private val repo: NotebookRepository) : ViewModel() {

    val notebooks: StateFlow<List<Notebook>?> =
        repo.observeNotebooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    fun create(title: String) = launchRepo { repo.create(title) }
    fun rename(id: NotebookId, title: String) = launchRepo { repo.rename(id, title) }
    fun delete(id: NotebookId, mode: NotebookDeleteMode) = launchRepo { repo.delete(id, mode) }

    /** Moves a notebook one place up (-1) or down (+1) — an accessible alternative to drag and drop. */
    fun move(id: NotebookId, direction: Int) {
        val list = notebooks.value ?: return
        val from = list.indexOfFirst { it.id == id }
        val to = from + direction
        if (from < 0 || to !in list.indices) return
        val order = list.map { it.id }.toMutableList().apply { add(to, removeAt(from)) }
        launchRepo { repo.reorder(order) }
    }

    fun messageShown() {
        _message.value = null
    }

    private fun launchRepo(block: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            (block() as? AppResult.Failure)?.let { _message.value = it.error.toUserMessage() }
        }
    }
}

@HiltViewModel
class TagsViewModel @Inject constructor(private val repo: TagRepository) : ViewModel() {

    val tags: StateFlow<List<Tag>?> = repo.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    fun rename(id: TagId, name: String) {
        viewModelScope.launch {
            val result = repo.rename(id, name)
            if (result is AppResult.Failure) {
                val error = result.error
                _message.value = if (error is DomainError.Validation && error.reason == "exists") {
                    UserMessage(R.string.tags_name_exists)
                } else {
                    error.toUserMessage()
                }
            }
        }
    }

    fun delete(id: TagId) {
        viewModelScope.launch { (repo.delete(id) as? AppResult.Failure)?.let { _message.value = it.error.toUserMessage() } }
    }

    fun messageShown() {
        _message.value = null
    }
}

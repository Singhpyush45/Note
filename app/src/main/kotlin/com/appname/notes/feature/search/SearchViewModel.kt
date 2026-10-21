package com.appname.notes.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appname.notes.core.domain.model.ModifiedWithin
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.core.domain.model.SearchFilters
import com.appname.notes.core.domain.model.SearchHit
import com.appname.notes.core.domain.model.Tag
import com.appname.notes.core.domain.model.TagId
import com.appname.notes.core.domain.repository.NoteRepository
import com.appname.notes.core.domain.repository.NotebookRepository
import com.appname.notes.core.domain.repository.TagRepository
import com.appname.notes.core.domain.search.SearchQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    /** null = nothing searched yet (empty query). */
    val results: List<SearchHit>? = null,
    val notebooks: List<Notebook> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val notes: NoteRepository,
    notebooks: NotebookRepository,
    tags: TagRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val query = MutableStateFlow(savedState.get<String>(KEY_QUERY).orEmpty())
    private val filters = MutableStateFlow(SearchFilters())

    private val results = combine(query.debounce(DEBOUNCE_MS).onStart { emit(query.value) }, filters) { q, f -> q to f }
        .flatMapLatest { (q, f) ->
            if (SearchQuery.toMatchExpression(q) == null) flowOf<List<SearchHit>?>(null) else notes.search(q, f)
        }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        filters,
        results,
        notebooks.observeNotebooks(),
        tags.observeTags(),
    ) { q, f, r, books, tagList -> SearchUiState(q, f, r, books, tagList) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(query = query.value))

    fun onQueryChange(value: String) {
        query.value = value
        savedState[KEY_QUERY] = value
    }

    fun setNotebook(id: NotebookId?) = filters.value.let { filters.value = it.copy(notebookId = id) }
    fun setTag(id: TagId?) = filters.value.let { filters.value = it.copy(tagId = id) }
    fun setModified(value: ModifiedWithin) = filters.value.let { filters.value = it.copy(modifiedWithin = value) }
    fun setIncludeArchived(value: Boolean) = filters.value.let { filters.value = it.copy(includeArchived = value) }

    private companion object {
        const val KEY_QUERY = "query"
        const val DEBOUNCE_MS = 150L
    }
}

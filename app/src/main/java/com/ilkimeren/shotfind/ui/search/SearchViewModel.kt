package com.ilkimeren.shotfind.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.data.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Results(val query: String, val results: List<SearchResult>) : SearchUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(application)

    /** What's typed in the search box. */
    var query by mutableStateOf("")
        private set

    fun onQueryChange(newQuery: String) {
        query = newQuery
    }

    val uiState: StateFlow<SearchUiState> = snapshotFlow { query }
        .map { it.trim() }
        .debounce(250)              // wait until the user pauses typing
        .distinctUntilChanged()
        .flatMapLatest { q ->       // a new query cancels the previous search
            if (q.length < MIN_QUERY_LENGTH) {
                flowOf(SearchUiState.Idle)
            } else {
                repository.search(q).map { SearchUiState.Results(q, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle)

    val pendingOcrCount: StateFlow<Int> = repository.observePendingOcrCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
package com.ilkimeren.shotfind.ui.inbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.data.ScreenshotRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ilkimeren.shotfind.work.OcrWorker

/** Everything the inbox screen can be showing. */
sealed interface InboxUiState {
    data object Loading : InboxUiState
    data class Loaded(
        val screenshots: List<Screenshot>,
        val totalCount: Int,       // all screenshots in the database, including sorted ones
        val pendingOcrCount: Int   // screenshots whose text hasn't been read yet
    ) : InboxUiState
    data class Error(val message: String) : InboxUiState
}

class InboxViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(application)
    private val syncError = MutableStateFlow<String?>(null)

    // Combines the live database results into one UI state
    val uiState: StateFlow<InboxUiState> = combine(
        repository.observeInbox(),
        repository.observeTotalCount(),
        repository.observePendingOcrCount(),
        syncError
    ) { inbox, total, pendingOcr, error ->
        if (error != null) InboxUiState.Error(error)
        else InboxUiState.Loaded(inbox, total, pendingOcr)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InboxUiState.Loading
    )

    /** Syncs the database with MediaStore. The UI updates by itself through the Flow. */
    fun loadScreenshots() {
        viewModelScope.launch {
            syncError.value = try {
                repository.syncFromMediaStore()
                OcrWorker.start(getApplication())
                null
            } catch (e: SecurityException) {
                "Fotoğraflara erişim izni yok."
            }
        }
    }
}
package com.ilkimeren.shotfind.ui.detail

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.data.ScreenshotRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(application)

    // Comes from the navigation route "detail/{mediaId}"
    private val mediaId: Long = checkNotNull(savedStateHandle[MEDIA_ID_ARG])

    val screenshot: StateFlow<Screenshot?> = repository.observeScreenshot(mediaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Text being edited. null means "not edited, show the saved note". */
    var noteDraft by mutableStateOf<String?>(null)
        private set

    /** True for a moment after a successful save, to show a confirmation. */
    var noteSaved by mutableStateOf(false)
        private set

    fun onNoteChange(text: String) {
        noteDraft = text
        noteSaved = false
    }

    fun saveNote() {
        val draft = noteDraft ?: return
        viewModelScope.launch {
            repository.saveNote(mediaId, draft)
            noteDraft = null
            noteSaved = true
            delay(2_000)     // show the confirmation for 2 seconds
            noteSaved = false
        }
    }

    companion object {
        const val MEDIA_ID_ARG = "mediaId"
    }
}
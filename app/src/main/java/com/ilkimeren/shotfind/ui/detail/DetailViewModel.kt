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
import android.content.IntentSender
import com.ilkimeren.shotfind.data.TagEntity
import com.ilkimeren.shotfind.data.TagRepository

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(application)
    private val tagRepository = TagRepository(application)

    // Comes from the navigation route "detail/{mediaId}"
    private val mediaId: Long = checkNotNull(savedStateHandle[MEDIA_ID_ARG])

    val screenshot: StateFlow<Screenshot?> = repository.observeScreenshot(mediaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tags: StateFlow<List<TagEntity>> = tagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** When set, the screen shows the system "allow ShotFind to modify?" dialog. */
    var writeRequest by mutableStateOf<IntentSender?>(null)
        private set

    /** Shown briefly if a move fails. */
    var moveFailed by mutableStateOf(false)
        private set

    // Remembers where the user wanted to move while the confirmation dialog is open.
    // A wrapper is needed because "null" is a valid target (back to inbox).
    private data class PendingMove(val tagFolder: String?)
    private var pendingMove: PendingMove? = null

    fun onTagSelected(tagFolder: String?) {
        val current = screenshot.value ?: return
        moveFailed = false
        // Already there, nothing to do
        if (ScreenshotRepository.tagFolderOf(current.relativePath) == tagFolder) return

        // Android always requires a write request to change another app's file.
        // With media management permission the system approves it instantly, no dialog.
        pendingMove = PendingMove(tagFolder)
        writeRequest = repository.createWriteRequest(current.uri)
    }

    /** Called right after the dialog is shown, so it isn't shown again (e.g. on rotation). */
    fun onWriteRequestLaunched() {
        writeRequest = null
    }

    fun onWriteRequestResult(granted: Boolean) {
        val target = pendingMove ?: return
        pendingMove = null
        val current = screenshot.value ?: return
        if (granted) {
            move(current, target.tagFolder)
        } else {
            moveFailed = true // user said no in the system dialog
        }
    }

    private fun move(current: Screenshot, tagFolder: String?) {
        viewModelScope.launch {
            val ok = repository.moveToFolder(current, tagFolder)
            if (!ok) moveFailed = true
        }
    }

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
package com.ilkimeren.shotfind.ui.sort

import android.app.Application
import android.content.IntentSender
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.data.TagEntity
import com.ilkimeren.shotfind.data.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SortViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(application)
    private val tagRepository = TagRepository(application)

    // Cards hidden right after a swipe, before the database confirms the change.
    // If the action fails, the card is un-hidden and comes back.
    private val hidden = MutableStateFlow<Set<Long>>(emptySet())

    /** Cards left to sort, newest first. null while loading. */
    val cards: StateFlow<List<Screenshot>?> = combine(repository.observeInbox(), hidden) { inbox, hiddenIds ->
        inbox.filter { it.id !in hiddenIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tags: StateFlow<List<TagEntity>> = tagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** When set, the screen shows a system write/trash dialog (skipped with media management). */
    var writeRequest by mutableStateOf<IntentSender?>(null)
        private set

    /** The screenshot whose tag picker is open, or null. */
    var tagPickerFor by mutableStateOf<Screenshot?>(null)
        private set

    var canUndo by mutableStateOf(false)
        private set

    // ---- What was done, for undo ----
    private sealed interface Done {
        val screenshot: Screenshot
        data class Kept(override val screenshot: Screenshot) : Done
        data class Trashed(override val screenshot: Screenshot) : Done
        data class Moved(override val screenshot: Screenshot) : Done
    }
    private val history = ArrayDeque<Done>()

    // ---- What is waiting for the system dialog ----
    private sealed interface Pending {
        data class Trash(val screenshot: Screenshot) : Pending
        data class Move(val screenshot: Screenshot, val folder: String) : Pending
        data class Restore(val screenshot: Screenshot) : Pending
        data class MoveBack(val screenshot: Screenshot) : Pending
    }
    private var pending: Pending? = null

    // ---- Actions ----

    fun keep(screenshot: Screenshot) {
        hide(screenshot)
        viewModelScope.launch {
            repository.markKept(screenshot.id)
            push(Done.Kept(screenshot))
        }
    }

    fun trash(screenshot: Screenshot) {
        hide(screenshot)
        request(Pending.Trash(screenshot)) { repository.createTrashRequest(listOf(screenshot.uri)) }
    }

    fun openTagPicker(screenshot: Screenshot) {
        tagPickerFor = screenshot
    }

    fun closeTagPicker() {
        tagPickerFor = null
    }

    fun moveToTag(screenshot: Screenshot, folder: String) {
        tagPickerFor = null
        hide(screenshot)
        request(Pending.Move(screenshot, folder)) { repository.createWriteRequest(screenshot.uri) }
    }

    fun undo() {
        val last = history.removeLastOrNull() ?: return
        canUndo = history.isNotEmpty()
        when (last) {
            is Done.Kept -> viewModelScope.launch {
                repository.clearKept(last.screenshot.id)
                show(last.screenshot)
            }
            is Done.Trashed -> request(Pending.Restore(last.screenshot)) {
                repository.createRestoreRequest(listOf(last.screenshot.uri))
            }
            is Done.Moved -> request(Pending.MoveBack(last.screenshot)) {
                repository.createWriteRequest(last.screenshot.uri)
            }
        }
    }

    // ---- System dialog plumbing ----

    fun onWriteRequestLaunched() {
        writeRequest = null
    }

    fun onWriteRequestResult(granted: Boolean) {
        val op = pending ?: return
        pending = null
        viewModelScope.launch {
            when (op) {
                is Pending.Trash ->
                    if (granted) {
                        repository.markTrashed(op.screenshot.id)
                        push(Done.Trashed(op.screenshot))
                    } else {
                        show(op.screenshot)
                    }

                is Pending.Move ->
                    if (granted && repository.moveToFolder(op.screenshot, op.folder)) {
                        push(Done.Moved(op.screenshot))
                    } else {
                        show(op.screenshot)
                    }

                is Pending.Restore ->
                    if (granted) {
                        repository.markRestored(op.screenshot.id)
                        show(op.screenshot)
                    } else {
                        push(Done.Trashed(op.screenshot)) // keep it undoable
                    }

                is Pending.MoveBack -> {
                    // Read the current location; the old object still points to the inbox
                    val current = repository.getScreenshot(op.screenshot.id)
                    if (granted && current != null && repository.moveToFolder(current, null)) {
                        show(op.screenshot)
                    } else {
                        push(Done.Moved(op.screenshot))
                    }
                }
            }
        }
    }

    private fun request(op: Pending, create: () -> IntentSender) {
        pending = op
        writeRequest = try {
            create()
        } catch (e: Exception) {
            pending = null
            when (op) {
                is Pending.Trash -> show(op.screenshot)
                is Pending.Move -> show(op.screenshot)
                else -> Unit
            }
            null
        }
    }

    private fun hide(screenshot: Screenshot) = hidden.update { it + screenshot.id }

    private fun show(screenshot: Screenshot) = hidden.update { it - screenshot.id }

    private fun push(done: Done) {
        history.addLast(done)
        if (history.size > MAX_UNDO) history.removeFirst()
        canUndo = true
    }

    private companion object {
        const val MAX_UNDO = 20
    }
}
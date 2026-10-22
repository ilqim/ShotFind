package com.ilkimeren.shotfind.ui.tags

import android.app.Application
import android.content.IntentSender
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.data.TagEntity
import com.ilkimeren.shotfind.data.TagRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlbumUiState(
    val title: String,
    val tag: TagEntity?,          // null for the "kept" album, which can't be edited
    val screenshots: List<Screenshot>
)

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(application)
    private val tagRepository = TagRepository(application)

    private val albumId: Long = checkNotNull(savedStateHandle[ALBUM_ID_ARG])

    /** null while loading. */
    val uiState: StateFlow<AlbumUiState?> =
        if (albumId == KEPT_ALBUM_ID) {
            repository.observeKept().map { AlbumUiState("Saklananlar", null, it) }
        } else {
            // Follow the tag, so a rename shows up right away
            tagRepository.observeById(albumId).flatMapLatest { tag ->
                if (tag == null) {
                    flowOf(AlbumUiState("Etiket", null, emptyList()))
                } else {
                    repository.observeTagAlbum(tag.folderName).map { AlbumUiState(tag.name, tag, it) }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var showEditDialog by mutableStateOf(false)
        private set
    var editError by mutableStateOf<String?>(null)
        private set
    var showDeleteDialog by mutableStateOf(false)
        private set
    /** True while screenshots are being moved. */
    var working by mutableStateOf(false)
        private set
    /** True after the tag was deleted; the screen should close. */
    var closed by mutableStateOf(false)
        private set
    var writeRequest by mutableStateOf<IntentSender?>(null)
        private set

    private sealed interface Pending {
        data class Rename(
            val tag: TagEntity,
            val newName: String,
            val newFolder: String,
            val color: Int,
            val screenshots: List<Screenshot>
        ) : Pending
        data class Delete(val tag: TagEntity, val screenshots: List<Screenshot>) : Pending
    }
    private var pending: Pending? = null

    // ---- Edit ----

    fun openEdit() {
        editError = null
        showEditDialog = true
    }

    fun closeEdit() {
        showEditDialog = false
    }

    fun saveEdit(name: String, colorArgb: Int) {
        val state = uiState.value ?: return
        val tag = state.tag ?: return
        viewModelScope.launch {
            tagRepository.checkName(name, excludingId = tag.id)?.let {
                editError = it
                return@launch
            }
            val newFolder = TagRepository.folderNameFor(name)
            val updated = tag.copy(name = name.trim(), folderName = newFolder, colorArgb = colorArgb)

            if (newFolder == tag.folderName || state.screenshots.isEmpty()) {
                // Only the name/color changed, or there are no files to move
                tagRepository.update(updated)
                showEditDialog = false
            } else {
                // The folder changes: move every screenshot into the new folder first
                showEditDialog = false
                pending = Pending.Rename(tag, updated.name, newFolder, colorArgb, state.screenshots)
                requestWrite(state.screenshots)
            }
        }
    }

    // ---- Delete ----

    fun openDelete() {
        showDeleteDialog = true
    }

    fun closeDelete() {
        showDeleteDialog = false
    }

    fun confirmDelete() {
        showDeleteDialog = false
        val state = uiState.value ?: return
        val tag = state.tag ?: return
        if (state.screenshots.isEmpty()) {
            viewModelScope.launch {
                tagRepository.delete(tag)
                closed = true
            }
        } else {
            // Move the screenshots back to the inbox, then delete the tag
            pending = Pending.Delete(tag, state.screenshots)
            requestWrite(state.screenshots)
        }
    }

    // ---- System dialog plumbing ----

    fun onWriteRequestLaunched() {
        writeRequest = null
    }

    fun onWriteRequestResult(granted: Boolean) {
        val op = pending ?: return
        pending = null
        if (!granted) return
        viewModelScope.launch {
            working = true
            when (op) {
                is Pending.Rename -> {
                    op.screenshots.forEach { repository.moveToFolder(it, op.newFolder) }
                    tagRepository.update(
                        op.tag.copy(name = op.newName, folderName = op.newFolder, colorArgb = op.color)
                    )
                }
                is Pending.Delete -> {
                    op.screenshots.forEach { repository.moveToFolder(it, null) }
                    tagRepository.delete(op.tag)
                    closed = true
                }
            }
            working = false
        }
    }

    private fun requestWrite(screenshots: List<Screenshot>) {
        writeRequest = try {
            repository.createWriteRequest(screenshots.map { it.uri })
        } catch (e: Exception) {
            pending = null
            null
        }
    }

    companion object {
        const val ALBUM_ID_ARG = "albumId"
        /** Not a real tag id; means "the kept screenshots". */
        const val KEPT_ALBUM_ID = -1L
    }
}
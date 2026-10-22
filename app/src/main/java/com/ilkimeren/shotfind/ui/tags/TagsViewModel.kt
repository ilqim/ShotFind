package com.ilkimeren.shotfind.ui.tags

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.data.TagEntity
import com.ilkimeren.shotfind.data.TagRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TagAlbum(
    val tag: TagEntity,
    val count: Int,
    val coverUri: Uri?
)

data class TagsUiState(
    val albums: List<TagAlbum>,
    val keptCount: Int,
    val trashedCount: Int
)

class TagsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(application)
    private val tagRepository = TagRepository(application)

    /** null while loading. */
    val uiState: StateFlow<TagsUiState?> = combine(
        tagRepository.observeTags(),
        repository.observeFolderSummaries(),
        repository.observeKeptCount(),
        repository.observeTrashedCount()
    ) { tags, summaries, keptCount, trashedCount ->
        // Match folders like DCIM/Screenshots/Ders/ to the tag "Ders"
        val byFolder = summaries.groupBy { ScreenshotRepository.tagFolderOf(it.relativePath) }
        val albums = tags.map { tag ->
            val folders = byFolder[tag.folderName].orEmpty()
            TagAlbum(
                tag = tag,
                count = folders.sumOf { it.count },
                coverUri = folders.firstNotNullOfOrNull { it.coverUri }?.toUri()
            )
        }
        TagsUiState(albums, keptCount, trashedCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var showCreateDialog by mutableStateOf(false)
        private set

    var createError by mutableStateOf<String?>(null)
        private set

    fun openCreate() {
        createError = null
        showCreateDialog = true
    }

    fun closeCreate() {
        showCreateDialog = false
    }

    fun create(name: String, colorArgb: Int) {
        viewModelScope.launch {
            val error = tagRepository.createTag(name, colorArgb)
            if (error == null) showCreateDialog = false else createError = error
        }
    }
}
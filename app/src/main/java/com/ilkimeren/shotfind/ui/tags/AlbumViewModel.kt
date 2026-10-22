package com.ilkimeren.shotfind.ui.tags

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.data.TagRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AlbumUiState(
    val title: String,
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
            repository.observeKept().map { AlbumUiState("Saklananlar", it) }
        } else {
            // Look up the tag once, then follow its folder
            flow { emit(tagRepository.getById(albumId)) }
                .flatMapLatest { tag ->
                    if (tag == null) {
                        flowOf(AlbumUiState("Etiket", emptyList()))
                    } else {
                        repository.observeTagAlbum(tag.folderName).map { AlbumUiState(tag.name, it) }
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        const val ALBUM_ID_ARG = "albumId"
        /** Not a real tag id; means "the kept screenshots". */
        const val KEPT_ALBUM_ID = -1L
    }
}
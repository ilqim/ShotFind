package com.ilkimeren.shotfind.ui.trash

import android.app.Application
import android.content.IntentSender
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.data.ScreenshotRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrashViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(application)

    val items: StateFlow<List<Screenshot>> = repository.observeTrashed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** When set, the screen shows the system "restore from trash?" dialog. */
    var restoreRequest by mutableStateOf<IntentSender?>(null)
        private set

    /** Shown if a restore can't even be requested (e.g. the files are already gone). */
    var restoreFailed by mutableStateOf(false)
        private set

    private var pendingIds: List<Long> = emptyList()

    fun restore(screenshots: List<Screenshot>) {
        if (screenshots.isEmpty()) return
        restoreFailed = false
        pendingIds = screenshots.map { it.id }
        restoreRequest = try {
            repository.createRestoreRequest(screenshots.map { it.uri })
        } catch (e: Exception) {
            restoreFailed = true
            null
        }
    }

    fun onRestoreRequestLaunched() {
        restoreRequest = null
    }

    fun onRestoreResult(granted: Boolean) {
        val ids = pendingIds
        pendingIds = emptyList()
        if (granted) {
            viewModelScope.launch { repository.markRestored(ids) }
        }
    }
}
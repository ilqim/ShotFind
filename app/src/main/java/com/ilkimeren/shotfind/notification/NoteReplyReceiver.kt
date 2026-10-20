package com.ilkimeren.shotfind.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.util.ShotNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives the text typed into the notification's "Not ekle" box. */
class NoteReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mediaId = intent.getLongExtra(ShotNotifications.EXTRA_MEDIA_ID, -1L)
        if (mediaId < 0) return

        val note = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(ShotNotifications.KEY_NOTE_REPLY)
            ?.toString()
            ?.trim()

        val appContext = context.applicationContext
        if (note.isNullOrEmpty()) {
            ShotNotifications.cancel(appContext, mediaId)
            return
        }

        // Receivers must finish quickly; goAsync() gives us a few extra seconds
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ScreenshotRepository(appContext).saveNote(mediaId, note)
                // Replacing the notification stops the "sending" spinner in the reply box
                ShotNotifications.showNoteSaved(appContext, mediaId, note)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
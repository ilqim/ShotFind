package com.ilkimeren.shotfind.notification

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.util.ShotNotifications
import kotlinx.coroutines.launch

/**
 * Invisible activity that shows the system "move to trash?" confirmation.
 * If the app has media management permission, the system skips the dialog.
 */
class TrashActivity : ComponentActivity() {

    private var mediaId = -1L

    private val trashLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            ShotNotifications.cancel(this, mediaId)
            lifecycleScope.launch {
                ScreenshotRepository(applicationContext).removeFromDatabase(mediaId)
                finish()
            }
        } else {
            finish() // user cancelled; keep the notification
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaId = intent.getLongExtra(ShotNotifications.EXTRA_MEDIA_ID, -1L)
        if (mediaId < 0) {
            finish()
            return
        }
        // After a rotation the dialog is already showing; don't open a second one
        if (savedInstanceState != null) return

        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        try {
            val request = MediaStore.createTrashRequest(contentResolver, listOf(uri), true)
            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (e: Exception) {
            // e.g. the file was already deleted from the gallery
            ShotNotifications.cancel(this, mediaId)
            finish()
        }
    }
}
package com.ilkimeren.shotfind.notification

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.util.ShotNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        Log.d(TAG, "Trash result: ${result.resultCode} (OK = $RESULT_OK)")
        if (result.resultCode == RESULT_OK) {
            val appContext = applicationContext
            val id = mediaId
            // Not tied to this activity, so closing it can't cancel the work halfway
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    ScreenshotRepository(appContext).markTrashed(id)
                    Log.d(TAG, "Marked as trashed: $id")
                    ShotNotifications.showTrashed(appContext, id)
                    Log.d(TAG, "Trashed notification posted: $id")
                } catch (e: Exception) {
                    Log.e(TAG, "After-trash work failed", e)
                }
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaId = intent.getLongExtra(ShotNotifications.EXTRA_MEDIA_ID, -1L)
        Log.d(TAG, "TrashActivity opened for $mediaId")
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
            Log.e(TAG, "Could not create trash request", e)
            ShotNotifications.cancel(this, mediaId)
            finish()
        }
    }

    private companion object {
        const val TAG = "ShotFindTrash"
    }
}
package com.ilkimeren.shotfind.notification

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.util.ShotNotifications
import kotlinx.coroutines.launch

/** Invisible activity behind "Geri al": takes the screenshot back out of the trash. */
class RestoreActivity : ComponentActivity() {

    private var mediaId = -1L

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            ShotNotifications.cancel(this, mediaId)
            lifecycleScope.launch {
                ScreenshotRepository(applicationContext).markRestored(mediaId)
                Toast.makeText(applicationContext, "Geri alındı", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaId = intent.getLongExtra(ShotNotifications.EXTRA_MEDIA_ID, -1L)
        if (mediaId < 0) {
            finish()
            return
        }
        if (savedInstanceState != null) return

        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        try {
            // false = take out of the trash
            val request = MediaStore.createTrashRequest(contentResolver, listOf(uri), false)
            restoreLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Geri alınamadı", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
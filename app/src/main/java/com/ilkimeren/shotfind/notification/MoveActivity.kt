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

/**
 * Invisible activity behind the notification's tag button: asks for write access
 * (approved silently with media management) and moves the screenshot.
 */
class MoveActivity : ComponentActivity() {

    private var mediaId = -1L
    private var folderName: String? = null
    private var tagName: String? = null

    private val writeLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            finish() // user said no; keep the notification
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val repository = ScreenshotRepository(applicationContext)
            val screenshot = repository.getScreenshot(mediaId)
            val moved = screenshot != null && repository.moveToFolder(screenshot, folderName)
            if (moved) {
                ShotNotifications.showMoved(applicationContext, mediaId, tagName ?: folderName.orEmpty())
            } else {
                Toast.makeText(applicationContext, "Taşınamadı", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaId = intent.getLongExtra(ShotNotifications.EXTRA_MEDIA_ID, -1L)
        folderName = intent.getStringExtra(ShotNotifications.EXTRA_TAG_FOLDER)
        tagName = intent.getStringExtra(ShotNotifications.EXTRA_TAG_NAME)
        if (mediaId < 0 || folderName == null) {
            finish()
            return
        }
        if (savedInstanceState != null) return // dialog already showing after rotation

        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        try {
            val sender = ScreenshotRepository(applicationContext).createWriteRequest(uri)
            writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (e: Exception) {
            finish()
        }
    }
}
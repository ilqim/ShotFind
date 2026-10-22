package com.ilkimeren.shotfind.notification

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.util.ShotNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Invisible activity behind "Çöpe at" on the expired-temporary notification. */
class TempCleanupActivity : ComponentActivity() {

    private var mediaIds: LongArray = LongArray(0)

    private val trashLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val appContext = applicationContext
            val ids = mediaIds
            ShotNotifications.cancelTempExpired(appContext)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val repository = ScreenshotRepository(appContext)
                ids.forEach { repository.markTrashed(it) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "${ids.size} ekran görüntüsü çöpe atıldı", Toast.LENGTH_SHORT).show()
                }
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaIds = intent.getLongArrayExtra(ShotNotifications.EXTRA_MEDIA_IDS) ?: LongArray(0)
        if (mediaIds.isEmpty()) {
            finish()
            return
        }
        if (savedInstanceState != null) return

        val uris = mediaIds.map { ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it) }
        try {
            val request = MediaStore.createTrashRequest(contentResolver, uris, true)
            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (e: Exception) {
            finish()
        }
    }
}
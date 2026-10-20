package com.ilkimeren.shotfind.work

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.util.ShotNotifications
import java.time.Duration

/**
 * Runs when something changes in the phone's photo library (even if the app is closed),
 * picks up new screenshots and shows a notification for them.
 * The trigger fires only once, so the worker re-arms itself every time.
 */
class NewScreenshotWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val repository = ScreenshotRepository(applicationContext)
            val added = repository.syncNewScreenshots()

            // Only notify for screenshots that really just arrived, in the inbox folder.
            // (The very first sync adds every old screenshot; those must not notify.)
            val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
            val fresh = added.filter {
                it.relativePath in ScreenshotRepository.INBOX_FOLDERS && it.dateAddedMillis >= cutoff
            }

            if (fresh.size > MAX_INDIVIDUAL_NOTIFICATIONS) {
                ShotNotifications.showSummary(applicationContext, fresh.size)
            } else {
                fresh.forEach { ShotNotifications.showNewScreenshot(applicationContext, it) }
            }

            if (added.isNotEmpty()) OcrWorker.start(applicationContext)
        } catch (e: SecurityException) {
            // No photo permission yet; nothing to do until the user grants it
        } finally {
            rearm(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "new_screenshot_watcher"
        private const val RECENT_WINDOW_MS = 10 * 60 * 1000L // 10 minutes
        private const val MAX_INDIVIDUAL_NOTIFICATIONS = 3

        /** Call on app start. Does nothing if the watcher is already waiting. */
        fun ensureScheduled(context: Context) =
            enqueue(context, ExistingWorkPolicy.KEEP)

        /** Queues the next watcher to start after the current run finishes. */
        private fun rearm(context: Context) =
            enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(Duration.ofSeconds(1)) // wait for writes to settle
                .setTriggerContentMaxDelay(Duration.ofSeconds(5))    // but never longer than this
                .build()

            val request = OneTimeWorkRequestBuilder<NewScreenshotWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
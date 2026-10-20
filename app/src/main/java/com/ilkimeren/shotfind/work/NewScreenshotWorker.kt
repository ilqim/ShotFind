package com.ilkimeren.shotfind.work

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.util.ShotNotifications
import kotlinx.coroutines.CancellationException
import java.time.Duration

/**
 * Watches the photo library for new screenshots, even when the app is closed.
 *
 * A content trigger fires only once, so two watchers ("slots" A and B) take turns:
 * when one starts, it first arms the other, so a screenshot taken while this one is
 * running is still caught. No chaining, so one failed run can't stop the watching.
 */
class NewScreenshotWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mySlot = inputData.getString(KEY_SLOT) ?: SLOT_A
        val otherSlot = if (mySlot == SLOT_A) SLOT_B else SLOT_A
        Log.d(TAG, "Watcher $mySlot started (${triggeredContentUris.size} changes)")

        // Arm the other watcher before doing anything, so there is no blind spot
        enqueue(applicationContext, otherSlot)
        try {
            detectAndNotify(applicationContext)
        } catch (e: CancellationException) {
            throw e // the system stopped us; let it
        } catch (e: Exception) {
            Log.w(TAG, "Watcher run failed", e)
        } finally {
            // In case the other watcher was busy when we started, make sure one is waiting
            enqueue(applicationContext, otherSlot)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ShotFindWatcher"
        private const val WORK_NAME_PREFIX = "new_screenshot_watcher_"
        private const val LEGACY_WORK_NAME = "new_screenshot_watcher"
        private const val KEY_SLOT = "slot"
        private const val SLOT_A = "A"
        private const val SLOT_B = "B"
        private const val RECENT_WINDOW_MS = 30 * 60 * 1000L // longer than the watchdog interval
        private const val MAX_INDIVIDUAL_NOTIFICATIONS = 3

        /**
         * Finds screenshots added since the last check and notifies about the fresh ones.
         * Shared by the watcher and the watchdog. Returns true if anything new was found.
         */
        suspend fun detectAndNotify(context: Context): Boolean {
            val repository = ScreenshotRepository(context)
            val added = try {
                repository.syncNewScreenshots()
            } catch (e: SecurityException) {
                return false // no photo permission yet
            }
            if (added.isEmpty()) return false

            // The very first sync adds every old screenshot; those must not notify
            val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
            val fresh = added.filter {
                it.relativePath in ScreenshotRepository.INBOX_FOLDERS && it.dateAddedMillis >= cutoff
            }
            if (fresh.size > MAX_INDIVIDUAL_NOTIFICATIONS) {
                ShotNotifications.showSummary(context, fresh.size)
            } else {
                fresh.forEach { screenshot ->
                    val suggestion = repository.suggestTag(screenshot)
                    ShotNotifications.showNewScreenshot(context, screenshot, suggestion)
                }
            }
            OcrWorker.start(context)
            return true
        }

        /** Call on app start and from the watchdog. Does nothing if a watcher is already waiting. */
        fun ensureScheduled(context: Context) {
            // The old single-watcher version used this name; remove it so it doesn't run twice
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
            enqueue(context, SLOT_A)
        }

        /** Arms the given slot unless it is already waiting or running (KEEP). */
        private fun enqueue(context: Context, slot: String) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(Duration.ofSeconds(1)) // wait for writes to settle
                .setTriggerContentMaxDelay(Duration.ofSeconds(5))    // but never longer than this
                .build()

            val request = OneTimeWorkRequestBuilder<NewScreenshotWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_SLOT to slot))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_PREFIX + slot,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
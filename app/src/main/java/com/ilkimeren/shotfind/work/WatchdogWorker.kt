package com.ilkimeren.shotfind.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.time.Duration

/**
 * Safety net that runs every ~15 minutes: re-arms the screenshot watcher if it died
 * and catches any screenshot it missed. Worst case, a screenshot is noticed late,
 * but never lost.
 */
class WatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        NewScreenshotWorker.ensureScheduled(applicationContext)
        try {
            NewScreenshotWorker.detectAndNotify(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ShotFindWatchdog", "Watchdog run failed", e)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            // 15 minutes is the shortest interval Android allows for periodic work
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(Duration.ofMinutes(15)).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "screenshot_watchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
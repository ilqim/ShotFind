package com.ilkimeren.shotfind.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilkimeren.shotfind.data.ScreenshotRepository
import com.ilkimeren.shotfind.util.ShotNotifications
import java.time.Duration

/** Once a day: if temporary screenshots have expired, asks whether to trash them. */
class TempCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = ScreenshotRepository(applicationContext)
        val expired = repository.findExpiredTemporary()
        if (expired.isNotEmpty()) {
            ShotNotifications.showTempExpired(
                applicationContext,
                expired.map { it.id },
                repository.firstTemporaryTagId()
            )
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TempCleanupWorker>(Duration.ofDays(1)).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "temp_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Runs the check right away; handy for testing. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<TempCleanupWorker>().build())
        }
    }
}
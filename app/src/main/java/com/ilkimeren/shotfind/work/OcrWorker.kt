package com.ilkimeren.shotfind.work

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilkimeren.shotfind.data.ShotFindDatabase
import com.ilkimeren.shotfind.ocr.OcrProcessor
import kotlinx.coroutines.CancellationException
import com.ilkimeren.shotfind.data.SearchNormalizer

/**
 * Runs OCR on screenshots that haven't been processed yet, in chunks.
 * If more are left after one chunk, it queues the next chunk.
 */
class OcrWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = ShotFindDatabase.getInstance(applicationContext).screenshotDao()
        val processor = OcrProcessor(applicationContext)

        try {
            var processed = 0
            while (processed < MAX_PER_RUN) {
                val batch = dao.getPendingOcr(BATCH_SIZE)
                if (batch.isEmpty()) break

                for (item in batch) {
                    val text = if (item.height > MAX_IMAGE_HEIGHT) {
                        null // very long scroll captures are skipped for now
                    } else {
                        try {
                            processor.recognize(item.uri.toUri())
                        } catch (e: CancellationException) {
                            throw e // the system stopped the work; let it stop
                        } catch (e: Exception) {
                            Log.w(TAG, "OCR failed for ${item.displayName}", e)
                            null
                        }
                    }
                    // Mark as done even on failure, so a broken file isn't retried forever
                    val searchText = SearchNormalizer.buildSearchText(text, item.note, item.sourceApp)
                    dao.saveOcrResult(item.mediaId, text, searchText)
                    processed++
                }
            }
        } finally {
            processor.close()
        }

        if (dao.countPendingOcr() > 0) enqueueNextChunk(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "OcrWorker"
        private const val WORK_NAME = "ocr"
        private const val BATCH_SIZE = 20
        private const val MAX_PER_RUN = 300
        private const val MAX_IMAGE_HEIGHT = 8000

        /** Starts OCR if it isn't already running. Safe to call often. */
        fun start(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<OcrWorker>().build()
            )
        }

        /** Queues another run to start right after the current one finishes. */
        private fun enqueueNextChunk(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<OcrWorker>().build()
            )
        }
    }
}
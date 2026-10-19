package com.ilkimeren.shotfind.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads the text inside an image with ML Kit, fully on device. */
class OcrProcessor(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Returns the recognized text, or null if the image has no text. */
    /** Returns the recognized text in reading order, or null if the image has no text. */
    suspend fun recognize(uri: Uri): String? {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()

        // The status bar (clock, notification and battery icons) is at the very top.
        // Its height is roughly proportional to the screen width.
        val statusBarBottom = (image.width * STATUS_BAR_HEIGHT_RATIO).toInt()

        val lines = result.textBlocks
            .flatMap { block -> block.lines }
            .filter { line ->
                val box = line.boundingBox ?: return@filter false
                val text = line.text.trim()
                box.top > statusBarBottom &&              // skip status bar
                        line.confidence >= MIN_CONFIDENCE &&  // skip shaky guesses (often icons)
                        text.length >= 2 &&                   // single letters are usually icons
                        text.any { it.isLetterOrDigit() }
            }
            // Top to bottom, then left to right, so chat bubbles keep their real order
            .sortedWith(compareBy({ it.boundingBox!!.top }, { it.boundingBox!!.left }))

        return lines.joinToString("\n") { it.text.trim() }.ifBlank { null }
    }

    /** Frees the model's memory. Call when done. */
    fun close() = recognizer.close()

    private companion object {
        const val STATUS_BAR_HEIGHT_RATIO = 0.085f // about 92 px on a 1080 px wide screen
        const val MIN_CONFIDENCE = 0.5f
    }
}

/** Turns a Google Play Services Task into a suspend call, like await in C#. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
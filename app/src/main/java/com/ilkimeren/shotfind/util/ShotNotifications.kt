package com.ilkimeren.shotfind.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ilkimeren.shotfind.MainActivity
import com.ilkimeren.shotfind.R
import com.ilkimeren.shotfind.data.Screenshot

object ShotNotifications {

    private const val CHANNEL_NEW_SCREENSHOTS = "new_screenshots"
    private const val SUMMARY_NOTIFICATION_ID = 1

    fun ensureChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_NEW_SCREENSHOTS,
            "Yeni ekran görüntüleri",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Yeni bir ekran görüntüsü aldığında not eklemen için"
        }
        // Safe to call many times; existing channels are left as they are
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked with canPost()
    fun showNewScreenshot(context: Context, screenshot: Screenshot) {
        if (!canPost(context)) return
        ensureChannels(context)

        val preview = loadPreview(context, screenshot.uri)
        val text = screenshot.sourceApp
            ?.let { "Kaynak: $it · Not eklemek için dokun" }
            ?: "Not eklemek için dokun"

        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_SCREENSHOTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Yeni ekran görüntüsü")
            .setContentText(text)
            .setLargeIcon(preview)
            .setStyle(
                preview?.let {
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(it)
                        .bigLargeIcon(null as Bitmap?) // hide the small preview when expanded
                }
            )
            .setContentIntent(openDetailIntent(context, screenshot.id))
            .setAutoCancel(true) // dismiss when tapped
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(screenshot.id), notification)
    }

    /** One notification instead of many, e.g. when several screenshots arrive at once. */
    @SuppressLint("MissingPermission") // checked with canPost()
    fun showSummary(context: Context, count: Int) {
        if (!canPost(context)) return
        ensureChannels(context)

        val openApp = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_SCREENSHOTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$count yeni ekran görüntüsü")
            .setContentText("Gelen kutusunda düzenlemek için dokun")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    /** A small, memory-friendly version of the image for the notification. */
    private fun loadPreview(context: Context, uri: Uri): Bitmap? =
        try {
            context.contentResolver.loadThumbnail(uri, Size(720, 720), null)
        } catch (e: Exception) {
            null
        }

    /** Opens the detail screen through the deep link shotfind://detail/{id}. */
    private fun openDetailIntent(context: Context, mediaId: Long): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "shotfind://detail/$mediaId".toUri(),
            context,
            MainActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        return PendingIntent.getActivity(
            context,
            notificationId(mediaId), // unique per screenshot so intents don't overwrite each other
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // Notification ids are Int; MediaStore ids are Long. Collisions are practically impossible here.
    private fun notificationId(mediaId: Long): Int = (mediaId % Int.MAX_VALUE).toInt() + 2
}
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
import androidx.core.app.RemoteInput
import com.ilkimeren.shotfind.notification.NoteReplyReceiver
import com.ilkimeren.shotfind.notification.TrashActivity
import com.ilkimeren.shotfind.data.TagEntity
import com.ilkimeren.shotfind.notification.MoveActivity
import com.ilkimeren.shotfind.notification.RestoreActivity
import com.ilkimeren.shotfind.notification.TempCleanupActivity

object ShotNotifications {

    private const val CHANNEL_NEW_SCREENSHOTS = "new_screenshots"
    private const val SUMMARY_NOTIFICATION_ID = 1

    private const val CHANNEL_CLEANUP = "temp_cleanup"
    private const val TEMP_CLEANUP_NOTIFICATION_ID = 2
    const val EXTRA_MEDIA_IDS = "media_ids"

    const val EXTRA_TAG_FOLDER = "tag_folder"
    const val EXTRA_TAG_NAME = "tag_name"
    private const val CHANNEL_CONFIRMATIONS = "action_confirmations"

    const val EXTRA_MEDIA_ID = "media_id"
    const val KEY_NOTE_REPLY = "note_reply"

    fun ensureChannels(context: Context) {
        val newScreenshots = NotificationChannel(
            CHANNEL_NEW_SCREENSHOTS,
            "Yeni ekran görüntüleri",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Yeni bir ekran görüntüsü aldığında not eklemen için"
        }

        // Pops up so the undo button is visible, but without sound or vibration
        val confirmations = NotificationChannel(
            CHANNEL_CONFIRMATIONS,
            "İşlem onayları",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Taşıma, çöpe atma ve not kaydetme onayları"
            setSound(null, null)
            enableVibration(false)
        }

        val cleanup = NotificationChannel(
            CHANNEL_CLEANUP,
            "Geçici temizlik",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Geçici etiketindeki ekran görüntülerinin süresi dolunca"
        }

        // Safe to call many times; existing channels are left as they are
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(newScreenshots, confirmations, cleanup))
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked with canPost()
    fun showNewScreenshot(context: Context, screenshot: Screenshot, suggestedTag: TagEntity?) {
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
            .setOnlyAlertOnce(true) // if the same screenshot is re-notified, don't pop up again
            .addAction(noteAction(context, screenshot.id))
            .apply { suggestedTag?.let { addAction(moveAction(context, screenshot.id, it)) } }
            .addAction(trashAction(context, screenshot.id))
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

    /** Replaces the screenshot's notification with a short confirmation. */
    @SuppressLint("MissingPermission") // checked with canPost()
    fun showNoteSaved(context: Context, mediaId: Long, note: String) {
        if (!canPost(context)) return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_CONFIRMATIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Not kaydedildi")
            .setContentText(note)
            .setContentIntent(openDetailIntent(context, mediaId))
            .setAutoCancel(true)
            .setTimeoutAfter(4_000) // disappears by itself
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(mediaId), notification)
    }

    /** Replaces the screenshot's notification after it was moved into a tag folder. */
    @SuppressLint("MissingPermission") // checked with canPost()
    fun showMoved(context: Context, mediaId: Long, tagName: String) {
        if (!canPost(context)) return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_CONFIRMATIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$tagName etiketine taşındı")
            .setContentIntent(openDetailIntent(context, mediaId))
            .setAutoCancel(true)
            .setTimeoutAfter(4_000)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(mediaId), notification)
    }

    /** Replaces the screenshot's notification after trashing, with an undo button. */
    @SuppressLint("MissingPermission") // checked with canPost()
    fun showTrashed(context: Context, mediaId: Long) {
        if (!canPost(context)) return
        ensureChannels(context)

        val restoreIntent = Intent(context, RestoreActivity::class.java)
            .putExtra(EXTRA_MEDIA_ID, mediaId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val restorePending = PendingIntent.getActivity(
            context,
            notificationId(mediaId),
            restoreIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CONFIRMATIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Çöp kutusuna taşındı")
            .setContentText("30 gün içinde ShotFind'dan geri alabilirsin")
            .addAction(R.drawable.ic_notification, "Geri al", restorePending)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000) // long enough to change your mind
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(mediaId), notification)
    }

    @SuppressLint("MissingPermission") // checked with canPost()
    fun showTempExpired(context: Context, mediaIds: List<Long>, albumId: Long?) {
        if (!canPost(context) || mediaIds.isEmpty()) return
        ensureChannels(context)

        val trashIntent = Intent(context, TempCleanupActivity::class.java)
            .putExtra(EXTRA_MEDIA_IDS, mediaIds.toLongArray())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val trashPending = PendingIntent.getActivity(
            context,
            TEMP_CLEANUP_NOTIFICATION_ID,
            trashIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Tapping the notification opens the temporary album to review
        val openIntent = if (albumId != null) {
            Intent(Intent.ACTION_VIEW, "shotfind://album/$albumId".toUri(), context, MainActivity::class.java)
        } else {
            Intent(context, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        val openPending = PendingIntent.getActivity(
            context,
            TEMP_CLEANUP_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANUP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${mediaIds.size} geçici ekran görüntüsünün süresi doldu")
            .setContentText("Çöpe atmak için dokun ya da gözden geçir")
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_notification, "Çöpe at", trashPending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(TEMP_CLEANUP_NOTIFICATION_ID, notification)
    }

    fun cancelTempExpired(context: Context) {
        NotificationManagerCompat.from(context).cancel(TEMP_CLEANUP_NOTIFICATION_ID)
    }

    private fun moveAction(context: Context, mediaId: Long, tag: TagEntity): NotificationCompat.Action {
        val intent = Intent(context, MoveActivity::class.java)
            .putExtra(EXTRA_MEDIA_ID, mediaId)
            .putExtra(EXTRA_TAG_FOLDER, tag.folderName)
            .putExtra(EXTRA_TAG_NAME, tag.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(mediaId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(R.drawable.ic_notification, tag.name, pendingIntent)
            .build()
    }

    fun cancel(context: Context, mediaId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(mediaId))
    }

    private fun noteAction(context: Context, mediaId: Long): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_NOTE_REPLY)
            .setLabel("Neden aldın?")
            .build()

        val intent = Intent(context, NoteReplyReceiver::class.java)
            .putExtra(EXTRA_MEDIA_ID, mediaId)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId(mediaId),
            intent,
            // Must be MUTABLE so the system can attach the typed text to the intent
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(R.drawable.ic_notification, "Not ekle", pendingIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false) // no auto-suggested replies like "OK"
            .build()
    }

    private fun trashAction(context: Context, mediaId: Long): NotificationCompat.Action {
        val intent = Intent(context, TrashActivity::class.java)
            .putExtra(EXTRA_MEDIA_ID, mediaId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(mediaId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(R.drawable.ic_notification, "Çöpe At", pendingIntent)
            .build()
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
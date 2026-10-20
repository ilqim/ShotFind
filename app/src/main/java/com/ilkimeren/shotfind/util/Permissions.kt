package com.ilkimeren.shotfind.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.os.PowerManager
import android.provider.MediaStore

/** The permission needed to read images on this Android version. */
fun imagePermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun hasImagePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, imagePermission()) ==
            PackageManager.PERMISSION_GRANTED

/** Opens this app's page in system Settings, for when the user denied the permission. */
fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** Media management + media location together let moves and trashing skip the dialog. */
fun hasMediaManagement(context: Context): Boolean =
    MediaStore.canManageMedia(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

/** True when the system won't delay our background work to save battery. */
fun isBatteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

fun openManageMediaSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_REQUEST_MANAGE_MEDIA,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
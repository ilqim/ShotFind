package com.ilkimeren.shotfind.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("ShotFind", text))
    // Android 13+ shows its own "Copied" confirmation, so no Toast here
}
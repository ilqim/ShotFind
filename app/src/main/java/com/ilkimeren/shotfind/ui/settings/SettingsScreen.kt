package com.ilkimeren.shotfind.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ilkimeren.shotfind.util.ShotNotifications
import com.ilkimeren.shotfind.util.hasImagePermission
import com.ilkimeren.shotfind.util.hasMediaManagement
import com.ilkimeren.shotfind.util.isBatteryUnrestricted
import com.ilkimeren.shotfind.util.openAppSettings
import com.ilkimeren.shotfind.util.openManageMediaSettings
import com.ilkimeren.shotfind.util.openNotificationSettings
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline

/** Snapshot of everything ShotFind needs from the system. */
private data class PermissionStatuses(
    val photos: Boolean,
    val notifications: Boolean,
    val mediaManagement: Boolean,
    val batteryUnrestricted: Boolean
) {
    companion object {
        fun read(context: Context) = PermissionStatuses(
            photos = hasImagePermission(context),
            notifications = ShotNotifications.canPost(context),
            mediaManagement = hasMediaManagement(context),
            batteryUnrestricted = isBatteryUnrestricted(context)
        )
    }
}

@Composable
fun SettingsScreen(
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var statuses by remember { mutableStateOf(PermissionStatuses.read(context)) }

    // Re-read when coming back from the system settings pages
    LifecycleResumeEffect(Unit) {
        statuses = PermissionStatuses.read(context)
        onPauseOrDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("İzinler ve arka plan", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ShotFind'ın düzgün çalışması için gerekenler. Eksik olana dokunarak açabilirsin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        PermissionRow(
            title = "Fotoğraflara erişim",
            description = "Ekran görüntülerini bulmak ve aramak için",
            granted = statuses.photos,
            onClick = { openAppSettings(context) }
        )
        PermissionRow(
            title = "Bildirimler",
            description = "Yeni bir ekran görüntüsü aldığında haber vermek için",
            granted = statuses.notifications,
            onClick = { openNotificationSettings(context) }
        )
        PermissionRow(
            title = "Medya yönetimi",
            description = "Taşırken ve çöpe atarken her seferinde onay sormaması için",
            granted = statuses.mediaManagement,
            onClick = { openManageMediaSettings(context) }
        )
        PermissionRow(
            title = "Pil kısıtlaması yok",
            description = "Bildirimlerin gecikmeden gelmesi için. Uygulama bilgisi > Pil > Kısıtlanmamış",
            granted = statuses.batteryUnrestricted,
            onClick = { openAppSettings(context) }
        )

        Spacer(Modifier.height(24.dp))
        Text("Depolama", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenTrash)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Silinenler", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Çöpe attığın ekran görüntülerini 30 gün içinde geri al",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = if (granted) "Açık" else "Kapalı",
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            TextButton(onClick = onClick) { Text("Aç") }
        }
    }
}
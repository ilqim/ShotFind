package com.ilkimeren.shotfind.ui.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.util.formatCaptureDate

private const val RETENTION_DAYS = 30
private const val DAY_MS = 24 * 60 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = viewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onRestoreResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel.restoreRequest) {
        viewModel.restoreRequest?.let { sender ->
            restoreLauncher.launch(IntentSenderRequest.Builder(sender).build())
            viewModel.onRestoreRequestLaunched()
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Silinenler") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                }
            },
            actions = {
                if (items.isNotEmpty()) {
                    TextButton(onClick = { viewModel.restore(items) }) {
                        Text("Tümünü geri al")
                    }
                }
            },
            windowInsets = WindowInsets(0)
        )

        if (viewModel.restoreFailed) {
            Text(
                text = "Geri alınamadı. Dosya kalıcı olarak silinmiş olabilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (items.isEmpty()) {
            EmptyTrash()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = "ShotFind ile çöpe attığın ekran görüntüleri $RETENTION_DAYS gün burada " +
                                "kalır, sonra Android onları kalıcı olarak siler. Galeri'nin çöp kutusunda görünmezler.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(items, key = { it.id }) { screenshot ->
                    TrashRow(
                        screenshot = screenshot,
                        onRestore = { viewModel.restore(listOf(screenshot)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashRow(screenshot: Screenshot, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = screenshot.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Android may hide trashed files from other apps; show an icon if so
            error = rememberVectorPainter(Icons.Outlined.BrokenImage),
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = screenshot.sourceApp ?: screenshot.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatCaptureDate(screenshot.capturedAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            screenshot.trashedAt?.let { trashedAt ->
                Text(
                    text = daysLeftText(trashedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        TextButton(onClick = onRestore) { Text("Geri al") }
    }
}

@Composable
private fun EmptyTrash() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Çöp kutusu boş",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

private fun daysLeftText(trashedAt: Long): String {
    val elapsedDays = (System.currentTimeMillis() - trashedAt) / DAY_MS
    val left = (RETENTION_DAYS - elapsedDays).toInt().coerceAtLeast(0)
    return if (left == 0) "Bugün kalıcı olarak silinecek" else "$left gün sonra kalıcı olarak silinecek"
}
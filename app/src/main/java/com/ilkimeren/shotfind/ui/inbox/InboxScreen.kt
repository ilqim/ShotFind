package com.ilkimeren.shotfind.ui.inbox

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.util.hasImagePermission
import com.ilkimeren.shotfind.util.imagePermission
import com.ilkimeren.shotfind.util.openAppSettings

@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(hasImagePermission(context)) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }

    // Shows the system "Allow access to photos?" dialog
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.loadScreenshots() else deniedOnce = true
    }

    // Runs every time the screen becomes visible: on start, and when coming back
    // from Settings or another app (so new screenshots show up)
    LifecycleResumeEffect(Unit) {
        hasPermission = hasImagePermission(context)
        if (hasPermission) viewModel.loadScreenshots()
        onPauseOrDispose { }
    }

    if (!hasPermission) {
        PermissionRequest(
            showSettingsButton = deniedOnce,
            onRequest = { permissionLauncher.launch(imagePermission()) },
            onOpenSettings = { openAppSettings(context) },
            modifier = modifier
        )
        return
    }

    when (val state = uiState) {
        InboxUiState.Loading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is InboxUiState.Error -> {
            Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(state.message, textAlign = TextAlign.Center)
            }
        }
        is InboxUiState.Loaded -> {
            ScreenshotGrid(
                screenshots = state.screenshots,
                totalCount = state.totalCount,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ScreenshotGrid(
    screenshots: List<Screenshot>,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    if (screenshots.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Gelen kutusu boş. Yeni bir ekran görüntüsü al, burada görünecek.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "${screenshots.size} ekran görüntüsü",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Veritabanında toplam $totalCount kayıt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(screenshots, key = { it.id }) { screenshot ->
                ScreenshotThumbnail(screenshot)
            }
        }
    }
}

@Composable
private fun ScreenshotThumbnail(screenshot: Screenshot) {
    Box(
        modifier = Modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = screenshot.uri,
            contentDescription = screenshot.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        screenshot.sourceApp?.let { app ->
            Text(
                text = app,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PermissionRequest(
    showSettingsButton: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Ekran görüntülerine erişim gerekli",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ShotFind, ekran görüntülerini bulup düzenleyebilmek için fotoğraflarına erişmeli. Hiçbir veri telefonundan çıkmaz.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text("İzin ver")
        }
        if (showSettingsButton) {
            TextButton(onClick = onOpenSettings) {
                Text("Ayarlardan aç")
            }
        }
    }
}
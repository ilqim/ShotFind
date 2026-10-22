package com.ilkimeren.shotfind.ui.sort

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.util.formatCaptureDate
import com.ilkimeren.shotfind.util.hasMediaManagement
import com.ilkimeren.shotfind.util.openManageMediaSettings
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SortViewModel = viewModel()
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var mediaManagement by remember { mutableStateOf(hasMediaManagement(context)) }
    LifecycleResumeEffect(Unit) {
        mediaManagement = hasMediaManagement(context)
        onPauseOrDispose { }
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onWriteRequestResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel.writeRequest) {
        viewModel.writeRequest?.let { sender ->
            writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
            viewModel.onWriteRequestLaunched()
        }
    }

    val top = cards?.firstOrNull()

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(cards?.let { "Ayıkla · ${it.size} kaldı" } ?: "Ayıkla") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                }
            },
            windowInsets = WindowInsets(0)
        )

        if (!mediaManagement) {
            Text(
                text = "Her işlemde onay sorulmaması için medya yönetimine izin ver",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { openManageMediaSettings(context) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // ---- Card stack ----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val list = cards
            when {
                list == null -> CircularProgressIndicator()
                list.isEmpty() -> AllSorted()
                else -> {
                    // Draw the next card behind the top one; the last drawn is on top
                    list.take(2).asReversed().forEach { screenshot ->
                        key(screenshot.id) {
                            val isTop = screenshot.id == list.first().id
                            SwipeCard(
                                screenshot = screenshot,
                                enabled = isTop,
                                onKeep = { viewModel.keep(screenshot) },
                                onTrash = { viewModel.trash(screenshot) },
                                onTag = { viewModel.openTagPicker(screenshot) },
                                modifier = if (isTop) Modifier else Modifier.graphicsLayer {
                                    scaleX = 0.94f
                                    scaleY = 0.94f
                                    alpha = 0.6f
                                }
                            )
                        }
                    }
                }
            }
        }

        // ---- Buttons (same actions as the gestures) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(Icons.Outlined.DeleteOutline, "Çöpe at", enabled = top != null) {
                top?.let(viewModel::trash)
            }
            ActionButton(Icons.Outlined.Sell, "Etiket", enabled = top != null) {
                top?.let(viewModel::openTagPicker)
            }
            ActionButton(Icons.Outlined.Check, "Sakla", enabled = top != null) {
                top?.let(viewModel::keep)
            }
        }
        TextButton(
            onClick = viewModel::undo,
            enabled = viewModel.canUndo,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Geri al")
        }
    }

    // ---- Tag picker ----
    viewModel.tagPickerFor?.let { screenshot ->
        ModalBottomSheet(onDismissRequest = viewModel::closeTagPicker) {
            Text(
                text = "Etiket seç",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            tags.forEach { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    leadingContent = {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(tag.colorArgb))
                        )
                    },
                    modifier = Modifier.clickable { viewModel.moveToTag(screenshot, tag.folderName) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwipeCard(
    screenshot: Screenshot,
    enabled: Boolean,
    onKeep: () -> Unit,
    onTrash: () -> Unit,
    onTag: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }   // how far to drag before it counts
    val flyDistance = with(density) { 700.dp.toPx() } // far enough to leave the screen

    fun springBack() {
        scope.launch { offsetX.animateTo(0f, spring()) }
        scope.launch { offsetY.animateTo(0f, spring()) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = offsetX.value / 30f // tilt while dragging sideways
            }
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(screenshot.id, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    },
                    onDragEnd = {
                        val x = offsetX.value
                        val y = offsetY.value
                        when {
                            x > threshold -> scope.launch {
                                offsetX.animateTo(flyDistance, tween(200))
                                onKeep()
                            }
                            x < -threshold -> scope.launch {
                                offsetX.animateTo(-flyDistance, tween(200))
                                onTrash()
                            }
                            y < -threshold && abs(y) > abs(x) -> {
                                springBack()
                                onTag()
                            }
                            else -> springBack()
                        }
                    },
                    onDragCancel = { springBack() }
                )
            }
    ) {
        AsyncImage(
            model = screenshot.uri,
            contentDescription = screenshot.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Stamps that fade in while dragging
        SwipeStamp("SAKLA", MaterialTheme.colorScheme.primary, offsetX.value / threshold, Alignment.TopStart)
        SwipeStamp("ÇÖPE AT", MaterialTheme.colorScheme.error, -offsetX.value / threshold, Alignment.TopEnd)
        SwipeStamp("ETİKET", MaterialTheme.colorScheme.secondary, -offsetY.value / threshold, Alignment.Center)

        Text(
            text = listOfNotNull(
                screenshot.sourceApp,
                formatCaptureDate(screenshot.capturedAtMillis)
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun BoxScope.SwipeStamp(text: String, color: Color, progress: Float, alignment: Alignment) {
    val alpha = progress.coerceIn(0f, 1f)
    if (alpha <= 0f) return
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .align(alignment)
            .padding(24.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .border(3.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AllSorted() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎉", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Gelen kutusu tertemiz",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}
package com.ilkimeren.shotfind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.ilkimeren.shotfind.data.Screenshot
import com.ilkimeren.shotfind.util.copyToClipboard
import com.ilkimeren.shotfind.util.formatCaptureDate
import com.ilkimeren.shotfind.util.openInGallery
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.platform.LocalFocusManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = viewModel()
) {
    val screenshot by viewModel.screenshot.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(screenshot?.let { formatCaptureDate(it.capturedAtMillis) } ?: "") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                }
            },
            actions = {
                screenshot?.let { s ->
                    IconButton(onClick = { openInGallery(context, s.uri) }) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Galeride aç")
                    }
                }
            },
            // The outer Scaffold already handles the status bar space
            windowInsets = WindowInsets(0)
        )

        val current = screenshot
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            DetailContent(
                screenshot = current,
                noteDraft = viewModel.noteDraft,
                noteSaved = viewModel.noteSaved,
                onNoteChange = viewModel::onNoteChange,
                onSaveNote = viewModel::saveNote
            )
        }
    }
}

@Composable
private fun DetailContent(
    screenshot: Screenshot,
    noteDraft: String?,
    noteSaved: Boolean,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val savedNote = screenshot.note ?: ""
    val noteText = noteDraft ?: savedNote

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AsyncImage(
            model = screenshot.uri,
            contentDescription = screenshot.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { openInGallery(context, screenshot.uri) }
        )

        screenshot.sourceApp?.let { app ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Kaynak: $app",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Note ----
        Spacer(Modifier.height(20.dp))
        Text("Not", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = noteText,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Bu ekran görüntüsünü neden aldın?") },
            minLines = 2
        )
        if (noteText.trim() != savedNote) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    focusManager.clearFocus() // closes the keyboard
                    onSaveNote()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Kaydet")
            }
        }

        if (noteSaved) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Not kaydedildi",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ---- Recognized text ----
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Tanınan metin",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            val text = screenshot.ocrText
            if (!text.isNullOrBlank()) {
                TextButton(onClick = { copyToClipboard(context, text) }) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Kopyala")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val text = screenshot.ocrText
        if (text.isNullOrBlank()) {
            Text(
                text = "Metin bulunamadı ya da henüz taranmadı.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Lets the user long-press and select part of the text
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
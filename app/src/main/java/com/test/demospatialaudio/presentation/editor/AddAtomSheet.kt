package com.test.demospatialaudio.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.test.demospatialaudio.audio.AudioLibraryEntry
import com.test.demospatialaudio.audio.AudioLibraryService
import com.test.demospatialaudio.presentation.canvas.CanvasViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAtomSheet(
    viewModel: CanvasViewModel,
    onDismiss: () -> Unit,
    onPickLibrary: (AudioLibraryEntry) -> Unit,
    onImportFile: () -> Unit,
    onAddUrl: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val builtIn = remember { AudioLibraryService.builtIn() }
    val imported = remember { AudioLibraryService.importedEntries(context) }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopAllPreviews() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Add an atom",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                "Tap to preview · pick one to drop into your space",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
            )
            Spacer8()

            ListItem(
                headlineContent = { Text("Import from device…") },
                supportingContent = { Text("WAV, MP3, OGG, M4A, FLAC") },
                leadingContent = { Icon(Icons.Default.CloudDownload, null) },
                modifier = Modifier.clickable(onClick = onImportFile)
            )

            ListItem(
                headlineContent = { Text("Pull from a link…") },
                supportingContent = { Text("Paste any direct audio URL") },
                leadingContent = { Icon(Icons.Default.Link, null) },
                modifier = Modifier.clickable(onClick = onAddUrl)
            )

            HorizontalDivider()

            if (imported.isNotEmpty()) {
                Text(
                    "Your library",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                imported.forEach { entry ->
                    AudioEntryItem(
                        entry = entry,
                        isPreviewing = viewModel.isPreviewing(entry),
                        onPreviewToggle = { viewModel.previewLibraryEntry(entry) },
                        onPick = { onPickLibrary(entry) }
                    )
                }
                HorizontalDivider()
            }

            Text(
                "Built-in atoms",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            builtIn.forEach { entry ->
                AudioEntryItem(
                    entry = entry,
                    isPreviewing = viewModel.isPreviewing(entry),
                    onPreviewToggle = { viewModel.previewLibraryEntry(entry) },
                    onPick = { onPickLibrary(entry) }
                )
            }
        }
    }
}

@Composable
private fun Spacer8() {
    Box(modifier = Modifier.size(8.dp))
}

@Composable
private fun AudioEntryItem(
    entry: AudioLibraryEntry,
    isPreviewing: Boolean,
    onPreviewToggle: () -> Unit,
    onPick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(entry.displayName) },
        supportingContent = {
            Text(
                if (isPreviewing) "Previewing — tap row to add" else entry.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPreviewing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPreviewing) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else Color.Transparent
                    )
                    .clickable(onClick = onPreviewToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPreviewing) "Stop preview" else "Preview",
                    tint = if (isPreviewing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        },
        trailingContent = {
            Icon(
                iconForEntry(entry),
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onPick),
        colors = ListItemDefaults.colors(
            containerColor = if (isPreviewing)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            else Color.Transparent
        )
    )
}

private fun iconForEntry(entry: AudioLibraryEntry): ImageVector {
    return when {
        entry.fileName.contains("rain", ignoreCase = true) -> Icons.Default.Cloud
        entry.fileName.contains("wind", ignoreCase = true) -> Icons.Default.Air
        entry.fileName.contains("fire", ignoreCase = true) -> Icons.Default.LocalFireDepartment
        else -> Icons.Default.Equalizer
    }
}

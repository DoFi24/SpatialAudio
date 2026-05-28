package com.test.demospatialaudio.presentation.recordings

import android.app.Application
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    val recordings = mutableStateListOf<File>()
    var playingFile by mutableStateOf<File?>(null)
        private set
    var progress by mutableStateOf(0f)
        private set
    var duration by mutableStateOf(0)
        private set

    private var player: MediaPlayer? = null

    init { refresh() }

    fun refresh() {
        val dir = File(getApplication<Application>().getExternalFilesDir(null), "Recordings")
        recordings.clear()
        dir.listFiles()
            ?.filter { it.extension.equals("wav", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.let { recordings.addAll(it) }
    }

    fun togglePlay(file: File) {
        if (playingFile == file) {
            stop()
            return
        }
        stop()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { stop() }
                start()
            }
            playingFile = file
            duration = player?.duration ?: 0
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        player?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
        player = null
        playingFile = null
        progress = 0f
    }

    fun tick() {
        val p = player ?: return
        if (!p.isPlaying) return
        val dur = duration.coerceAtLeast(1)
        progress = (p.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
    }

    fun delete(file: File) {
        if (playingFile == file) stop()
        try { file.delete() } catch (_: Exception) {}
        recordings.remove(file)
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onBack: () -> Unit,
    viewModel: RecordingsViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    LaunchedEffect(viewModel.playingFile) {
        while (viewModel.playingFile != null) {
            viewModel.tick()
            delay(150)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Captured spaces") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0E1A)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0D1424), Color(0xFF03050D))
                    )
                )
        ) {
            if (viewModel.recordings.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "Nothing captured yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        "Tap the record button on your soundscape to capture it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.recordings, key = { it.absolutePath }) { file ->
                        RecordingItem(
                            file = file,
                            isPlaying = viewModel.playingFile == file,
                            progress = if (viewModel.playingFile == file) viewModel.progress else 0f,
                            onPlayToggle = { viewModel.togglePlay(file) },
                            onShare = { shareRecording(context, file) },
                            onDelete = { viewModel.delete(file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingItem(
    file: File,
    isPlaying: Boolean,
    progress: Float,
    onPlayToggle: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val date = remember(file.lastModified()) {
        SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }
    val sizeKb = (file.length() / 1024L).coerceAtLeast(1)
    val sizeText = if (sizeKb > 1024) String.format("%.1f MB", sizeKb / 1024f) else "$sizeKb KB"

    Column {
        ListItem(
            headlineContent = { Text(file.nameWithoutExtension) },
            supportingContent = { Text("$date · $sizeText") },
            leadingContent = {
                IconButton(onClick = onPlayToggle) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, "Share")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        if (isPlaying && progress > 0f) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(2.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .size(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

private fun shareRecording(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Captured space")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share recording"))
    } catch (_: Exception) {}
}

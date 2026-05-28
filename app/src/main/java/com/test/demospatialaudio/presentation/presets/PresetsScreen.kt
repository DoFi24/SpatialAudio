package com.test.demospatialaudio.presentation.presets

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.test.demospatialaudio.DemoSpatialAudioApp
import com.test.demospatialaudio.domain.AppDatabase
import com.test.demospatialaudio.domain.SoundscapeTemplates
import com.test.demospatialaudio.domain.model.PresetWithAtoms
import com.test.demospatialaudio.domain.model.SoundscapePresetEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class PresetsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DemoSpatialAudioApp
    private val db = AppDatabase.getInstance(application)
    private val presetDao = db.presetDao()
    private val atomDao = db.atomDao()

    val playbackState = app.playback.state
    val presets = presetDao.getAllPresetsWithAtoms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenPresetIds = mutableStateMapOf<String, SoundscapePresetEntity>()
    var pendingUndoPresetId by mutableStateOf<String?>(null)
        private set
    var snackbarMessage by mutableStateOf<String?>(null)
        private set

    fun createPreset(name: String): String {
        val trimmed = name.trim()
        val finalName = trimmed.ifEmpty { "Untitled" }
        val entity = SoundscapePresetEntity(name = finalName)
        viewModelScope.launch {
            presetDao.insert(entity)
        }
        return entity.id
    }

    fun createFromTemplate(template: SoundscapeTemplates.Template): String {
        val (preset, atoms) = SoundscapeTemplates.materialize(template)
        viewModelScope.launch {
            presetDao.insert(preset)
            for (atom in atoms) atomDao.insert(atom)
        }
        return preset.id
    }

    fun renamePreset(preset: SoundscapePresetEntity, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            presetDao.update(preset.copy(name = trimmed))
        }
    }

    fun duplicatePreset(preset: SoundscapePresetEntity) {
        viewModelScope.launch {
            val copy = SoundscapePresetEntity(
                name = "${preset.name} ·",
                reverbPresetOrdinal = preset.reverbPresetOrdinal,
                reverbLevel = preset.reverbLevel,
                masterVolume = preset.masterVolume
            )
            presetDao.insert(copy)
            val atoms = atomDao.getAtomsForPreset(preset.id)
            for (atom in atoms) {
                atomDao.insert(atom.copy(
                    id = UUID.randomUUID().toString(),
                    presetId = copy.id
                ))
            }
            snackbarMessage = "Soundscape cloned"
        }
    }

    fun schedulePresetDelete(preset: SoundscapePresetEntity) {
        hiddenPresetIds[preset.id] = preset
        pendingUndoPresetId = preset.id
        viewModelScope.launch {
            delay(5500)
            if (hiddenPresetIds.remove(preset.id) != null) {
                presetDao.delete(preset)
            }
            if (pendingUndoPresetId == preset.id) pendingUndoPresetId = null
        }
        snackbarMessage = "Soundscape dissolved"
    }

    fun undoPresetDelete(presetId: String) {
        hiddenPresetIds.remove(presetId)
        if (pendingUndoPresetId == presetId) pendingUndoPresetId = null
    }

    fun clearSnackbar() {
        snackbarMessage = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsListScreen(
    onPresetClick: (String) -> Unit,
    onRecordingsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: PresetsViewModel = viewModel()
) {
    val presets by viewModel.presets.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val visiblePresets by remember(presets, viewModel.hiddenPresetIds) {
        derivedStateOf {
            presets.filter { it.preset.id !in viewModel.hiddenPresetIds }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renamingPreset by remember { mutableStateOf<SoundscapePresetEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.snackbarMessage) {
        val msg = viewModel.snackbarMessage ?: return@LaunchedEffect
        val undoId = viewModel.pendingUndoPresetId
        val result = if (undoId != null) {
            snackbarHostState.showSnackbar(msg, actionLabel = "Undo", duration = SnackbarDuration.Long)
        } else {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
        if (result == SnackbarResult.ActionPerformed && undoId != null) {
            viewModel.undoPresetDelete(undoId)
        }
        viewModel.clearSnackbar()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Atoms") },
                actions = {
                    IconButton(onClick = onRecordingsClick) {
                        Icon(Icons.Default.LibraryMusic, contentDescription = "Recordings")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0E1A)
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    newName = ""
                    showCreateDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "New") },
                text = { Text("New space") }
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
            Column(modifier = Modifier.fillMaxSize()) {
                if (playbackState.isPlaying) {
                    NowPlayingBar(
                        presetName = playbackState.presetName ?: "Playing",
                        isPaused = playbackState.isPaused,
                        onClick = {
                            playbackState.presetId?.let(onPresetClick)
                        }
                    )
                }

                if (visiblePresets.isEmpty()) {
                    EmptyState(
                        onTemplates = { showTemplates = true },
                        onCreate = {
                            newName = ""
                            showCreateDialog = true
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { showTemplates = true },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                            label = { Text("Templates") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visiblePresets, key = { it.preset.id }) { presetWithAtoms ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.schedulePresetDelete(presetWithAtoms.preset)
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Red.copy(alpha = 0.8f))
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = Color.White)
                                    }
                                },
                                enableDismissFromStartToEnd = false
                            ) {
                                PresetItem(
                                    presetWithAtoms = presetWithAtoms,
                                    isPlaying = playbackState.presetId == presetWithAtoms.preset.id && playbackState.isPlaying,
                                    onClick = { onPresetClick(presetWithAtoms.preset.id) },
                                    onRename = {
                                        renameText = presetWithAtoms.preset.name
                                        renamingPreset = presetWithAtoms.preset
                                    },
                                    onDuplicate = { viewModel.duplicatePreset(presetWithAtoms.preset) },
                                    onDelete = { viewModel.schedulePresetDelete(presetWithAtoms.preset) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Name your space") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("e.g. Late evening") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = viewModel.createPreset(newName)
                    showCreateDialog = false
                    onPresetClick(id)
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTemplates) {
        TemplatesSheet(
            onPick = { template ->
                showTemplates = false
                val id = viewModel.createFromTemplate(template)
                onPresetClick(id)
            },
            onDismiss = { showTemplates = false }
        )
    }

    renamingPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { renamingPreset = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renamePreset(preset, renameText)
                    renamingPreset = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingPreset = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyState(onTemplates: () -> Unit, onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text("Your spaces will live here", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "Start from a template, or build one atom at a time.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onTemplates,
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                label = { Text("Templates") }
            )
            AssistChip(
                onClick = onCreate,
                leadingIcon = { Icon(Icons.Default.Add, null) },
                label = { Text("Blank space") }
            )
        }
    }
}

@Composable
private fun NowPlayingBar(
    presetName: String,
    isPaused: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            if (isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isPaused) "Paused" else "Now playing",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(presetName, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
        Text(
            "Open",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatesSheet(
    onPick: (SoundscapeTemplates.Template) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                "Pick a starting space",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                "Open one and reshape it however you like",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            SoundscapeTemplates.all.forEach { t ->
                ListItem(
                    headlineContent = { Text(t.name) },
                    supportingContent = { Text(t.description) },
                    leadingContent = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable { onPick(t) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetItem(
    presetWithAtoms: PresetWithAtoms,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val preset = presetWithAtoms.preset
    val atomCount = presetWithAtoms.atoms.size
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                if (isPlaying) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                    )
                }
            }
        },
        supportingContent = {
            val atomLabel = when {
                atomCount == 0 -> "Empty space"
                atomCount == 1 -> "1 atom"
                else -> "$atomCount atoms"
            }
            Text(
                "$atomLabel · ${preset.reverbPreset.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface),
        colors = ListItemDefaults.colors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        trailingContent = {
            Column {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { showMenu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Clone") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { showMenu = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("Dissolve") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    )
}

package com.test.demospatialaudio.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.test.demospatialaudio.audio.CoordinateMapper
import com.test.demospatialaudio.domain.model.AtomAnimationMode
import com.test.demospatialaudio.domain.model.AudioAtomEntity
import com.test.demospatialaudio.presentation.canvas.CanvasViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtomEditorSheet(
    atom: AudioAtomEntity,
    viewModel: CanvasViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var nameDraft by remember(atom.id) { mutableStateOf(atom.displayName) }
    LaunchedEffect(nameDraft, atom.id) {
        if (nameDraft == atom.displayName) return@LaunchedEffect
        delay(400)
        viewModel.updateDisplayName(atom.id, nameDraft)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Tune this atom", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Where it lives, how it breathes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            SectionTitle("Name")
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                singleLine = true,
                placeholder = { Text("Atom") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle("Sound")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconToggleButton(
                    checked = !atom.isMuted,
                    onCheckedChange = { viewModel.toggleMute(atom.id) },
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                ) {
                    Icon(
                        if (atom.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (atom.isMuted) "Unmute" else "Mute"
                    )
                }

                FilterChip(
                    selected = viewModel.soloAtomId == atom.id,
                    onClick = { viewModel.toggleSolo(atom.id) },
                    label = { Text("Solo") }
                )

                if (viewModel.soloAtomId != null && viewModel.soloAtomId != atom.id) {
                    Text(
                        "Another atom is soloed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            SliderWithLabel(
                label = "Volume",
                value = atom.volume,
                onValueChange = { viewModel.updateVolume(atom.id, it) },
                valueRange = 0f..1f,
                valueText = "${(atom.volume * 100).toInt()}%"
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Elevation")

            SliderWithLabel(
                label = null,
                value = atom.z.toFloat(),
                onValueChange = {
                    viewModel.updateElevation(atom.id, it.toDouble())
                    viewModel.commitChanges()
                },
                valueRange = -1f..1f,
                valueText = String.format("%+.1f m above floor", atom.z * CoordinateMapper.ROOM_RADIUS)
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Reverb")

            SliderWithLabel(
                label = null,
                value = atom.reverbBlend,
                onValueChange = { viewModel.updateReverbBlend(atom.id, it) },
                valueRange = 0f..1f,
                valueText = "${(atom.reverbBlend * 100).toInt()}% in the room"
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Motion")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AtomAnimationMode.entries.forEach { mode ->
                    val selected = atom.animationMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.updateAnimation(atom.id, mode = mode) },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            if (atom.animationMode != AtomAnimationMode.NONE) {
                Spacer(Modifier.height(8.dp))
                SliderWithLabel(
                    label = "Speed",
                    value = atom.animationSpeed.toFloat(),
                    onValueChange = { viewModel.updateAnimation(atom.id, speed = it.toDouble()) },
                    valueRange = 0.02f..2f,
                    valueText = String.format("%.2f×", atom.animationSpeed)
                )

                if (atom.animationMode == AtomAnimationMode.PULSE || atom.animationMode == AtomAnimationMode.DRIFT) {
                    SliderWithLabel(
                        label = "Depth",
                        value = atom.animationDepth.toFloat(),
                        onValueChange = { viewModel.updateAnimation(atom.id, depth = it.toDouble()) },
                        valueRange = 0.05f..0.9f,
                        valueText = String.format("%.0f%%", atom.animationDepth * 100)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Source")
            Text(
                atom.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    viewModel.duplicateAtom(atom.id)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clone this atom")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dissolve atom")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Dissolve this atom?") },
            text = { Text("It will fade out and disappear from your space.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAtom(atom.id)
                    showDeleteConfirm = false
                    onDismiss()
                }) { Text("Dissolve", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SliderWithLabel(
    label: String?,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String
) {
    Column {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            valueText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

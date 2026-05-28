package com.test.demospatialaudio.presentation.canvas

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.test.demospatialaudio.audio.AtomAnimationDriver
import com.test.demospatialaudio.audio.CoordinateMapper
import com.test.demospatialaudio.domain.model.AtomAnimationMode
import com.test.demospatialaudio.domain.model.AudioAtomEntity
import com.test.demospatialaudio.domain.model.ReverbPreset
import com.test.demospatialaudio.presentation.editor.AddAtomSheet
import com.test.demospatialaudio.presentation.editor.AtomEditorSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    presetId: String,
    onBack: () -> Unit,
    viewModel: CanvasViewModel = viewModel()
) {
    var showAddAtom by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    var showReverbMenu by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showImportError by remember { mutableStateOf(false) }
    var showRecordingExitConfirm by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importAtom(it) }
    }

    LaunchedEffect(Unit) { viewModel.onAppear() }
    DisposableEffect(Unit) { onDispose { viewModel.onDisappear() } }
    LaunchedEffect(viewModel.importError) {
        showImportError = viewModel.importError != null
    }

    LaunchedEffect(viewModel.isRecording) {
        recordingSeconds = 0
        if (viewModel.isRecording) {
            while (true) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    LaunchedEffect(viewModel.snackbarMessage) {
        val msg = viewModel.snackbarMessage ?: return@LaunchedEffect
        val undoId = viewModel.pendingUndoAtomId
        val recording = viewModel.lastRecordingFile

        val (actionLabel, duration) = when {
            undoId != null -> "Undo" to SnackbarDuration.Long
            recording != null -> "Share" to SnackbarDuration.Long
            else -> null to SnackbarDuration.Short
        }
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = actionLabel,
            duration = duration
        )
        if (result == SnackbarResult.ActionPerformed) {
            when {
                undoId != null -> viewModel.undoRemoveAtom(undoId)
                recording != null -> {
                    viewModel.consumeLastRecording()?.let { shareWavFile(context, it) }
                }
            }
        }
        viewModel.clearSnackbar()
    }

    BackHandler(enabled = viewModel.isRecording) {
        showRecordingExitConfirm = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(viewModel.preset?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.isRecording) showRecordingExitConfirm = true
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.recenterHeading() }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Recenter heading")
                    }
                    Box {
                        IconButton(onClick = { showReverbMenu = true }) {
                            Icon(Icons.Default.Tune, "Room")
                        }
                        ReverbDropdownMenu(
                            expanded = showReverbMenu,
                            onDismiss = { showReverbMenu = false },
                            viewModel = viewModel
                        )
                    }
                    IconButton(onClick = { showAddAtom = true }) {
                        Icon(Icons.Default.Add, "Add atom")
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
        ) {
            CanvasContent(
                viewModel = viewModel,
                onAtomTap = { atomId ->
                    viewModel.selectAtom(atomId)
                    showEditor = true
                }
            )

            if (!viewModel.isEngineReady) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00BCD4),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.size(16.dp))
                    Text("Tuning the room…", color = Color.White.copy(alpha = 0.6f))
                }
            }

            if (viewModel.isEngineReady && viewModel.atoms.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Place your first sound",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap + and pick something to drift around you",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            MasterControlsOverlay(
                viewModel = viewModel,
                recordingSeconds = recordingSeconds,
                onSleepTimerClick = { showSleepSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }

    if (showAddAtom) {
        AddAtomSheet(
            viewModel = viewModel,
            onDismiss = {
                showAddAtom = false
                viewModel.stopAllPreviews()
            },
            onPickLibrary = { entry ->
                viewModel.addAtomFromLibrary(entry)
                viewModel.stopAllPreviews()
                showAddAtom = false
            },
            onImportFile = {
                viewModel.stopAllPreviews()
                showAddAtom = false
                filePickerLauncher.launch(arrayOf("audio/*"))
            },
            onAddUrl = {
                viewModel.stopAllPreviews()
                showAddAtom = false
                urlText = ""
                showUrlDialog = true
            }
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Add from link") },
            text = {
                Column {
                    Text(
                        "Paste a direct audio URL — stream, podcast clip, anything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        singleLine = true,
                        placeholder = { Text("https://…") },
                        modifier = Modifier.width(320.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addRemoteAtom(urlText)
                        showUrlDialog = false
                    },
                    enabled = urlText.isNotBlank()
                ) { Text("Drop it in") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditor) {
        viewModel.selectedAtom?.let { atom ->
            AtomEditorSheet(
                atom = atom,
                viewModel = viewModel,
                onDismiss = {
                    viewModel.commitChanges()
                    viewModel.selectAtom(null)
                    showEditor = false
                }
            )
        }
    }

    if (showImportError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportError(); showImportError = false },
            title = { Text("That didn't work") },
            text = { Text(viewModel.importError ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportError(); showImportError = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showRecordingExitConfirm) {
        AlertDialog(
            onDismissRequest = { showRecordingExitConfirm = false },
            title = { Text("Stop recording?") },
            text = { Text("You're still capturing this space.") },
            confirmButton = {
                TextButton(onClick = {
                    showRecordingExitConfirm = false
                    viewModel.toggleRecording()
                    onBack()
                }) { Text("Stop & leave") }
            },
            dismissButton = {
                TextButton(onClick = { showRecordingExitConfirm = false }) { Text("Keep going") }
            }
        )
    }

    if (showSleepSheet) {
        SleepTimerSheet(
            currentEndsAt = viewModel.sleepTimerEndsAt,
            onPick = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepSheet = false
            },
            onDismiss = { showSleepSheet = false }
        )
    }
}

@Composable
fun CanvasContent(viewModel: CanvasViewModel, onAtomTap: (String) -> Unit) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draggingAtomId by remember { mutableStateOf<String?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val view = LocalView.current

    val cyanColor = Color(0xFF00BCD4)
    val orangeColor = Color(0xFFFF9800)

    val dragScale by animateFloatAsState(
        targetValue = if (draggingAtomId != null) 1.3f else 1f,
        label = "dragScale"
    )

    val animatedYaw by animateFloatAsState(
        targetValue = viewModel.listenerYaw,
        animationSpec = tween(200, easing = LinearEasing),
        label = "yaw"
    )

    var conePulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(viewModel.recenterPulse) {
        if (viewModel.recenterPulse > 0f) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            conePulse = 1f
            val steps = 18
            repeat(steps) {
                delay(20)
                conePulse = 1f - (it + 1f) / steps
            }
            conePulse = 0f
        }
    }

    val radarClock = rememberInfiniteTransition(label = "radarClock")
    val sweepAngle by radarClock.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "sweep"
    )
    val glowPulse by radarClock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowPulse"
    )
    val spinnerAngle by radarClock.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinner"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1424),
                        Color(0xFF03050D)
                    )
                )
            )
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = canvasSize.width.toFloat()
                    val h = canvasSize.height.toFloat()

                    val hitAtom = findAtomAt(down.position, viewModel, w, h)
                    if (hitAtom != null) {
                        draggingAtomId = hitAtom.id
                        var wasDragged = false

                        drag(down.id) { change ->
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!wasDragged && (dx * dx + dy * dy) < 20f * 20f) {
                                change.consume()
                                return@drag
                            }
                            if (!wasDragged) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                            wasDragged = true
                            val normalized = CoordinateMapper.normalize(
                                change.position.x, change.position.y, w, h
                            )
                            viewModel.updatePosition(hitAtom.id, normalized.first, normalized.second)
                            change.consume()
                        }

                        draggingAtomId = null
                        if (wasDragged) {
                            viewModel.commitChanges()
                        } else {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onAtomTap(hitAtom.id)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val radarSize = min(w, h)

            for (ring in 1..4) {
                val opacity = 0.07f + ring * 0.01f
                val radius = radarSize * ring / 8f
                drawCircle(
                    color = cyanColor.copy(alpha = opacity),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }

            for (i in 0 until 8) {
                val angle = i * PI.toFloat() / 4f
                val endX = center.x + cos(angle) * radarSize / 2f
                val endY = center.y + sin(angle) * radarSize / 2f
                drawLine(
                    color = cyanColor.copy(alpha = 0.05f),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 0.5f
                )
            }

            val sweepReach = radarSize / 2f
            val sweepBase = Math.toRadians(sweepAngle.toDouble())
            val trailSteps = 26
            for (t in 0 until trailSteps) {
                val a = sweepBase - t * 0.045
                val alpha = (1f - t / trailSteps.toFloat()) * 0.14f
                drawLine(
                    color = cyanColor.copy(alpha = alpha),
                    start = center,
                    end = Offset(
                        center.x + cos(a).toFloat() * sweepReach,
                        center.y + sin(a).toFloat() * sweepReach
                    ),
                    strokeWidth = 2.5f
                )
            }
            drawLine(
                color = cyanColor.copy(alpha = 0.55f),
                start = center,
                end = Offset(
                    center.x + cos(sweepBase).toFloat() * sweepReach,
                    center.y + sin(sweepBase).toFloat() * sweepReach
                ),
                strokeWidth = 2f
            )

            // Orbit guide rings
            for (atom in viewModel.atoms) {
                if (atom.animationMode == AtomAnimationMode.ORBIT) {
                    val r = sqrt(atom.x * atom.x + atom.y * atom.y).coerceAtLeast(0.05) * (radarSize / 2f)
                    drawCircle(
                        color = cyanColor.copy(alpha = 0.18f),
                        radius = r.toFloat(),
                        center = center,
                        style = Stroke(
                            width = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    )
                }
            }

            // Motion trails
            for (atom in viewModel.atoms) {
                val trail = viewModel.positionTrail(atom.id) ?: continue
                if (trail.size < 2) continue
                val n = trail.size
                for ((i, pos) in trail.withIndex()) {
                    val p = CoordinateMapper.screenPoint(pos.x, pos.y, w, h)
                    val age = i.toFloat() / n
                    val alpha = age * 0.35f
                    val radius = 2f + age * 4f
                    drawCircle(
                        color = cyanColor.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(p.first, p.second)
                    )
                }
            }

            // Listener (centre) with rotating cone
            val coneAlpha = 0.5f + conePulse * 0.45f
            rotate(animatedYaw, center) {
                val coneLength = 70f + conePulse * 14f
                val coneHalfWidth = 28f
                val path = Path().apply {
                    moveTo(center.x, center.y - coneLength)
                    lineTo(center.x - coneHalfWidth, center.y + 14f)
                    lineTo(center.x + coneHalfWidth, center.y + 14f)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            orangeColor.copy(alpha = coneAlpha * 0.55f),
                            orangeColor.copy(alpha = 0f)
                        ),
                        startY = center.y - coneLength,
                        endY = center.y + 14f
                    )
                )
                drawPath(
                    path = path,
                    color = orangeColor.copy(alpha = coneAlpha * 0.85f),
                    style = Stroke(width = 1.3f)
                )
            }

            drawCircle(
                color = orangeColor.copy(alpha = 0.15f),
                radius = 40f,
                center = center
            )
            drawCircle(
                color = orangeColor.copy(alpha = 0.25f),
                radius = 28f,
                center = center
            )
            drawCircle(
                color = orangeColor,
                radius = 28f,
                center = center,
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = orangeColor,
                radius = 6f,
                center = center
            )

            for (atom in viewModel.atoms) {
                val isDragging = draggingAtomId == atom.id
                drawAtomNode(
                    atom = atom,
                    viewModel = viewModel,
                    canvasWidth = w,
                    canvasHeight = h,
                    isDragging = isDragging,
                    atomScale = if (isDragging) dragScale else 1f,
                    textMeasurer = textMeasurer,
                    cyanColor = cyanColor,
                    density = density.density,
                    isLoading = viewModel.isLoading(atom.id),
                    glowPulse = glowPulse,
                    spinnerAngle = spinnerAngle
                )
            }
        }
    }
}

private fun DrawScope.drawAtomNode(
    atom: AudioAtomEntity,
    viewModel: CanvasViewModel,
    canvasWidth: Float,
    canvasHeight: Float,
    isDragging: Boolean,
    atomScale: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    cyanColor: Color,
    density: Float,
    isLoading: Boolean,
    glowPulse: Float,
    spinnerAngle: Float
) {
    val live = viewModel.livePosition(atom)
    val screenPos = CoordinateMapper.screenPoint(
        live.first, live.second, canvasWidth, canvasHeight
    )
    val center = Offset(screenPos.first, screenPos.second)
    val dist = sqrt(live.first * live.first + live.second * live.second).toFloat()
    val level = viewModel.level(atom)
    val isSelected = viewModel.selectedAtomId == atom.id
    val isSolo = viewModel.soloAtomId == atom.id

    val normalized = dist.coerceIn(0f, 1f)
    val sizeScale = 1f - normalized * 0.45f
    val baseRadius = 28f * sizeScale * atomScale

    if (isLoading) {
        drawCircle(
            color = cyanColor.copy(alpha = 0.12f),
            radius = baseRadius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = baseRadius,
            center = center,
            style = Stroke(width = 1.5f)
        )
        val ringRadius = baseRadius + 7f
        drawArc(
            color = cyanColor,
            startAngle = spinnerAngle,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
        drawAtomLabel(atom, center, baseRadius, textMeasurer, density)
        return
    }

    val fillColor = if (atom.isMuted) Color.Gray
    else {
        val e = live.third.coerceIn(-1.0, 1.0).toFloat()
        if (e >= 0) Color(
            red = 0.4f * (1 - e) + 0.2f * e,
            green = 0.85f,
            blue = 1f
        )
        else Color(
            red = 1f,
            green = 0.7f * (1 + e) + 0.4f * (-e),
            blue = 0.4f
        )
    }

    val fillAlpha = if (atom.isMuted) 0.2f
    else 0.55f + (1f - normalized) * 0.35f

    if (isDragging) {
        drawCircle(
            color = fillColor.copy(alpha = 0.1f),
            radius = baseRadius * 1.6f,
            center = center
        )
    }

    if (atom.animationMode != AtomAnimationMode.NONE) {
        drawCircle(
            color = cyanColor.copy(alpha = 0.4f),
            radius = baseRadius + 5f,
            center = center,
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
        )
    }

    if (!atom.isMuted) {
        val levelClamped = (level * 4f).coerceIn(0f, 1f)
        val levelRingScale = 1f + levelClamped * 0.35f
        drawCircle(
            color = fillColor.copy(alpha = 0.55f),
            radius = baseRadius * levelRingScale,
            center = center,
            style = Stroke(width = 2f)
        )
    }

    if (!atom.isMuted) {
        val levelClamped = (level * 4f).coerceIn(0f, 1f)
        val glowRadius = baseRadius * (1.9f + glowPulse * 0.3f + levelClamped * 0.6f)
        val glowStrength = (0.22f + levelClamped * 0.3f + glowPulse * 0.08f).coerceAtMost(0.6f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(fillColor.copy(alpha = glowStrength), Color.Transparent),
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center
        )
    }

    drawCircle(
        color = fillColor.copy(alpha = fillAlpha),
        radius = baseRadius,
        center = center
    )

    val strokeWidth = if (isSelected) 3f else 2f
    val strokeAlpha = if (isSelected) 1f else 0.7f
    drawCircle(
        color = Color.White.copy(alpha = strokeAlpha),
        radius = baseRadius,
        center = center,
        style = Stroke(width = strokeWidth)
    )

    if (isSolo) {
        drawCircle(
            color = Color.Yellow,
            radius = baseRadius + 9f,
            center = center,
            style = Stroke(width = 2f)
        )
    }

    if (atom.isMuted) {
        val s = baseRadius * 0.3f
        drawLine(
            Color.White.copy(alpha = 0.7f),
            Offset(center.x - s, center.y - s),
            Offset(center.x + s, center.y + s),
            strokeWidth = 2.5f
        )
        drawLine(
            Color.White.copy(alpha = 0.7f),
            Offset(center.x + s, center.y - s),
            Offset(center.x - s, center.y + s),
            strokeWidth = 2.5f
        )
    } else {
        val barW = baseRadius * 0.1f
        val spacing = baseRadius * 0.2f
        val heights = floatArrayOf(0.35f, 0.6f, 0.45f)
        for (i in heights.indices) {
            val barH = baseRadius * heights[i]
            val x = center.x + (i - 1) * spacing
            drawLine(
                Color.White.copy(alpha = 0.85f),
                Offset(x, center.y - barH / 2),
                Offset(x, center.y + barH / 2),
                strokeWidth = barW
            )
        }
    }

    drawAtomLabel(atom, center, baseRadius, textMeasurer, density)
}

private fun DrawScope.drawAtomLabel(
    atom: AudioAtomEntity,
    center: Offset,
    baseRadius: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    density: Float
) {
    val labelStyle = TextStyle(
        color = Color.White,
        fontSize = 10.sp,
        textAlign = TextAlign.Center
    )
    val textLayout = textMeasurer.measure(
        atom.displayName,
        style = labelStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (80 * density).toInt())
    )
    drawText(
        textLayout,
        topLeft = Offset(
            center.x - textLayout.size.width / 2f,
            center.y + baseRadius + 6f
        )
    )
}

private fun findAtomAt(
    position: Offset,
    viewModel: CanvasViewModel,
    width: Float,
    height: Float
): AudioAtomEntity? {
    val threshold = 60f
    var closest: AudioAtomEntity? = null
    var closestDist = Float.MAX_VALUE

    for (atom in viewModel.atoms) {
        val live = viewModel.livePosition(atom)
        val sp = CoordinateMapper.screenPoint(live.first, live.second, width, height)
        val dx = position.x - sp.first
        val dy = position.y - sp.second
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < threshold && dist < closestDist) {
            closest = atom
            closestDist = dist
        }
    }
    return closest
}

@Composable
private fun MasterControlsOverlay(
    viewModel: CanvasViewModel,
    recordingSeconds: Int,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        OutputMeter(viewModel = viewModel)
        Spacer(Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = { viewModel.toggleMasterPause() }, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (viewModel.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (viewModel.isPaused) "Resume" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Icon(
                Icons.Default.VolumeDown, null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )

            Slider(
                value = viewModel.preset?.masterVolume ?: 1f,
                onValueChange = { viewModel.updateMasterVolume(it) },
                valueRange = 0f..1f,
                modifier = Modifier.width(120.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00BCD4),
                    activeTrackColor = Color(0xFF00BCD4)
                )
            )

            Icon(
                Icons.Default.VolumeUp, null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(6.dp))

            IconButton(
                onClick = onSleepTimerClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Bedtime,
                    contentDescription = "Sleep timer",
                    tint = if (viewModel.sleepTimerEndsAt != null) Color(0xFFFF9800)
                    else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { viewModel.toggleRecording() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (viewModel.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (viewModel.isRecording) "Stop recording" else "Record",
                    tint = if (viewModel.isRecording) Color.Red else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (viewModel.isRecording) {
                val minutes = recordingSeconds / 60
                val seconds = recordingSeconds % 60
                Text(
                    String.format("%02d:%02d", minutes, seconds),
                    color = Color.Red,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                )
            }
        }

        viewModel.sleepTimerEndsAt?.let { endsAt ->
            var remaining by remember(endsAt) { mutableStateOf(remainingMillis(endsAt)) }
            LaunchedEffect(endsAt) {
                while (remaining > 0) {
                    delay(1000)
                    remaining = remainingMillis(endsAt)
                }
            }
            val mins = (remaining / 60_000L).toInt()
            val secs = ((remaining / 1000L) % 60).toInt()
            Spacer(Modifier.height(6.dp))
            Text(
                String.format("Drifting off in %d:%02d", mins, secs),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF9800).copy(alpha = 0.85f)
            )
        }
    }
}

private fun remainingMillis(endsAt: Long): Long {
    return (endsAt - System.currentTimeMillis()).coerceAtLeast(0)
}

@Composable
private fun OutputMeter(viewModel: CanvasViewModel) {
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            val target = viewModel.outputLevel().coerceIn(0f, 1f)
            level = level * 0.65f + target * 0.35f
        }
    }
    val barWidth = 160.dp
    Box(
        modifier = Modifier
            .width(barWidth)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.1f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(level.coerceIn(0f, 1f))
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00BCD4),
                            Color(0xFFFF9800)
                        )
                    )
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    currentEndsAt: Long?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
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
                "Drift off in…",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Text(
                "Your space will fade quietly to silence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            listOf(15, 30, 45, 60, 90).forEach { mins ->
                ListItem(
                    headlineContent = { Text("$mins minutes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(mins) }
                )
            }
            if (currentEndsAt != null) {
                ListItem(
                    headlineContent = { Text("Cancel timer") },
                    modifier = Modifier.clickable { onPick(null) }
                )
            }
        }
    }
}

private fun shareWavFile(context: android.content.Context, file: File) {
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
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun ReverbDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: CanvasViewModel
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ReverbPreset.entries.forEach { preset ->
            val selected = viewModel.preset?.reverbPreset == preset
            DropdownMenuItem(
                text = {
                    Text(
                        preset.displayName,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                },
                trailingIcon = if (selected) {
                    { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
                onClick = {
                    viewModel.applyReverb(preset)
                    onDismiss()
                }
            )
        }
    }
}

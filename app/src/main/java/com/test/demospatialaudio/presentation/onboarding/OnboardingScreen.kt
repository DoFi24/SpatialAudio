package com.test.demospatialaudio.presentation.onboarding

import android.app.Application
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.test.demospatialaudio.DemoSpatialAudioApp
import com.test.demospatialaudio.audio.AtomDescriptor
import com.test.demospatialaudio.domain.model.ReverbPreset
import com.test.demospatialaudio.presentation.theme.LocalRadarPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DemoSpatialAudioApp
    private val controller = app.playback

    val engine get() = controller.engine
    val headTracking get() = controller.headTracking

    var step by mutableIntStateOf(0)
        private set
    var isDemoPlaying by mutableStateOf(false)
        private set
    var listenerYaw by mutableFloatStateOf(0f)
        private set

    fun next() { step++ }

    fun startDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            engine.stop()
            controller.bindPreset("__onboarding__", "First listen")
            engine.start()
            engine.applyReverb(ReverbPreset.LARGE_HALL, -8f)
            engine.setMasterVolume(0f)

            val demoAtoms = listOf(
                AtomDescriptor("rain", "audio/rain.mp3", -0.65f, -0.3f, 0f, 0.6f, false, 0.4f),
                AtomDescriptor("wind", "audio/wind.mp3", 0.6f, 0.35f, 0f, 0.5f, false, 0.5f),
                AtomDescriptor("fire", "audio/fire.mp3", 0.0f, 0.6f, 0f, 0.7f, false, 0.2f)
            )
            engine.reset(demoAtoms)
            controller.onPlaybackStarted()

            launch(Dispatchers.Main) {
                headTracking.start()
                isDemoPlaying = true
                pollYaw()
            }
            fadeIn()
        }
    }

    private suspend fun fadeIn() {
        val steps = 30
        for (i in 1..steps) {
            engine.setMasterVolume(i / steps.toFloat() * 0.8f)
            delay(40)
        }
    }

    private fun pollYaw() {
        viewModelScope.launch {
            while (isDemoPlaying) {
                delay(50)
                // sample engine indirectly — yaw is set inside engine
                // For UI animation, we'll just animate based on time
            }
        }
    }

    fun stopDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            isDemoPlaying = false
            headTracking.stop()
            val steps = 12
            for (i in steps downTo 1) {
                engine.setMasterVolume(i / steps.toFloat() * 0.4f)
                delay(40)
            }
            engine.stop()
            controller.onPlaybackStopped()
        }
    }

    fun finish() {
        stopDemo()
        viewModelScope.launch {
            app.preferences.setOnboardingDone(true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDemo()
    }
}

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val palette = LocalRadarPalette.current
    DisposableEffect(Unit) { onDispose { viewModel.stopDemo() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.background, Color.Black)
                )
            )
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        when (viewModel.step) {
            0 -> StepWelcome(
                onContinue = { viewModel.next() }
            )
            1 -> StepListen(
                isPlaying = viewModel.isDemoPlaying,
                onStart = { viewModel.startDemo() },
                onContinue = { viewModel.next() }
            )
            else -> StepDone(
                onTemplates = {
                    viewModel.finish()
                    onDone()
                },
                onBlank = {
                    viewModel.finish()
                    onDone()
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.StepWelcome(onContinue: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    OrbitMark()
    Spacer(Modifier.height(40.dp))
    Text(
        "Atoms",
        style = MaterialTheme.typography.displaySmall,
        color = Color.White,
        fontWeight = FontWeight.Light
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Build a soundscape around your head.\nMove sounds in space. Turn — they stay put.",
        color = Color.White.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(40.dp))
    Text(
        "Headphones make the magic real.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onContinue,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Begin", fontSize = 16.sp)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ColumnScope.StepListen(
    isPlaying: Boolean,
    onStart: () -> Unit,
    onContinue: () -> Unit
) {
    Spacer(Modifier.height(40.dp))
    DemoRadar(isPlaying = isPlaying)
    Spacer(Modifier.height(40.dp))
    Text(
        if (isPlaying) "Now turn your head." else "Press play.\nListen with both ears.",
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        if (isPlaying)
            "Rain on your left. Wind on your right. Fire ahead."
        else
            "Three atoms will appear around you.",
        color = Color.White.copy(alpha = 0.65f),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.weight(1f))
    if (!isPlaying) {
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Listen", fontSize = 16.sp) }
    } else {
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Continue", fontSize = 16.sp) }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ColumnScope.StepDone(onTemplates: () -> Unit, onBlank: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    OrbitMark()
    Spacer(Modifier.height(40.dp))
    Text(
        "Your space awaits",
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Start from a template or build from silence.",
        color = Color.White.copy(alpha = 0.65f),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onTemplates,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text("Show me", fontSize = 16.sp) }
    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = onBlank,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Start blank", color = Color.White.copy(alpha = 0.65f)) }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun OrbitMark() {
    val palette = LocalRadarPalette.current
    val infinite = rememberInfiniteTransition(label = "orbit-mark")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "rot"
    )
    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = min(size.width, size.height) / 2f - 4f
            drawCircle(
                color = palette.primary.copy(alpha = 0.2f),
                radius = r,
                center = center,
                style = Stroke(width = 1.5f)
            )
            rotate(rotation, center) {
                drawCircle(
                    color = palette.primary,
                    radius = 8f,
                    center = Offset(center.x + r, center.y)
                )
            }
            rotate(-rotation * 0.6f, center) {
                drawCircle(
                    color = palette.accent,
                    radius = 6f,
                    center = Offset(center.x - r * 0.7f, center.y)
                )
            }
            drawCircle(
                color = palette.accent,
                radius = 14f,
                center = center
            )
            drawCircle(
                color = palette.accent.copy(alpha = 0.2f),
                radius = 28f,
                center = center
            )
        }
    }
}

@Composable
private fun DemoRadar(isPlaying: Boolean) {
    val palette = LocalRadarPalette.current
    val infinite = rememberInfiniteTransition(label = "demo-radar")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = min(size.width, size.height) / 2f - 2f

            for (i in 1..3) {
                drawCircle(
                    color = palette.primary.copy(alpha = 0.08f + i * 0.02f),
                    radius = maxR * i / 4f,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }

            val sweepRad = Math.toRadians(sweep.toDouble())
            drawLine(
                color = palette.primary.copy(alpha = 0.6f),
                start = center,
                end = Offset(
                    center.x + cos(sweepRad).toFloat() * maxR,
                    center.y + sin(sweepRad).toFloat() * maxR
                ),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )

            // Three atoms (rain left, wind right, fire ahead)
            val atomPositions = listOf(
                Offset(-0.65f, -0.3f),
                Offset(0.6f, 0.35f),
                Offset(0f, 0.6f)
            )
            val atomColors = listOf(
                Color(0xFF80DEEA),
                Color(0xFFCE93D8),
                Color(0xFFFFB74D)
            )
            for ((i, p) in atomPositions.withIndex()) {
                val pos = Offset(center.x + p.x * maxR * 0.85f, center.y - p.y * maxR * 0.85f)
                val glow = (if (isPlaying) 1f else 0.3f) * (0.6f + pulse * 0.4f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(atomColors[i].copy(alpha = 0.4f * glow), Color.Transparent),
                        center = pos,
                        radius = 40f * glow
                    ),
                    radius = 40f * glow,
                    center = pos
                )
                drawCircle(
                    color = atomColors[i].copy(alpha = if (isPlaying) 0.9f else 0.4f),
                    radius = 10f,
                    center = pos
                )
            }

            // Centre listener (cone if playing)
            if (isPlaying) {
                val cone = Path().apply {
                    moveTo(center.x, center.y - 28f)
                    lineTo(center.x - 14f, center.y + 8f)
                    lineTo(center.x + 14f, center.y + 8f)
                    close()
                }
                drawPath(cone, color = palette.accent.copy(alpha = 0.6f))
            }
            drawCircle(palette.accent, radius = 6f, center = center)
        }
    }
}

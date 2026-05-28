package com.test.demospatialaudio.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.test.demospatialaudio.DemoSpatialAudioApp

data class RadarPalette(
    val name: String,
    val primary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color
)

object RadarPalettes {
    val Cyan = RadarPalette(
        name = "Cyan night",
        primary = Color(0xFF00BCD4),
        accent = Color(0xFFFF9800),
        background = Color(0xFF0A0E1A),
        surface = Color(0xFF141A2B)
    )
    val Amber = RadarPalette(
        name = "Amber dusk",
        primary = Color(0xFFFFB74D),
        accent = Color(0xFFE91E63),
        background = Color(0xFF13100A),
        surface = Color(0xFF221A11)
    )
    val Magenta = RadarPalette(
        name = "Cosmos",
        primary = Color(0xFFE040FB),
        accent = Color(0xFF40C4FF),
        background = Color(0xFF0E0A1A),
        surface = Color(0xFF1B1530)
    )
    val Forest = RadarPalette(
        name = "Forest",
        primary = Color(0xFF80E27E),
        accent = Color(0xFFFFD54F),
        background = Color(0xFF0A150E),
        surface = Color(0xFF14241A)
    )
    val all = listOf(Cyan, Amber, Magenta, Forest)
}

val LocalRadarPalette = staticCompositionLocalOf { RadarPalettes.Cyan }

@Composable
fun SpatialAudioTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? DemoSpatialAudioApp
    val themeIndex by (app?.preferences?.themeIndex
        ?: kotlinx.coroutines.flow.flowOf(0)).collectAsState(initial = 0)
    val palette = RadarPalettes.all.getOrElse(themeIndex) { RadarPalettes.Cyan }

    val scheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = Color.White,
        secondary = palette.accent,
        onSecondary = Color.Black,
        background = palette.background,
        onBackground = Color.White,
        surface = palette.surface,
        onSurface = Color.White,
        surfaceVariant = palette.surface,
        onSurfaceVariant = Color(0xFFB0BEC5),
        error = Color(0xFFCF6679),
        onError = Color.Black
    )

    CompositionLocalProvider(LocalRadarPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}

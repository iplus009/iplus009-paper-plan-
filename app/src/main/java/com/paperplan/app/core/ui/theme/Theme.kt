package com.paperplan.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PaperColorScheme = darkColorScheme(
    primary = PaperViolet,
    onPrimary = Color(0xFF140F29),
    secondary = PaperMint,
    onSecondary = Color(0xFF06211B),
    background = PaperBackground,
    onBackground = PaperText,
    surface = PaperSurface,
    onSurface = PaperText,
    surfaceVariant = PaperSurfaceElevated,
    onSurfaceVariant = PaperMuted,
    error = PaperDanger,
    onError = Color.White,
    outline = PaperGlassBorder
)

@Composable
fun PaperPlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColorScheme,
        content = content
    )
}

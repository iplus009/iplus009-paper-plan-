package com.paperplan.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PaperColorScheme = darkColorScheme(
    primary = PaperViolet,
    secondary = PaperMint,
    background = PaperBackground,
    surface = PaperSurface,
    surfaceVariant = PaperSurfaceElevated,
    onBackground = PaperText,
    onSurface = PaperText
)

@Composable
fun PaperPlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColorScheme,
        content = content
    )
}

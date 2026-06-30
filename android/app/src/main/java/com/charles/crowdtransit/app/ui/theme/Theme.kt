package com.charles.crowdtransit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    background = SurfaceDark,
    surface = Surface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceSecondary,
    error = Error,
    tertiary = Success,
)

@Composable
fun CrowdTransitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = CrowdTransitTypography,
        shapes = CrowdTransitShapes,
        content = content,
    )
}

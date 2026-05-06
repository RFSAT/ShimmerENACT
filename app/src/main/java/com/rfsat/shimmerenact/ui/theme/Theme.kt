package com.rfsat.shimmerenact.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val DarkColorScheme = darkColorScheme(
    primary          = EnactGreen,
    onPrimary        = EnactDark,
    primaryContainer = EnactDarkMid,
    secondary        = EnactLime,
    onSecondary      = EnactDark,
    background       = EnactDark,
    onBackground     = EnactOnSurface,
    surface          = EnactSurface,
    onSurface        = EnactOnSurface,
    surfaceVariant   = EnactSurfaceVar,
    onSurfaceVariant = EnactOnSurface,
    error            = EnactError,
    outline          = EnactGreen.copy(alpha = 0.4f)
)

@Composable
fun ShimmerENACTTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}

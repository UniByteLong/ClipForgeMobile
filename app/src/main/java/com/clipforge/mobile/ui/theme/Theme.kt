package com.clipforge.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GraphiteBg = Color(0xFF0B0B0F)
val SurfaceDark = Color(0xFF14141B)
val BorderDark = Color(0xFF262633)
val AccentPurple = Color(0xFF8B5CF6)
val TextPrimary = Color(0xFFF4F4F6)
val TextSecondary = Color(0xFF9CA3AF)

private val DarkColors = darkColorScheme(
    primary = AccentPurple,
    background = GraphiteBg,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderDark
)

private val LightColors = lightColorScheme(
    primary = AccentPurple,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    outline = Color(0xFFE5E7EB)
)

@Composable
fun ClipForgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

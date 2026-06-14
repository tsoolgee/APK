package com.musicplayer.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Palette ───────────────────────────────────────────────────────────────────
// Deep midnight base with electric violet accent and soft cyan highlights

val Background       = Color(0xFF0D0D14)
val SurfaceDeep      = Color(0xFF13131F)
val SurfaceCard      = Color(0xFF1A1A2E)
val SurfaceElevated  = Color(0xFF22223A)
val SurfaceBorder    = Color(0xFF2A2A42)

val AccentViolet     = Color(0xFF7B61FF)   // primary CTA
val AccentVioletDim  = Color(0xFF4A3A99)
val AccentCyan       = Color(0xFF00D4FF)   // playing indicator / progress
val AccentGlow       = Color(0xFF7B61FF).copy(alpha = 0.25f)

val TextPrimary      = Color(0xFFF0EEF8)
val TextSecondary    = Color(0xFF8A88A0)
val TextTertiary     = Color(0xFF555370)

val FavoriteRed      = Color(0xFFFF4D6D)
val SuccessGreen     = Color(0xFF4DFF91)

// ── Typography ────────────────────────────────────────────────────────────────

val MusicTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = TextSecondary
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = TextTertiary
    )
)

// ── Color scheme ──────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary          = AccentViolet,
    onPrimary        = TextPrimary,
    primaryContainer = AccentVioletDim,
    secondary        = AccentCyan,
    background       = Background,
    surface          = SurfaceDeep,
    surfaceVariant   = SurfaceCard,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline          = SurfaceBorder,
    error            = FavoriteRed
)

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MusicTypography,
        content = content
    )
}

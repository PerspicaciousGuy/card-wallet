package com.cardwallet.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The only file in the app where raw color values are legal (AGENTS.md §6).

val Indigo600 = Color(0xFF4F46E5)
val Indigo400 = Color(0xFF818CF8)
val Indigo100 = Color(0xFFE0E7FF)
val Violet500 = Color(0xFF8B5CF6)
val Slate950 = Color(0xFF0B0F1A)
val Slate900 = Color(0xFF111827)
val Slate800 = Color(0xFF1F2937)
val Slate200 = Color(0xFFE5E7EB)
val Slate100 = Color(0xFFF3F4F6)
val Slate50 = Color(0xFFF9FAFB)
val Red500 = Color(0xFFEF4444)
val Red300 = Color(0xFFFCA5A5)

/** Accent used by the liquid-glass navbar's hidden reveal layer. */
val GlassAccentLight = Color(0xFF0066FF)
val GlassAccentDark = Color(0xFF3D8BFF)

/** Readability scrim the glass bar draws over its refracted backdrop. */
val GlassContainerLight = Color(0xFFFAFAFA).copy(alpha = 0.4f)
val GlassContainerDark = Color(0xFF121212).copy(alpha = 0.4f)

/** Home backdrop gradient — gives the glass something to refract. */
val BackdropTopLight = Indigo100
val BackdropBottomLight = Slate50
val BackdropTopDark = Slate900
val BackdropBottomDark = Slate950

val LightColorScheme =
    lightColorScheme(
        primary = Indigo600,
        onPrimary = Color.White,
        primaryContainer = Indigo100,
        onPrimaryContainer = Slate900,
        secondary = Violet500,
        onSecondary = Color.White,
        background = Slate50,
        onBackground = Slate900,
        surface = Slate100,
        onSurface = Slate900,
        surfaceVariant = Slate200,
        onSurfaceVariant = Slate800,
        error = Red500,
        onError = Color.White,
    )

val DarkColorScheme =
    darkColorScheme(
        primary = Indigo400,
        onPrimary = Slate950,
        primaryContainer = Slate800,
        onPrimaryContainer = Indigo100,
        secondary = Violet500,
        onSecondary = Color.White,
        background = Slate950,
        onBackground = Slate100,
        surface = Slate900,
        onSurface = Slate100,
        surfaceVariant = Slate800,
        onSurfaceVariant = Slate200,
        error = Red300,
        onError = Slate950,
    )

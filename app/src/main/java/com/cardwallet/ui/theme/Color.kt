package com.cardwallet.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The only file in the app where raw color values are legal (AGENTS.md §6).
// Palette locked 2026-07-17: warm-neutral chrome, honey accent, and 8 card
// tokens validated for colorblind separation + lightness (dataviz validator).

// --- Chrome: warm neutrals biased toward the honey accent ---
val Field = Color(0xFFFAF9F7)
val Surface = Color(0xFFF1EFEA)
val Surface2 = Color(0xFFE7E4DC)
val Hairline = Color(0xFFE0DCD3)
val InkMute = Color(0xFF8B857C)
val InkSoft = Color(0xFF57534E)
val Ink = Color(0xFF1C1917)

val FieldDark = Color(0xFF14110E)
val SurfaceDark = Color(0xFF1F1B16)
val Surface2Dark = Color(0xFF2A251E)
val HairlineDark = Color(0xFF322C24)
val InkMuteDark = Color(0xFF8B857C)
val InkSoftDark = Color(0xFFC3BDB2)
val InkDark = Color(0xFFF5F3EF)

// --- Accent: honey. The one chrome color (pill reveal, + button, selection) ---
val Honey = Color(0xFFE0A020)
val HoneyDeep = Color(0xFFB87D12)
val HoneyDark = Color(0xFFF0B840)
val OnHoney = Color(0xFF241A00)

// --- Card tokens: the app's real color, one per card (F4.11) ---
// CVD-validated in this order; do not reorder without re-running the validator.
val CardBlue = Color(0xFF2A78D6)
val CardGreen = Color(0xFF008300)
val CardRose = Color(0xFFE87BA4)
val CardAmber = Color(0xFFEDA100)
val CardAqua = Color(0xFF1BAF7A)
val CardOrange = Color(0xFFEB6834)
val CardViolet = Color(0xFF4A3AA7)
val CardRed = Color(0xFFE34948)

// Glass backdrop gradient — the quiet field the navbar refracts.
val BackdropTopLight = Field
val BackdropBottomLight = Surface
val BackdropTopDark = SurfaceDark
val BackdropBottomDark = FieldDark

/** Readability scrim the glass bar draws over its refracted backdrop. */
val GlassContainerLight = Field.copy(alpha = 0.4f)
val GlassContainerDark = Color(0xFF121212).copy(alpha = 0.4f)

val LightColorScheme =
    lightColorScheme(
        primary = Honey,
        onPrimary = OnHoney,
        primaryContainer = Surface2,
        onPrimaryContainer = HoneyDeep,
        secondary = InkSoft,
        onSecondary = Field,
        background = Field,
        onBackground = Ink,
        surface = Surface,
        onSurface = Ink,
        surfaceVariant = Surface2,
        onSurfaceVariant = InkSoft,
        outline = Hairline,
        error = CardRed,
        onError = Color.White,
    )

val DarkColorScheme =
    darkColorScheme(
        primary = HoneyDark,
        onPrimary = OnHoney,
        primaryContainer = Surface2Dark,
        onPrimaryContainer = HoneyDark,
        secondary = InkSoftDark,
        onSecondary = FieldDark,
        background = FieldDark,
        onBackground = InkDark,
        surface = SurfaceDark,
        onSurface = InkDark,
        surfaceVariant = Surface2Dark,
        onSurfaceVariant = InkSoftDark,
        outline = HairlineDark,
        error = CardRed,
        onError = Color.White,
    )

package com.cardwallet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardwallet.domain.CardColorToken

/** 4/8-based spacing scale (compose-rules.md §9). No magic paddings in features. */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
)

/** Resolves a domain [CardColorToken] to its tile color. Theme-independent for
 *  now (vivid tiles read on both grounds); becomes theme-aware here if needed.
 *  The palette order is CVD-validated; treat it as fixed. */
fun CardColorToken.color(): Color =
    when (this) {
        CardColorToken.BLUE -> CardBlue
        CardColorToken.GREEN -> CardGreen
        CardColorToken.ROSE -> CardRose
        CardColorToken.AMBER -> CardAmber
        CardColorToken.AQUA -> CardAqua
        CardColorToken.ORANGE -> CardOrange
        CardColorToken.VIOLET -> CardViolet
        CardColorToken.RED -> CardRed
    }

/** Readable content color on each card tile — dark ink on the light accents. */
fun CardColorToken.onColor(): Color =
    when (this) {
        CardColorToken.ROSE, CardColorToken.AMBER, CardColorToken.AQUA -> Ink
        else -> Color.White
    }

/**
 * Wallet-specific design tokens beyond Material's roles (AGENTS.md §6).
 * Provided via [LocalWalletTokens]; read through [WalletTheme.tokens].
 */
@Immutable
data class WalletTokens(
    val spacing: Spacing,
    val glassAccent: Color,
    val glassContainer: Color,
    val backdropTop: Color,
    val backdropBottom: Color,
)

val LightWalletTokens =
    WalletTokens(
        spacing = Spacing(),
        glassAccent = Honey,
        glassContainer = GlassContainerLight,
        backdropTop = BackdropTopLight,
        backdropBottom = BackdropBottomLight,
    )

val DarkWalletTokens =
    WalletTokens(
        spacing = Spacing(),
        glassAccent = HoneyDark,
        glassContainer = GlassContainerDark,
        backdropTop = BackdropTopDark,
        backdropBottom = BackdropBottomDark,
    )

val LocalWalletTokens = staticCompositionLocalOf { LightWalletTokens }

object WalletTheme {
    val tokens: WalletTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalWalletTokens.current
}

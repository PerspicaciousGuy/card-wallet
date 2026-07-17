package com.cardwallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun CardWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val tokens = if (darkTheme) DarkWalletTokens else LightWalletTokens

    CompositionLocalProvider(LocalWalletTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WalletTypography,
            content = content,
        )
    }
}

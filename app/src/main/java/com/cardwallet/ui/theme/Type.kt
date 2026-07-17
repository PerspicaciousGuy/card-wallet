package com.cardwallet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3 defaults with heavier display/title weights — the wallet leans on
// large, confident headings over imagery and glass.
val WalletTypography =
    Typography().run {
        copy(
            headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelSmall = labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
        )
    }

package com.cardwallet.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * A detached circular tinted-glass action button (F5.2) — the accent circle
 * beside the navbar capsule. A thin wrapper over [LiquidButton], so it inherits
 * the press-lift, finger-follow drag and specular highlight for free; only the
 * square sizing and the icon slot differ.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        tint = tint,
        // The navbar row owns this button's size, so no intrinsic height/padding.
        height = null,
        contentPadding = 0.dp,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

package com.cardwallet.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/**
 * A detached circular tinted-glass action button (F5.2) — the accent circle
 * that sits beside the navbar capsule, refracting the same backdrop. Tint uses
 * the library's tinted-button recipe: hue-adapt the refracted backdrop, then a
 * translucent tint layer, so the color reads as glass rather than paint.
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
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = TINT_ALPHA))
                },
            ).clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val TINT_ALPHA = 0.75f

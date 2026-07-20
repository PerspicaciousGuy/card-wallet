package com.cardwallet.ui.glass

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

private const val TINT_ALPHA = 0.75f
private const val DRAG_INITIAL_DERIVATIVE = 0.05f
private const val SCALE_X_DRAG_WEIGHT = 1f
private val BUTTON_HEIGHT = 48.dp
private val BUTTON_PADDING = 16.dp
private val PRESS_LIFT = 4.dp
private val MAX_DRAG_SCALE = 4.dp

/**
 * Ported from the Kyant AndroidLiquidGlass catalog app (`LiquidButton`).
 *
 * A capsule of real glass that responds physically to touch: pressing lifts it
 * slightly, and dragging pulls the whole button after your finger along a
 * `tanh` curve (so it eases to a limit instead of sliding away) while
 * stretching along the drag axis. All of that lives in `layerBlock`, so the
 * refracted backdrop stays put while the button itself deforms.
 *
 * [tint] uses the library's tinted-glass recipe — hue-adapt the backdrop, then
 * a strong tint layer. Unlike the navbar pill, a *button* should read as a
 * solid accent object, so the strong tint is correct here.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    /** Null lets the caller's modifier own the size (e.g. the navbar's + button). */
    height: Dp? = BUTTON_HEIGHT,
    contentPadding: Dp = BUTTON_PADDING,
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock =
                    if (isInteractive) {
                        {
                            val width = size.width
                            val height = size.height

                            val progress = interactiveHighlight.pressProgress
                            val scale = lerp(1f, 1f + PRESS_LIFT.toPx() / height, progress)

                            // tanh keeps the follow bounded: the button leans toward
                            // the finger but never runs away from its slot.
                            val maxOffset = size.minDimension
                            val offset = interactiveHighlight.offset
                            translationX =
                                maxOffset * tanh(DRAG_INITIAL_DERIVATIVE * offset.x / maxOffset)
                            translationY =
                                maxOffset * tanh(DRAG_INITIAL_DERIVATIVE * offset.y / maxOffset)

                            val maxDragScale = MAX_DRAG_SCALE.toPx() / height
                            val offsetAngle = atan2(offset.y, offset.x)
                            scaleX =
                                scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / height).fastCoerceAtMost(SCALE_X_DRAG_WEIGHT)
                            scaleY =
                                scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (height / width).fastCoerceAtMost(SCALE_X_DRAG_WEIGHT)
                        }
                    } else {
                        null
                    },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = TINT_ALPHA))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                },
            ).clickable(
                enabled = isEnabled,
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ).then(
                if (isInteractive && isEnabled) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                },
            ).then(if (height != null) Modifier.height(height) else Modifier)
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

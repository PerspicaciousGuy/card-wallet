package com.cardwallet.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

private val TRACK_SIZE_WIDTH = 64.dp
private val TRACK_SIZE_HEIGHT = 28.dp
private val THUMB_WIDTH = 40.dp
private val THUMB_HEIGHT = 24.dp
private val THUMB_PADDING = 2.dp
private val DRAG_WIDTH = 20.dp
private const val PRESSED_SCALE = 1.5f
private const val HALFWAY = 0.5f
private const val VELOCITY_DAMPING = 50f
private const val SHADOW_ALPHA = 0.05f

/**
 * Ported from the Kyant AndroidLiquidGlass catalog app (`LiquidToggle`), with
 * the accent mapped to our honey token instead of iOS green.
 *
 * The thumb is glass over a *combined* backdrop: the page plus a squeezed copy
 * of the track, so the track's color bends through it. Blur and lens are
 * inverted by press progress — at rest it is a soft frosted pill; pressed, the
 * blur drops away and the refraction blooms. It is draggable, not just
 * tappable: slide it and it follows with velocity squash-and-stretch.
 *
 * Vendored ([Suppress]: the drag state, track and thumb are one interlocking
 * algorithm — splitting them would separate pieces that only change together).
 */
@Suppress("LongMethod")
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    accentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { DRAG_WIDTH.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }

    val dampedDragAnimation =
        remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = fraction,
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = PRESSED_SCALE,
                onDragStarted = {},
                onDragStopped = {
                    // A drag settles to whichever side it ended nearest; a plain
                    // tap (no movement) simply flips the current value.
                    if (didDrag) {
                        fraction = if (targetValue >= HALFWAY) 1f else 0f
                        onSelect(fraction == 1f)
                        didDrag = false
                    } else {
                        fraction = if (selected()) 0f else 1f
                        onSelect(fraction == 1f)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = dragAmount.x / dragWidth
                    fraction =
                        if (isLtr) {
                            (fraction + delta).fastCoerceIn(0f, 1f)
                        } else {
                            (fraction - delta).fastCoerceIn(0f, 1f)
                        }
                },
            )
        }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { dampedDragAnimation.updateValue(it) }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != fraction) {
                fraction = target
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    drawRect(lerp(trackColor, accentColor, dampedDragAnimation.value))
                }.size(TRACK_SIZE_WIDTH, TRACK_SIZE_HEIGHT),
        )

        Box(
            Modifier
                .graphicsLayer {
                    val padding = THUMB_PADDING.toPx()
                    val travelled = dampedDragAnimation.value
                    translationX =
                        if (isLtr) {
                            lerp(padding, padding + dragWidth, travelled)
                        } else {
                            lerp(-padding, -(padding + dragWidth), travelled)
                        }
                }.semantics { role = Role.Switch }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop =
                        rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = dampedDragAnimation.pressProgress
                                scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) {
                                    drawBackdrop()
                                }
                            },
                        ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress,
                        )
                    },
                    shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = SHADOW_ALPHA)) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / VELOCITY_DAMPING
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - dampedDragAnimation.pressProgress))
                    },
                ).size(THUMB_WIDTH, THUMB_HEIGHT),
        )
    }
}

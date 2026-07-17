package com.cardwallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

private const val STROKE_WIDTH_FRACTION = 0.085f

/**
 * Line-style icons drawn on a [Canvas] so the app needs no icon-font dependency.
 * Drawn twice by the glass navbar (white layer + hidden accent layer), so they
 * must be pure tint-parameterized vectors.
 */
@Composable
fun WalletTabIcon(
    tab: WalletTab,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke =
            Stroke(
                width = w * STROKE_WIDTH_FRACTION,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

        when (tab) {
            WalletTab.CARDS -> {
                // Back card, peeking out top-right.
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(0.24f * w, 0.14f * h),
                    size = Size(0.62f * w, 0.44f * h),
                    cornerRadius = CornerRadius(0.08f * w),
                    style = stroke,
                )
                // Front card with a magstripe.
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(0.12f * w, 0.36f * h),
                    size = Size(0.62f * w, 0.44f * h),
                    cornerRadius = CornerRadius(0.08f * w),
                    style = stroke,
                )
                val stripe =
                    Path().apply {
                        moveTo(0.20f * w, 0.56f * h)
                        lineTo(0.66f * w, 0.56f * h)
                    }
                drawPath(stripe, tint, style = stroke)
            }

            WalletTab.SETTINGS -> {
                // Three slider rows with offset knobs.
                val rows = listOf(0.26f, 0.50f, 0.74f)
                val knobs = listOf(0.64f, 0.34f, 0.56f)
                rows.forEachIndexed { i, y ->
                    val line =
                        Path().apply {
                            moveTo(0.14f * w, y * h)
                            lineTo(0.86f * w, y * h)
                        }
                    drawPath(line, tint, style = stroke)
                    drawCircle(
                        color = tint,
                        radius = 0.085f * w,
                        center = Offset(knobs[i] * w, y * h),
                    )
                }
            }
        }
    }
}

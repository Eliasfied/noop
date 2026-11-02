package com.noop.ui.clean

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.noop.ui.rememberReduceMotion
import kotlin.math.cos
import kotlin.math.sin

// MARK: - Score dials
//
// One ring for every score: a faint full-circle track, a gradient arc from the domain's deep tone to its
// bright tone, a soft glow under the arc, and a lit knob where the arc ends. The arc and the number in the
// middle ride ONE reveal animation, so the count-up and the sweep always agree on where they are.

/** A domain's two tones: where an arc starts and where it ends. */
internal data class DialColors(val deep: Color, val bright: Color)

/**
 * A score ring.
 *
 * @param fraction how full the ring is, 0..1; null draws a dashed track only (calibrating / no data).
 * @param target an optional band of the ring, as 0..1 fractions, drawn as a thin outer arc (the strain
 *   target for the day). Purely a marker; it never changes the fill.
 * @param delayMillis start of the reveal, for staggering several dials.
 * @param center the middle of the dial; receives the 0..1 reveal so numbers can count up in step.
 */
@Composable
internal fun ScoreDial(
    fraction: Float?,
    colors: DialColors,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    target: ClosedFloatingPointRange<Float>? = null,
    delayMillis: Int = 0,
    center: @Composable (reveal: Float) -> Unit,
) {
    val reduce = rememberReduceMotion()
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) reveal.animateTo(1f, tween(CleanMotion.dialMillis, delayMillis, CleanMotion.emphasized))
    }
    // Later data changes glide rather than jump.
    val shown by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(if (reduce) 0 else 700, easing = CleanMotion.emphasized),
        label = "dialFraction",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawDial(
                fraction = if (fraction == null) null else shown * reveal.value,
                colors = colors,
                strokePx = stroke.toPx(),
                target = target,
                targetAlpha = reveal.value,
            )
        }
        center(reveal.value)
    }
}

private fun DrawScope.drawDial(
    fraction: Float?,
    colors: DialColors,
    strokePx: Float,
    target: ClosedFloatingPointRange<Float>?,
    targetAlpha: Float,
) {
    val inset = strokePx * 1.4f
    val radius = (size.minDimension - inset * 2f) / 2f
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)

    // Track: solid for a score, dashed when there is none to show.
    drawCircle(
        color = CleanColors.track,
        radius = radius,
        center = center,
        style = Stroke(
            width = strokePx,
            pathEffect = if (fraction == null) PathEffect.dashPathEffect(floatArrayOf(strokePx * 0.5f, strokePx * 0.9f)) else null,
        ),
    )

    // Target band: a thin arc just outside the ring.
    if (target != null && targetAlpha > 0f) {
        val outer = radius + strokePx * 0.95f
        rotate(-90f, center) {
            drawArc(
                color = colors.bright.copy(alpha = 0.75f * targetAlpha),
                startAngle = target.start * 360f,
                sweepAngle = (target.endInclusive - target.start) * 360f,
                useCenter = false,
                topLeft = Offset(center.x - outer, center.y - outer),
                size = Size(outer * 2f, outer * 2f),
                style = Stroke(width = strokePx * 0.28f, cap = StrokeCap.Round),
            )
        }
    }

    val f = fraction ?: return
    if (f <= 0.001f) return
    val sweep = 360f * f
    rotate(-90f, center) {
        // Glow under the arc. BlurMaskFilter is a framework paint feature; where the canvas cannot blur
        // it degrades to a faint wide stroke, which still reads as light.
        drawIntoCanvas { canvas ->
            val glow = Paint().apply {
                style = PaintingStyle.Stroke
                strokeWidth = strokePx * 1.25f
                strokeCap = StrokeCap.Round
                color = colors.bright.copy(alpha = 0.42f)
            }
            glow.asFrameworkPaint().maskFilter = BlurMaskFilter(strokePx * 1.1f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(
                topLeft.x, topLeft.y, topLeft.x + arcSize.width, topLeft.y + arcSize.height,
                0f, sweep, false, glow,
            )
        }
        val stops = if (f < 0.999f) {
            arrayOf(0f to colors.deep, f to colors.bright, 1f to colors.deep)
        } else {
            arrayOf(0f to colors.deep, 1f to colors.bright)
        }
        drawArc(
            brush = Brush.sweepGradient(*stops, center = center),
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
    // The knob at the arc's end, in screen coordinates (0 deg at 12 o'clock, clockwise).
    val angle = Math.toRadians((sweep - 90f).toDouble())
    val knob = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    drawCircle(colors.bright.copy(alpha = 0.35f), radius = strokePx * 0.85f, center = knob)
    drawCircle(Color.White, radius = strokePx * 0.30f, center = knob)
}

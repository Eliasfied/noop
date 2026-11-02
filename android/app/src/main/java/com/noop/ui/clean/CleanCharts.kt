package com.noop.ui.clean

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noop.ui.OptimalStrainRange
import com.noop.ui.rememberReduceMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// MARK: - Charts
//
// Every chart here answers one question and says which, in its card: what one bar or point is, what the
// dashed line is, what the shaded band is. Days without data stay on the axis as a dot, never squeezed
// out, so a gap reads as a gap. Touching a chart selects a day and the card's headline switches to that
// day's value, so no number has to be read off an axis.

/** One bar: its value (null = no data that day) and its colour. */
internal data class BarPoint(val value: Double?, val color: Color)

/** What a chart card's headline shows: the big value, its unit and a caption naming what it is. */
internal data class ChartHeadline(val value: String, val unit: String?, val caption: String, val color: Color)

/**
 * A glass card around a chart: title, a one-sentence explanation, a headline that follows the selected
 * day, the chart itself and an optional legend. [headline] receives the selected index (null = summary).
 */
@Composable
internal fun ChartCard(
    title: String,
    explanation: String,
    resetKey: Any?,
    headline: (selected: Int?) -> ChartHeadline,
    modifier: Modifier = Modifier,
    legend: (@Composable () -> Unit)? = null,
    chart: @Composable (selected: Int?, onSelect: (Int?) -> Unit) -> Unit,
) {
    var selected by remember(resetKey) { mutableStateOf<Int?>(null) }
    GlassCard(modifier) {
        Text(title, style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(explanation, style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(12.dp))
        val h = headline(selected)
        AnimatedContent(
            targetState = h,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "chartHeadline",
        ) { shown ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(shown.value, style = CleanType.bigNumber, color = shown.color)
                    if (shown.unit != null) {
                        Text(
                            shown.unit,
                            style = CleanType.unit,
                            color = CleanColors.textSecondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                    }
                }
                Text(shown.caption, style = CleanType.caption, color = CleanColors.textTertiary)
            }
        }
        Spacer(Modifier.height(14.dp))
        chart(selected) { selected = it }
        if (legend != null) {
            Spacer(Modifier.height(12.dp))
            legend()
        }
    }
}

/** A legend entry: a swatch (solid, or a dashed line) and its meaning. */
@Composable
internal fun LegendItem(color: Color, text: String, dashed: Boolean = false, band: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
            when {
                band -> drawRoundRect(color, cornerRadius = CornerRadius(3.dp.toPx()))
                dashed -> drawLine(
                    color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
                else -> drawCircle(color, radius = size.height / 2.6f)
            }
        }
        Text(text, style = CleanType.caption, color = CleanColors.textSecondary)
    }
}

/** A wrapping row of legend items. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun Legend(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

private val axisLabelStyle = TextStyle(fontSize = CleanType.micro.fontSize, fontWeight = CleanType.micro.fontWeight)

/** Index of the slot under [x] for [n] equal slots across [width]. */
private fun slotAt(x: Float, width: Float, n: Int): Int =
    floor(x / (width / n)).toInt().coerceIn(0, n - 1)

/** [v] kept within 0..[max]; a canvas narrower than its label (first layout pass) pins it to 0. */
private fun clampX(v: Float, max: Float): Float = if (max <= 0f) 0f else v.coerceIn(0f, max)

/** Shared touch handling: tap toggles a day, horizontal drag scrubs. Vertical scrolling passes through. */
private fun Modifier.scrubbable(
    n: Int,
    plotWidth: (Float) -> Float,
    selected: () -> Int?,
    onSelect: (Int?) -> Unit,
    tick: () -> Unit,
): Modifier = this
    .pointerInput(n) {
        detectTapGestures { pos ->
            val i = slotAt(pos.x, plotWidth(size.width.toFloat()), n)
            tick()
            onSelect(if (selected() == i) null else i)
        }
    }
    .pointerInput(n) {
        var last = -1
        detectHorizontalDragGestures(
            onDragStart = { pos ->
                last = slotAt(pos.x, plotWidth(size.width.toFloat()), n)
                tick()
                onSelect(last)
            },
            onHorizontalDrag = { change, _ ->
                val i = slotAt(change.position.x, plotWidth(size.width.toFloat()), n)
                if (i != last) {
                    last = i
                    tick()
                    onSelect(i)
                }
            },
        )
    }

/** The 0..1 entrance of a chart, replayed whenever its data changes. */
@Composable
private fun rememberChartReveal(key: Any?): Float {
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduce) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(CleanMotion.chartMillis, easing = CleanMotion.emphasized))
        }
    }
    return progress.value
}

/**
 * A bar chart over consecutive days.
 *
 * @param average the dashed reference across the chart (the period mean), labelled [averageLabel].
 * @param reference a second dashed line (a target such as the sleep need), labelled [referenceLabel].
 * @param xLabel the label under bar i, or null for none (thinned out on long ranges).
 */
@Composable
internal fun GlassBarChart(
    points: List<BarPoint>,
    yMax: Double,
    yTicks: List<Double>,
    yLabel: (Double) -> String,
    xLabel: (Int) -> String?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    average: Double? = null,
    averageLabel: String? = null,
    reference: Double? = null,
    referenceLabel: String? = null,
    referenceColor: Color = CleanColors.textSecondary,
    height: Dp = CleanDims.chartHeight,
    description: String = "",
) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer(cacheSize = 48)
    val tick = rememberTick()
    val reveal = rememberChartReveal(points)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val axisW = 34.dp
    val axisWPx = with(LocalDensity.current) { axisW.toPx() }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .scrubbable(points.size, { it - axisWPx }, { currentSelected }, { currentOnSelect(it) }, tick),
    ) {
        val axisPx = axisW.toPx()
        val xAxisH = 18.dp.toPx()
        val plotW = size.width - axisPx
        val plotH = size.height - xAxisH
        val n = points.size
        val slot = plotW / n
        val barW = min(slot * if (n <= 10) 0.56f else if (n <= 40) 0.64f else 0.74f, 26.dp.toPx())
        fun yOf(v: Double): Float = plotH - ((v / yMax).toFloat().coerceIn(0f, 1f)) * plotH

        drawGrid(measurer, yTicks, ::yOf, plotW, yLabel)

        points.forEachIndexed { i, p ->
            val cx = slot * i + slot / 2f
            val dim = selected != null && selected != i
            val v = p.value
            if (v == null) {
                drawCircle(CleanColors.track, radius = 2.dp.toPx(), center = Offset(cx, plotH - 3.dp.toPx()))
                return@forEachIndexed
            }
            val local = CleanMotion.emphasized.transform(((reveal * 1.3f) - (i.toFloat() / n) * 0.3f).coerceIn(0f, 1f))
            val top = yOf(v * local)
            val h = max(plotH - top, 2.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(p.color, p.color.copy(alpha = 0.42f)), startY = plotH - h, endY = plotH),
                topLeft = Offset(cx - barW / 2f, plotH - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(min(barW / 2f, h / 2f)),
                alpha = if (dim) 0.3f else 1f,
            )
        }
        if (selected != null && selected in points.indices) {
            val cx = slot * selected + slot / 2f
            drawLine(CleanColors.glassRimBright, Offset(cx, 0f), Offset(cx, plotH), strokeWidth = 1.dp.toPx())
        }
        if (average != null) {
            drawRefLine(measurer, yOf(average), plotW, CleanColors.textPrimary.copy(alpha = 0.7f), averageLabel, start = true, alpha = reveal)
        }
        if (reference != null) {
            drawRefLine(measurer, yOf(reference), plotW, referenceColor, referenceLabel, start = false, alpha = reveal)
        }
        drawXLabels(measurer, n, slot, plotW, plotH, xLabel, selected)
    }
}

/** A point on a line chart. */
internal data class LinePoint(val value: Double?)

/**
 * A line chart over consecutive days with an optional shaded normal range ([band]). The line breaks over
 * days without data rather than drawing a slope nobody measured.
 */
@Composable
internal fun GlassLineChart(
    points: List<LinePoint>,
    yMin: Double,
    yMax: Double,
    yTicks: List<Double>,
    yLabel: (Double) -> String,
    xLabel: (Int) -> String?,
    color: Color,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    band: ClosedFloatingPointRange<Double>? = null,
    bandLabel: String? = null,
    average: Double? = null,
    averageLabel: String? = null,
    height: Dp = CleanDims.chartHeight,
    description: String = "",
) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer(cacheSize = 48)
    val tick = rememberTick()
    val reveal = rememberChartReveal(points)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val axisW = 34.dp
    val axisWPx = with(LocalDensity.current) { axisW.toPx() }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .scrubbable(points.size, { it - axisWPx }, { currentSelected }, { currentOnSelect(it) }, tick),
    ) {
        val axisPx = axisW.toPx()
        val xAxisH = 18.dp.toPx()
        val plotW = size.width - axisPx
        val plotH = size.height - xAxisH
        val n = points.size
        val slot = plotW / n
        val span = (yMax - yMin).takeIf { it > 0 } ?: 1.0
        fun yOf(v: Double): Float = plotH - (((v - yMin) / span).toFloat().coerceIn(0f, 1f)) * plotH
        fun xOf(i: Int): Float = slot * i + slot / 2f

        if (band != null) {
            val top = yOf(band.endInclusive)
            val bottom = yOf(band.start)
            drawRect(color.copy(alpha = 0.10f), topLeft = Offset(0f, top), size = Size(plotW, max(bottom - top, 1f)))
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            drawLine(color.copy(alpha = 0.35f), Offset(0f, top), Offset(plotW, top), 1.dp.toPx(), pathEffect = dash)
            drawLine(color.copy(alpha = 0.35f), Offset(0f, bottom), Offset(plotW, bottom), 1.dp.toPx(), pathEffect = dash)
            if (bandLabel != null) {
                val layout = measurer.measure(bandLabel, axisLabelStyle.copy(color = color.copy(alpha = 0.85f)))
                drawText(layout, topLeft = Offset(plotW - layout.size.width - 4.dp.toPx(), top + 2.dp.toPx()))
            }
        }
        drawGrid(measurer, yTicks, ::yOf, plotW, yLabel)

        // Runs of consecutive days with data, each drawn as its own smooth segment.
        val runs = mutableListOf<List<Int>>()
        var cur = mutableListOf<Int>()
        points.forEachIndexed { i, p ->
            if (p.value != null) cur.add(i) else if (cur.isNotEmpty()) { runs.add(cur); cur = mutableListOf() }
        }
        if (cur.isNotEmpty()) runs.add(cur)

        clipRect(right = plotW * reveal + slot) {
            for (run in runs) {
                val pts = run.map { Offset(xOf(it), yOf(points[it].value!!)) }
                if (pts.size == 1) {
                    drawCircle(color, radius = 3.dp.toPx(), center = pts[0])
                    continue
                }
                val line = smoothPath(pts)
                val area = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, plotH)
                    lineTo(pts.first().x, plotH)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), endY = plotH))
                drawPath(line, color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
            }
            if (n <= 31) {
                points.forEachIndexed { i, p ->
                    val v = p.value ?: return@forEachIndexed
                    drawCircle(CleanColors.canvasBottom, radius = 3.2.dp.toPx(), center = Offset(xOf(i), yOf(v)))
                    drawCircle(color, radius = 2.2.dp.toPx(), center = Offset(xOf(i), yOf(v)))
                }
            }
        }
        points.forEachIndexed { i, _ -> if (points[i].value == null) {
            drawCircle(CleanColors.track, radius = 2.dp.toPx(), center = Offset(xOf(i), plotH - 3.dp.toPx()))
        } }
        if (average != null) {
            drawRefLine(measurer, yOf(average), plotW, CleanColors.textPrimary.copy(alpha = 0.6f), averageLabel, start = true, alpha = reveal)
        }
        if (selected != null && selected in points.indices) {
            val x = xOf(selected)
            drawLine(CleanColors.glassRimBright, Offset(x, 0f), Offset(x, plotH), strokeWidth = 1.dp.toPx())
            points[selected].value?.let { v ->
                drawCircle(color.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = Offset(x, yOf(v)))
                drawCircle(Color.White, radius = 3.5.dp.toPx(), center = Offset(x, yOf(v)))
            }
        }
        drawXLabels(measurer, n, slot, plotW, plotH, xLabel, selected)
    }
}

/** A gently smoothed path through [pts] (horizontal-tangent cubic segments, never overshooting in x). */
private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val mx = (a.x + b.x) / 2f
        cubicTo(mx, a.y, mx, b.y, b.x, b.y)
    }
}

private fun DrawScope.drawGrid(
    measurer: TextMeasurer,
    ticks: List<Double>,
    yOf: (Double) -> Float,
    plotW: Float,
    label: (Double) -> String,
) {
    for (t in ticks) {
        val y = yOf(t)
        drawLine(CleanColors.gridLine, Offset(0f, y), Offset(plotW, y), strokeWidth = 1.dp.toPx())
        val layout = measurer.measure(label(t), axisLabelStyle.copy(color = CleanColors.textTertiary))
        drawText(layout, topLeft = Offset(plotW + 6.dp.toPx(), y - layout.size.height / 2f))
    }
}

private fun DrawScope.drawRefLine(
    measurer: TextMeasurer,
    y: Float,
    plotW: Float,
    color: Color,
    label: String?,
    start: Boolean,
    alpha: Float,
) {
    drawLine(
        color.copy(alpha = color.alpha * alpha), Offset(0f, y), Offset(plotW, y),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
    if (label == null) return
    val layout = measurer.measure(label, axisLabelStyle.copy(color = CleanColors.textPrimary))
    val padH = 6.dp.toPx()
    val padV = 2.dp.toPx()
    val w = layout.size.width + padH * 2
    val h = layout.size.height + padV * 2
    val x = if (start) 4.dp.toPx() else plotW - w - 4.dp.toPx()
    val top = (y - h - 3.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(
        CleanColors.glassBarDeep.copy(alpha = 0.9f * alpha), topLeft = Offset(x, top), size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f),
    )
    drawRoundRect(
        color.copy(alpha = 0.55f * alpha), topLeft = Offset(x, top), size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f), style = Stroke(1.dp.toPx()),
    )
    drawText(layout, topLeft = Offset(x + padH, top + padV), alpha = alpha)
}

private fun DrawScope.drawXLabels(
    measurer: TextMeasurer,
    n: Int,
    slot: Float,
    plotW: Float,
    plotH: Float,
    label: (Int) -> String?,
    selected: Int?,
) {
    for (i in 0 until n) {
        val text = label(i) ?: continue
        val active = selected == i
        val layout = measurer.measure(
            text,
            axisLabelStyle.copy(color = if (active) CleanColors.textPrimary else CleanColors.textTertiary),
        )
        val cx = slot * i + slot / 2f
        val x = clampX(cx - layout.size.width / 2f, plotW - layout.size.width)
        drawText(layout, topLeft = Offset(x, plotH + 4.dp.toPx()))
    }
}

/** Every [step]-th label counted back from the newest day, so the last day is always labelled. */
internal fun thinnedLabel(i: Int, n: Int, step: Int, label: () -> String): String? =
    if ((n - 1 - i) % step == 0) label() else null

/** Label spacing for a day count: every day up to a week, then weekly, then fortnightly. */
internal fun labelStep(n: Int): Int = when {
    n <= 7 -> 1
    n <= 31 -> 7
    else -> 15
}

/** Round-number y ticks covering 0..[max] (at most four lines). */
internal fun niceTicks(maxValue: Double, minValue: Double = 0.0): List<Double> {
    val span = (maxValue - minValue).takeIf { it > 0 } ?: return listOf(minValue)
    val raw = span / 3.0
    val mag = Math.pow(10.0, floor(Math.log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
    val first = ceil(minValue / step) * step
    return generateSequence(first) { it + step }.takeWhile { it <= maxValue + 1e-9 }.toList()
}

// MARK: - Sleep charts

internal fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.AWAKE -> CleanColors.stageAwake
    SleepStage.REM -> CleanColors.stageRem
    SleepStage.LIGHT -> CleanColors.stageLight
    SleepStage.DEEP -> CleanColors.stageDeep
}

private val hypnogramLanes = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)

/**
 * The night as four lanes, light to deep, with each run of a stage drawn in its lane and thin steps joining
 * consecutive runs. Hour ticks underneath. Dragging along it reports the moment under the finger.
 */
@Composable
internal fun SleepHypnogram(
    runs: List<StageRun>,
    onset: Long,
    wake: Long,
    stageLabel: (SleepStage) -> String,
    timeLabel: (Long) -> String,
    scrubTs: Long?,
    onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    if (wake <= onset) return
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val reveal = rememberChartReveal(runs)
    val tick = rememberTick()
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentScrub by rememberUpdatedState(scrubTs)
    val axisH = 20.dp
    Row(modifier.fillMaxWidth().height(CleanDims.hypnogramHeight)) {
        Column(Modifier.width(50.dp).fillMaxHeight().padding(bottom = axisH)) {
            hypnogramLanes.forEach { stage ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Text(stageLabel(stage), style = CleanType.micro, color = stageColor(stage))
                }
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics { contentDescription = description }
                .pointerInput(onset, wake) {
                    detectTapGestures { pos ->
                        val ts = onset + ((pos.x / size.width).coerceIn(0f, 1f) * (wake - onset)).toLong()
                        tick()
                        currentOnScrub(if (currentScrub != null) null else ts)
                    }
                }
                .pointerInput(onset, wake) {
                    var lastMinute = -1L
                    detectHorizontalDragGestures(
                        onDragStart = { pos ->
                            tick()
                            currentOnScrub(onset + ((pos.x / size.width).coerceIn(0f, 1f) * (wake - onset)).toLong())
                        },
                        onHorizontalDrag = { change, _ ->
                            val ts = onset + ((change.position.x / size.width).coerceIn(0f, 1f) * (wake - onset)).toLong()
                            val minute = ts / 600
                            if (minute != lastMinute) {
                                lastMinute = minute
                                tick()
                            }
                            currentOnScrub(ts)
                        },
                    )
                },
        ) {
            val axisPx = axisH.toPx()
            val laneH = (size.height - axisPx) / hypnogramLanes.size
            val span = (wake - onset).toFloat()
            fun xOf(ts: Long): Float = ((ts - onset) / span).coerceIn(0f, 1f) * size.width
            fun laneOf(stage: SleepStage): Int = hypnogramLanes.indexOf(stage)

            hypnogramLanes.forEachIndexed { i, _ ->
                drawRoundRect(
                    CleanColors.track.copy(alpha = 0.45f),
                    topLeft = Offset(0f, i * laneH + laneH * 0.14f),
                    size = Size(size.width, laneH * 0.72f),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            clipRect(right = size.width * reveal) {
                runs.forEachIndexed { idx, run ->
                    val lane = laneOf(run.stage)
                    if (idx > 0) {
                        val prev = runs[idx - 1]
                        if (run.start - prev.end <= 600) {
                            val x = xOf(run.start)
                            drawLine(
                                Color.White.copy(alpha = 0.22f),
                                Offset(x, laneOf(prev.stage) * laneH + laneH / 2f),
                                Offset(x, lane * laneH + laneH / 2f),
                                strokeWidth = 1.2.dp.toPx(),
                            )
                        }
                    }
                    val x0 = xOf(run.start)
                    val w = max(xOf(run.end) - x0, 1.5.dp.toPx())
                    val c = stageColor(run.stage)
                    val top = lane * laneH + laneH * 0.18f
                    val h = laneH * 0.64f
                    drawRoundRect(
                        Brush.verticalGradient(listOf(c, c.copy(alpha = 0.7f)), startY = top, endY = top + h),
                        topLeft = Offset(x0, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(min(4.dp.toPx(), w / 2f)),
                    )
                }
            }
            // Hour ticks: every hour on a short night, every two or three on a long one.
            val hours = (wake - onset) / 3600.0
            val step = when {
                hours <= 5.0 -> 3600L
                hours <= 10.0 -> 7200L
                else -> 10800L
            }
            var t = (onset / 3600L + 1) * 3600L
            val labelStyle = axisLabelStyle.copy(color = CleanColors.textTertiary)
            while (t < wake) {
                if ((t / 3600L) % (step / 3600L) == 0L) {
                    val x = xOf(t)
                    drawLine(CleanColors.gridLine, Offset(x, 0f), Offset(x, size.height - axisPx), strokeWidth = 1.dp.toPx())
                    val layout = measurer.measure(timeLabel(t), labelStyle)
                    val lx = clampX(x - layout.size.width / 2f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(lx, size.height - axisPx + 4.dp.toPx()))
                }
                t += 3600L
            }
            if (scrubTs != null) {
                val x = xOf(scrubTs)
                drawLine(Color.White.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height - axisPx), strokeWidth = 1.5.dp.toPx())
                runs.firstOrNull { scrubTs in it.start until it.end }?.let { run ->
                    val y = laneOf(run.stage) * laneH + laneH / 2f
                    drawCircle(stageColor(run.stage).copy(alpha = 0.4f), radius = 8.dp.toPx(), center = Offset(x, y))
                    drawCircle(Color.White, radius = 3.5.dp.toPx(), center = Offset(x, y))
                }
            }
        }
    }
}

/** One stacked bar of the night's stage shares, light to deep, widths animated in. */
@Composable
internal fun StageShareBar(shares: List<Pair<SleepStage, Double>>, modifier: Modifier = Modifier) {
    val total = shares.sumOf { it.second }.takeIf { it > 0 } ?: return
    val reveal = rememberChartReveal(shares)
    Canvas(modifier.fillMaxWidth().height(CleanDims.stageBarHeight).clip(RoundedCornerShape(CleanDims.pillRadius))) {
        var x = 0f
        val gap = 2.dp.toPx()
        shares.forEach { (stage, minutes) ->
            val w = (minutes / total).toFloat() * size.width * reveal
            if (w > gap) {
                drawRect(stageColor(stage), topLeft = Offset(x, 0f), size = Size(w - gap, size.height))
            }
            x += w
        }
    }
}

/**
 * Slept time against the need: a filled track with the need marked. The track runs to the larger of the
 * two (plus a margin), so an over-need night visibly passes the marker.
 */
@Composable
internal fun NeedBar(asleepMin: Double, needMin: Double, color: Color, modifier: Modifier = Modifier) {
    val reveal = rememberChartReveal(asleepMin)
    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        val maxMin = max(asleepMin, needMin) * 1.08
        val trackH = 10.dp.toPx()
        val top = (size.height - trackH) / 2f
        drawRoundRect(CleanColors.track, topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = CornerRadius(trackH / 2f))
        val w = (asleepMin / maxMin).toFloat() * size.width * reveal
        if (w > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color), endX = w),
                topLeft = Offset(0f, top), size = Size(w, trackH), cornerRadius = CornerRadius(trackH / 2f),
            )
        }
        val nx = (needMin / maxMin).toFloat() * size.width
        drawLine(Color.White, Offset(nx, 0f), Offset(nx, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

// MARK: - Small explanatory graphics

/**
 * Where today's value sits in the personal normal range: a faint track, the range as a brighter band and
 * the value as a dot in the tone colour. The track spans the range plus a margin, widened to keep the
 * value on it.
 */
@Composable
internal fun RangeBar(stat: VitalStat, toneColor: Color, modifier: Modifier = Modifier) {
    val low = stat.bandLow
    val high = stat.bandHigh
    val dotX by animateFloatAsState(
        targetValue = if (low != null && high != null) {
            val margin = (high - low) * 0.75
            val lo = min(low - margin, stat.value - margin * 0.2)
            val hi = max(high + margin, stat.value + margin * 0.2)
            ((stat.value - lo) / (hi - lo)).toFloat()
        } else 0.5f,
        animationSpec = tween(900, easing = CleanMotion.emphasized),
        label = "rangeDot",
    )
    Canvas(modifier.height(12.dp)) {
        val trackH = CleanDims.rangeBarHeight.toPx()
        val top = (size.height - trackH) / 2f
        drawRoundRect(CleanColors.track, topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = CornerRadius(trackH / 2f))
        if (low == null || high == null) return@Canvas
        val margin = (high - low) * 0.75
        val lo = min(low - margin, stat.value - margin * 0.2)
        val hi = max(high + margin, stat.value + margin * 0.2)
        val bx0 = ((low - lo) / (hi - lo)).toFloat() * size.width
        val bx1 = ((high - lo) / (hi - lo)).toFloat() * size.width
        drawRoundRect(Color.White.copy(alpha = 0.22f), topLeft = Offset(bx0, top), size = Size(bx1 - bx0, trackH), cornerRadius = CornerRadius(trackH / 2f))
        val cx = dotX * size.width
        drawCircle(toneColor.copy(alpha = 0.35f), radius = size.height / 2f, center = Offset(cx, size.height / 2f))
        drawCircle(toneColor, radius = size.height / 3.2f, center = Offset(cx, size.height / 2f))
    }
}

/**
 * A recovery driver's pull, as a bar growing left (pulled the score down) or right (lifted it) from a
 * centre line. [maxAbs] scales all rows of one list together so their lengths compare.
 */
@Composable
internal fun DriverBar(points: Int, maxAbs: Int, modifier: Modifier = Modifier) {
    val target = if (maxAbs <= 0) 0f else (points.toFloat() / maxAbs).coerceIn(-1f, 1f)
    val shown by animateFloatAsState(target, tween(900, easing = CleanMotion.emphasized), label = "driver")
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val mid = size.width / 2f
        val h = size.height
        drawRoundRect(CleanColors.track, size = Size(size.width, h), cornerRadius = CornerRadius(h / 2f))
        val w = abs(shown) * mid
        if (w > 0.5f) {
            val color = if (shown > 0) CleanColors.good else CleanColors.watch
            val x = if (shown > 0) mid else mid - w
            drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
        }
        drawLine(Color.White.copy(alpha = 0.6f), Offset(mid, -2.dp.toPx()), Offset(mid, h + 2.dp.toPx()), strokeWidth = 1.5.dp.toPx())
    }
}

internal fun strainLevelColor(level: StrainLevel): Color = when (level) {
    StrainLevel.LIGHT -> CleanColors.strainBright.copy(alpha = 0.55f)
    StrainLevel.MODERATE -> CleanColors.strainBright
    StrainLevel.HIGH -> CleanColors.strain
    StrainLevel.ALL_OUT -> CleanColors.strainDeep
}

/**
 * The 0-21 strain scale in its four levels, the day's target band bracketed above it and the current
 * strain as a lit marker. Answers "how far am I from today's target" at a glance.
 */
@Composable
internal fun StrainScale(
    strain21: Double?,
    target: OptimalStrainRange?,
    tickLabel: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val markerX by animateFloatAsState(
        ((strain21 ?: 0.0) / STRAIN_MAX).toFloat().coerceIn(0f, 1f),
        tween(1100, easing = CleanMotion.emphasized),
        label = "strainMarker",
    )
    Canvas(modifier.fillMaxWidth().height(58.dp)) {
        val barTop = 18.dp.toPx()
        val barH = 10.dp.toPx()
        val w = size.width
        fun xOf(v: Double) = (v / STRAIN_MAX).toFloat() * w
        // Target bracket above the bar.
        if (target != null) {
            val x0 = xOf(target.low.toDouble())
            val x1 = xOf(target.high.toDouble())
            drawRoundRect(
                CleanColors.strain.copy(alpha = 0.22f), topLeft = Offset(x0, barTop - 6.dp.toPx()),
                size = Size(x1 - x0, barH + 12.dp.toPx()), cornerRadius = CornerRadius(8.dp.toPx()),
            )
            drawRoundRect(
                CleanColors.strainBright.copy(alpha = 0.8f), topLeft = Offset(x0, barTop - 6.dp.toPx()),
                size = Size(x1 - x0, barH + 12.dp.toPx()), cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(1.2.dp.toPx()),
            )
        }
        StrainLevel.entries.forEach { level ->
            val x0 = xOf(level.from)
            val x1 = xOf(level.to)
            drawRoundRect(
                strainLevelColor(level).copy(alpha = 0.55f), topLeft = Offset(x0 + 1.dp.toPx(), barTop),
                size = Size(x1 - x0 - 2.dp.toPx(), barH), cornerRadius = CornerRadius(barH / 2f),
            )
        }
        val style = axisLabelStyle.copy(color = CleanColors.textTertiary)
        listOf(0, 10, 14, 18, 21).forEach { v ->
            val layout = measurer.measure(tickLabel(v), style)
            val x = clampX(xOf(v.toDouble()) - layout.size.width / 2f, w - layout.size.width)
            drawText(layout, topLeft = Offset(x, barTop + barH + 8.dp.toPx()))
        }
        if (strain21 != null) {
            val mx = markerX * w
            val cy = barTop + barH / 2f
            drawCircle(CleanColors.strainBright.copy(alpha = 0.35f), radius = 11.dp.toPx(), center = Offset(mx, cy))
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(mx, cy))
            drawCircle(CleanColors.strain, radius = 3.5.dp.toPx(), center = Offset(mx, cy))
        }
    }
}

/** A small coloured dot. */
@Composable
internal fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

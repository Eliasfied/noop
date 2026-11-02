package com.noop.ui.clean

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.noop.R
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.NoopPrefs
import com.noop.ui.StressBand
import com.noop.ui.StressModel
import com.noop.widget.StressPoint
import com.noop.widget.StressWidgetProducer
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

// MARK: - Clean Stress
//
// Stress on WHOOP's familiar 0-3 scale, from the SAME two producers the classic Stress screen, the Today
// card and the widget read: StressModel for the day's score (stored series first, else resting HR and HRV
// against the 30-day baseline) and StressWidgetProducer for today's curve. Three surfaces, one number.

/** The stress scale's top. */
internal const val STRESS_MAX = 3.0

internal fun stressColor(level: Double): Color = when (StressBand.forScore(level)) {
    StressBand.Low -> CleanColors.trend
    StressBand.Medium -> CleanColors.watch
    StressBand.High -> CleanColors.bad
}

internal fun stressDialColors(level: Double): DialColors = when (StressBand.forScore(level)) {
    StressBand.Low -> DialColors(CleanColors.trendDeep, CleanColors.trend)
    StressBand.Medium -> DialColors(CleanColors.recoveryMidDeep, CleanColors.watch)
    StressBand.High -> DialColors(CleanColors.recoveryLowDeep, CleanColors.bad)
}

@Composable
internal fun stressBandName(level: Double): String = stringResource(
    when (StressBand.forScore(level)) {
        StressBand.Low -> R.string.clean_stress_low
        StressBand.Medium -> R.string.clean_stress_medium
        StressBand.High -> R.string.clean_stress_high
    },
)

/** The day's stress model plus its trend by day, loaded the way the classic screen loads them. */
internal data class StressRead(val model: StressModel?, val byDay: Map<String, Double>, val loaded: Boolean)

@Composable
internal fun rememberStressRead(vm: AppViewModel, days: List<DailyMetric>): StressRead {
    var stored by remember { mutableStateOf<Map<String, Double>?>(null) }
    LaunchedEffect(Unit) {
        stored = runCatching {
            vm.repo.metricSeries("my-whoop", "stress", "0000-01-01", "9999-12-31")
        }.getOrDefault(emptyList()).associate { it.day to it.value.coerceIn(0.0, STRESS_MAX) }
    }
    val s = stored ?: return StressRead(null, emptyMap(), loaded = false)
    return remember(days, s) {
        val model = StressModel.build(days, s)
        StressRead(model, model?.fullTrend?.associate { it.day to it.value }.orEmpty(), loaded = true)
    }
}

/** Today's curve from the shared producer, refreshed on the producer's own interval while visible. */
@Composable
internal fun rememberStressCurve(vm: AppViewModel): StressWidgetProducer.Curve? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var curve by remember { mutableStateOf<StressWidgetProducer.Curve?>(null) }
    LaunchedEffect(vm.activeStrapId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                curve = StressWidgetProducer.todayCurve(
                    vm.repo, vm.activeStrapId, NoopPrefs.stressPersonalBaseline(context),
                ) ?: curve
                delay(StressWidgetProducer.RESCORE_INTERVAL_MS)
            }
        }
    }
    return curve
}

@Composable
internal fun CleanStressScreen(vm: AppViewModel, onBack: () -> Unit, onOpenBreathe: () -> Unit, onAsk: (String) -> Unit) {
    val fmt = rememberCleanFormat()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val stress = rememberStressRead(vm, days)
    val curve = rememberStressCurve(vm)
    val model = stress.model
    var range by rememberSaveable { mutableStateOf(HistoryRange.MONTH) }
    val end = days.lastOrNull()?.day?.let(LocalDate::parse) ?: LocalDate.now()

    CleanScreen(
        title = stringResource(R.string.clean_stress_title),
        subtitle = stringResource(R.string.clean_stress_subtitle),
        accent = model?.score?.let(::stressColor) ?: CleanColors.trend,
        onBack = onBack,
    ) {
        StressHero(model, fmt, Modifier.cleanReveal(1))
        model?.score?.let { AskCoachButton(stringResource(R.string.clean_coach_q_stress, fmt.decimal(it)), onAsk, Modifier.cleanReveal(1)) }
        DayCurveCard(curve, fmt, Modifier.cleanReveal(2))
        GlassCard(Modifier.cleanReveal(3), tint = CleanColors.trend, onClick = onOpenBreathe) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Air, null, tint = CleanColors.trend, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.clean_stress_breathe_title), style = CleanType.body, color = CleanColors.textPrimary)
                    Text(stringResource(R.string.clean_stress_breathe_body), style = CleanType.caption, color = CleanColors.textTertiary)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary)
            }
        }
        if (stress.byDay.isNotEmpty()) {
            HistoryBarsCard(
                title = stringResource(R.string.clean_history),
                explanation = stringResource(R.string.clean_stress_chart_explain),
                days = days,
                end = end,
                range = range,
                ranges = listOf(HistoryRange.WEEK, HistoryRange.MONTH, HistoryRange.QUARTER),
                onRange = { range = it },
                pick = { stress.byDay[it.day] },
                color = ::stressColor,
                yMax = STRESS_MAX,
                yTicks = listOf(0.0, 1.0, 2.0, 3.0),
                yLabel = { fmt.int(it) },
                valueText = { ValueText(fmt.decimal(it), null) },
                fmt = fmt,
                accent = CleanColors.trend,
                modifier = Modifier.cleanReveal(4),
                legend = {
                    Legend {
                        LegendItem(CleanColors.trend, stringResource(R.string.clean_stress_legend_low))
                        LegendItem(CleanColors.watch, stringResource(R.string.clean_stress_legend_medium))
                        LegendItem(CleanColors.bad, stringResource(R.string.clean_stress_legend_high))
                    }
                },
            )
        }
        GlassCard(Modifier.cleanReveal(5)) {
            Text(stringResource(R.string.clean_stress_how_title), style = CleanType.headline, color = CleanColors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.clean_stress_how_body), style = CleanType.callout, color = CleanColors.textSecondary)
        }
        HonestyNote(Modifier.cleanReveal(6))
    }
}

@Composable
private fun StressHero(model: StressModel?, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val score = model?.score
    GlassCard(modifier, tint = score?.let(::stressColor)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreDial(
                fraction = score?.let { (it / STRESS_MAX).toFloat() },
                colors = score?.let(::stressDialColors) ?: DialColors(CleanColors.trendDeep, CleanColors.trend),
                size = CleanDims.heroDial,
                stroke = CleanDims.heroDialStroke,
            ) { reveal ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        score?.let { fmt.decimal(it * reveal) } ?: "–",
                        style = CleanType.heroNumber,
                        color = if (score == null) CleanColors.textTertiary else CleanColors.textPrimary,
                    )
                    Text(stringResource(R.string.clean_stress_of_3).uppercase(), style = CleanType.label, color = CleanColors.textSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (model == null || score == null) {
                Text(stringResource(R.string.clean_stress_none), style = CleanType.callout, color = CleanColors.textSecondary, textAlign = TextAlign.Center)
                return@Column
            }
            GlassChip(stressBandName(score), stressColor(score))
            Spacer(Modifier.height(10.dp))
            val parts = buildList {
                model.rhrDelta?.let { d ->
                    add(
                        stringResource(
                            if (d >= 0) R.string.clean_stress_rhr_up else R.string.clean_stress_rhr_down,
                            fmt.string(R.string.clean_value_bpm, fmt.int(abs(d))),
                        ),
                    )
                }
                model.hrvDelta?.let { d ->
                    add(
                        stringResource(
                            if (d >= 0) R.string.clean_stress_hrv_up else R.string.clean_stress_hrv_down,
                            fmt.string(R.string.clean_value_ms, fmt.int(abs(d))),
                        ),
                    )
                }
            }
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · "),
                    style = CleanType.callout,
                    color = CleanColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            model.fullTrend.lastOrNull()?.day?.let { day ->
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.clean_as_of, fmt.relativeDay(LocalDate.parse(day))),
                    style = CleanType.caption,
                    color = CleanColors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun DayCurveCard(curve: StressWidgetProducer.Curve?, fmt: CleanFormat, modifier: Modifier = Modifier) {
    var selected by remember(curve) { mutableStateOf<Int?>(null) }
    val points = curve?.points.orEmpty()
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_stress_today_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_stress_today_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(12.dp))
        val scored = points.filter { it.level != null && !it.moving }
        if (scored.isEmpty()) {
            Text(
                stringResource(if (curve == null) R.string.clean_loading else R.string.clean_stress_today_none),
                style = CleanType.callout,
                color = CleanColors.textTertiary,
            )
            return@GlassCard
        }
        val pick = selected?.let { points.getOrNull(it) }
        val shown = pick?.takeIf { it.level != null } ?: scored.last()
        val headline = when {
            pick != null && pick.moving -> "${fmt.time(pick.ts)} · ${stringResource(R.string.clean_stress_moving)}"
            pick != null && pick.level == null -> "${fmt.time(pick.ts)} · ${stringResource(R.string.clean_no_data_day)}"
            else -> "${fmt.time(shown.ts)} · ${fmt.decimal(shown.level!!)} · ${stressBandName(shown.level)}"
        }
        Text(
            (if (pick == null) stringResource(R.string.clean_stress_latest) + " " else "") + headline,
            style = CleanType.headline,
            color = if (pick == null || pick.level != null) stressColor(shown.level!!) else CleanColors.textSecondary,
        )
        val peak = scored.maxBy { it.level!! }
        Text(
            stringResource(R.string.clean_stress_peak, fmt.decimal(peak.level!!), fmt.time(peak.ts)),
            style = CleanType.caption,
            color = CleanColors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        StressTimelineChart(points, fmt, selected) { selected = it }
        Spacer(Modifier.height(10.dp))
        Legend {
            LegendItem(CleanColors.trend, stringResource(R.string.clean_stress_legend_low))
            LegendItem(CleanColors.watch, stringResource(R.string.clean_stress_legend_medium))
            LegendItem(CleanColors.bad, stringResource(R.string.clean_stress_legend_high))
            LegendItem(CleanColors.textTertiary, stringResource(R.string.clean_stress_moving))
        }
        curve?.activityMaskedHours?.takeIf { it > 0 }?.let { hours ->
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.clean_stress_masked, hours),
                style = CleanType.caption,
                color = CleanColors.textTertiary,
            )
        }
    }
}

/**
 * Today's stress through the day on the 0-3 scale, the three bands shaded behind the line. Points the
 * producer marked as movement sit on the baseline in grey: exertion is not scored as stress.
 */
@Composable
private fun StressTimelineChart(
    points: List<StressPoint>,
    fmt: CleanFormat,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val tick = rememberTick()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val first = points.first().ts
    val last = max(points.last().ts + 3600L, first + 3600L)
    fun nearest(x: Float, width: Float): Int {
        val ts = first + ((x / width).coerceIn(0f, 1f) * (last - first)).toLong()
        return points.indices.minBy { abs(points[it].ts + 1800L - ts) }
    }
    val labelStyle = TextStyle(fontSize = CleanType.micro.fontSize, color = CleanColors.textTertiary)
    val description = stringResource(R.string.clean_stress_today_title)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CleanDims.chartHeight)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { pos ->
                    val i = nearest(pos.x, size.width.toFloat() - 30.dp.toPx())
                    tick()
                    currentOnSelect(if (currentSelected == i) null else i)
                }
            }
            .pointerInput(points) {
                var lastIndex = -1
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        lastIndex = nearest(pos.x, size.width.toFloat() - 30.dp.toPx())
                        tick()
                        currentOnSelect(lastIndex)
                    },
                    onHorizontalDrag = { change, _ ->
                        val i = nearest(change.position.x, size.width.toFloat() - 30.dp.toPx())
                        if (i != lastIndex) {
                            lastIndex = i
                            tick()
                            currentOnSelect(i)
                        }
                    },
                )
            },
    ) {
        val axisW = 30.dp.toPx()
        val axisH = 18.dp.toPx()
        val w = size.width - axisW
        val h = size.height - axisH
        fun xOf(ts: Long) = ((ts + 1800L - first).toFloat() / (last - first)).coerceIn(0f, 1f) * w
        fun yOf(v: Double) = h - (v / STRESS_MAX).toFloat().coerceIn(0f, 1f) * h
        // The three bands, calm at the bottom.
        listOf(0.0 to CleanColors.trend, 1.0 to CleanColors.watch, 2.0 to CleanColors.bad).forEach { (from, c) ->
            drawRect(c.copy(alpha = 0.09f), topLeft = Offset(0f, yOf(from + 1.0)), size = Size(w, yOf(from) - yOf(from + 1.0)))
        }
        for (v in 0..3) {
            val y = yOf(v.toDouble())
            drawLine(CleanColors.gridLine, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(v.toString(), labelStyle)
            drawText(layout, topLeft = Offset(w + 8.dp.toPx(), y - layout.size.height / 2f))
        }
        // Line through consecutive scored points; movement and gaps break it.
        clipRect(right = w) {
            var prev: Offset? = null
            points.forEach { p ->
                val level = p.level
                if (level == null || p.moving) {
                    prev = null
                    if (p.moving) {
                        drawCircle(CleanColors.textTertiary, radius = 2.5.dp.toPx(), center = Offset(xOf(p.ts), h - 3.dp.toPx()))
                    }
                    return@forEach
                }
                val pt = Offset(xOf(p.ts), yOf(level))
                prev?.let { drawLine(Color.White.copy(alpha = 0.55f), it, pt, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round) }
                prev = pt
            }
            points.forEach { p ->
                val level = p.level ?: return@forEach
                if (p.moving) return@forEach
                drawCircle(stressColor(level), radius = 3.2.dp.toPx(), center = Offset(xOf(p.ts), yOf(level)))
            }
        }
        // Hour labels every three hours.
        var t = (first / 10800L + 1) * 10800L
        while (t < last) {
            val x = xOf(t - 1800L)
            val layout = measurer.measure(fmt.hour(t), labelStyle)
            val lx = x - layout.size.width / 2f
            if (lx >= 0f && lx + layout.size.width <= w) drawText(layout, topLeft = Offset(lx, h + 4.dp.toPx()))
            t += 10800L
        }
        selected?.let { points.getOrNull(it) }?.let { p ->
            val x = xOf(p.ts)
            drawLine(CleanColors.glassRimBright, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            p.level?.takeIf { !p.moving }?.let { level ->
                drawCircle(stressColor(level).copy(alpha = 0.35f), radius = 9.dp.toPx(), center = Offset(x, yOf(level)))
                drawCircle(Color.White, radius = 3.5.dp.toPx(), center = Offset(x, yOf(level)), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

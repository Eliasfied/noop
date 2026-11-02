package com.noop.ui.clean

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.noop.R
import com.noop.analytics.HeartRateRecovery
import com.noop.analytics.HrZoneSet
import com.noop.data.HrBucket
import com.noop.data.WorkoutRow
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// MARK: - Clean Workouts
//
// The workout log and one workout opened up. Every number comes from the AppViewModel's existing workout
// reads (loadWorkouts, workoutHrBuckets, workoutZoneMinutes, workoutHeartRateRecovery), which resolve the
// same strap the classic Workouts card charts, so the curve, the zones and the averages on one page
// describe the same samples. Editing stays in the classic view.

/** How far back the clean log lists workouts. */
private const val WORKOUT_LOG_DAYS = 90L

/** Opens one workout's clean detail page. */
fun NavHostController.openCleanWorkout(w: WorkoutRow) {
    navigate("${CleanRoutes.WORKOUT}/${Uri.encode(w.deviceId)}/${w.startTs}")
}

private fun localDate(epochSec: Long): LocalDate =
    Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
internal fun CleanWorkoutsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenWorkout: (WorkoutRow) -> Unit,
    onOpenClassic: () -> Unit,
) {
    val fmt = rememberCleanFormat()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadWorkouts() }
    val cutoff = System.currentTimeMillis() / 1000L - WORKOUT_LOG_DAYS * 86_400L
    val recent = remember(workouts) { workouts.filter { it.startTs >= cutoff }.sortedByDescending { it.startTs } }
    val weekFields = WeekFields.of(fmt.locale)
    val today = LocalDate.now()
    val weeks = remember(recent) {
        recent.groupBy { localDate(it.startTs).with(weekFields.dayOfWeek(), 1) }.toList()
    }

    CleanScreen(
        title = stringResource(R.string.clean_workouts),
        subtitle = stringResource(R.string.clean_workouts_subtitle),
        accent = CleanColors.strain,
        onBack = onBack,
    ) {
        WeekSummary(recent, fmt, Modifier.cleanReveal(1))
        if (recent.isEmpty()) {
            GlassCard(Modifier.cleanReveal(2)) {
                Text(stringResource(R.string.clean_workouts_empty), style = CleanType.callout, color = CleanColors.textSecondary)
            }
        }
        weeks.forEachIndexed { i, (weekStart, list) ->
            val thisWeek = today.with(weekFields.dayOfWeek(), 1)
            val title = when (weekStart) {
                thisWeek -> stringResource(R.string.clean_week_this)
                thisWeek.minusWeeks(1) -> stringResource(R.string.clean_week_last)
                else -> stringResource(R.string.clean_week_of, fmt.dayMonth(weekStart), fmt.dayMonth(weekStart.plusDays(6)))
            }
            Column(Modifier.cleanReveal(2 + min(i, 6))) {
                SectionLabel(title, trailing = pluralStringResource(R.plurals.clean_workouts_count, list.size, list.size))
                Spacer(Modifier.height(6.dp))
                GlassCard(padding = 14.dp) {
                    list.forEachIndexed { j, w ->
                        WorkoutLine(w, fmt, showDate = true, onClick = { onOpenWorkout(w) })
                        if (j < list.lastIndex) HorizontalDivider(color = CleanColors.hairline, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
        ClassicLinkCard(
            stringResource(R.string.clean_workouts_classic_title),
            stringResource(R.string.clean_workouts_classic_body),
            onOpenClassic,
            Modifier.cleanReveal(9),
        )
    }
}

@Composable
private fun WeekSummary(recent: List<WorkoutRow>, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val since = LocalDate.now().minusDays(6)
    val week = recent.filter { !localDate(it.startTs).isBefore(since) }
    val strains = week.mapNotNull { it.strain }.map { round1(strainOn21(it)) }
    GlassCard(modifier, tint = CleanColors.strain) {
        Text(stringResource(R.string.clean_workouts_week_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            SummaryStat(week.size.toString(), stringResource(R.string.clean_workouts), Modifier.weight(1f))
            SummaryStat(fmt.hoursMinutes(week.sumOf { workoutMinutes(it) }), stringResource(R.string.clean_workouts_time), Modifier.weight(1f))
            SummaryStat(
                strains.takeIf { it.isNotEmpty() }?.let { fmt.decimal(it.average()) } ?: "–",
                stringResource(R.string.clean_workouts_avg_strain),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = CleanType.number, color = CleanColors.textPrimary)
        Text(label, style = CleanType.caption, color = CleanColors.textTertiary)
    }
}

/** A glass row that opens a classic screen. */
@Composable
internal fun ClassicLinkCard(title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = CleanType.body, color = CleanColors.textPrimary)
                Text(body, style = CleanType.caption, color = CleanColors.textTertiary)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary)
        }
    }
}

// MARK: Detail

@Composable
internal fun CleanWorkoutScreen(
    vm: AppViewModel,
    deviceId: String,
    startTs: Long,
    onBack: () -> Unit,
    onOpenClassic: () -> Unit,
) {
    val fmt = rememberCleanFormat()
    val context = LocalContext.current
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadWorkouts() }
    val w = remember(workouts, deviceId, startTs) {
        workouts.firstOrNull { it.deviceId == deviceId && it.startTs == startTs }
            ?: workouts.firstOrNull { it.startTs == startTs }
    }
    val zoneSet = remember { ProfileStore.from(context).hrZoneSet }
    var zones by remember(w) { mutableStateOf<List<Double>?>(null) }
    var zonesLoaded by remember(w) { mutableStateOf(false) }
    var buckets by remember(w) { mutableStateOf<List<HrBucket>>(emptyList()) }
    var hrr by remember(w) { mutableStateOf<HeartRateRecovery.Result?>(null) }
    LaunchedEffect(w) {
        val row = w ?: return@LaunchedEffect
        buckets = runCatching { vm.workoutHrBuckets(row.startTs, row.endTs, row.source, row.deviceId) }.getOrDefault(emptyList())
        zones = runCatching { vm.workoutZoneMinutes(row.startTs, row.endTs, row.source, row.deviceId) }.getOrNull()
        zonesLoaded = true
        hrr = runCatching { vm.workoutHeartRateRecovery(row.startTs, row.endTs, row.source, row.deviceId) }.getOrNull()
    }

    CleanScreen(
        title = w?.let { sportLabel(it.sport) } ?: stringResource(R.string.clean_workouts),
        subtitle = w?.let {
            "${fmt.longDate(localDate(it.startTs))} · ${fmt.time(it.startTs)}–${fmt.time(it.endTs)}"
        },
        accent = CleanColors.strain,
        onBack = onBack,
    ) {
        if (w == null) {
            GlassCard(Modifier.cleanReveal(1)) {
                Text(stringResource(R.string.clean_loading), style = CleanType.callout, color = CleanColors.textTertiary)
            }
            return@CleanScreen
        }
        WorkoutHero(w, fmt, Modifier.cleanReveal(1))
        if (buckets.size >= 2) {
            GlassCard(Modifier.cleanReveal(2)) {
                Text(stringResource(R.string.clean_workout_hr_title), style = CleanType.headline, color = CleanColors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.clean_workout_hr_explain), style = CleanType.caption, color = CleanColors.textSecondary)
                Spacer(Modifier.height(12.dp))
                WorkoutHrChart(buckets, zoneSet, fmt)
            }
        }
        ZonesCard(
            title = stringResource(R.string.clean_zones_workout_title),
            explanation = stringResource(R.string.clean_zones_workout_explain),
            split = when {
                zones != null -> ZoneSplit(zones!!, zoneSet)
                zonesLoaded -> ZoneSplit(List(5) { 0.0 }, zoneSet)
                else -> null
            },
            fmt = fmt,
            modifier = Modifier.cleanReveal(3),
        )
        hrr?.let { HrrCard(it, fmt, Modifier.cleanReveal(4)) }
        w.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            GlassCard(Modifier.cleanReveal(5)) {
                Text(stringResource(R.string.clean_workout_notes), style = CleanType.headline, color = CleanColors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(notes, style = CleanType.callout, color = CleanColors.textSecondary)
            }
        }
        ClassicLinkCard(
            stringResource(R.string.clean_workout_classic_title),
            stringResource(R.string.clean_workout_classic_body),
            onOpenClassic,
            Modifier.cleanReveal(6),
        )
    }
}

@Composable
private fun WorkoutHero(w: WorkoutRow, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val strain = w.strain?.let { round1(strainOn21(it)) }
    GlassCard(modifier, tint = CleanColors.strain) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreDial(
                fraction = strain?.let { (it / STRAIN_MAX).toFloat() },
                colors = strainDialColors,
                size = 170.dp,
                stroke = 13.dp,
            ) { reveal ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strain?.let { fmt.decimal(it * reveal) } ?: "–", style = CleanType.heroNumber, color = CleanColors.textPrimary)
                    Text(stringResource(R.string.clean_strain).uppercase(), style = CleanType.label, color = CleanColors.textSecondary)
                }
            }
            if (strain != null) {
                Spacer(Modifier.height(10.dp))
                GlassChip(strainLevelName(strainLevel(strain)), strainLevelColor(strainLevel(strain)))
            }
            Spacer(Modifier.height(16.dp))
            val stats = buildList {
                add(stringResource(R.string.clean_stat_duration) to fmt.hoursMinutes(workoutMinutes(w)))
                w.avgHr?.let { add(stringResource(R.string.clean_stat_avg_hr) to fmt.string(R.string.clean_value_bpm, it.toString())) }
                w.maxHr?.let { add(stringResource(R.string.clean_stat_max_hr) to fmt.string(R.string.clean_value_bpm, it.toString())) }
                w.energyKcal?.let { add(stringResource(R.string.clean_kcal) to fmt.string(R.string.clean_kcal_value, fmt.int(it))) }
                w.distanceM?.takeIf { it > 0 }?.let { add(stringResource(R.string.clean_stat_distance) to fmt.string(R.string.clean_value_km, fmt.decimal(it / 1000.0, 2))) }
                w.steps?.let { add(stringResource(R.string.clean_steps) to fmt.int(it.toDouble())) }
            }
            stats.chunked(3).forEach { rowStats ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    rowStats.forEach { (label, value) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, style = CleanType.number, color = CleanColors.textPrimary)
                            Text(label, style = CleanType.caption, color = CleanColors.textTertiary, textAlign = TextAlign.Center)
                        }
                    }
                    repeat(3 - rowStats.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HrrCard(hrr: HeartRateRecovery.Result, fmt: CleanFormat, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_hrr_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_hrr_explain, hrr.endHr), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(1 to hrr.after1Minute, 2 to hrr.after2Minutes, 5 to hrr.after5Minutes).forEach { (minutes, drop) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        drop?.let { fmt.string(R.string.clean_value_bpm, (if (it > 0) MINUS else "+") + abs(it)) } ?: "–",
                        style = CleanType.number,
                        color = if (drop != null && drop > 0) CleanColors.good else CleanColors.textSecondary,
                    )
                    Text(stringResource(R.string.clean_hrr_after, minutes), style = CleanType.caption, color = CleanColors.textTertiary)
                }
            }
        }
    }
}

/**
 * Heart rate through one workout, the user's five zones shaded behind it so each stretch of the curve
 * reads as a zone. Touching it reports the moment and the heart rate.
 */
@Composable
internal fun WorkoutHrChart(
    buckets: List<HrBucket>,
    zoneSet: HrZoneSet,
    fmt: CleanFormat,
    description: String = stringResource(R.string.clean_workout_hr_title),
) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val tick = rememberTick()
    var selected by remember(buckets) { mutableStateOf<Int?>(null) }
    val currentSelected by rememberUpdatedState(selected)
    val lo = (buckets.minOf { it.avgBpm } - 8).coerceAtLeast(30.0)
    val hi = buckets.maxOf { it.avgBpm } + 8
    val labelStyle = TextStyle(fontSize = CleanType.micro.fontSize, color = CleanColors.textTertiary)
    val pick = selected?.let { buckets.getOrNull(it) }
    Text(
        pick?.let { "${fmt.time(it.bucket)} · ${fmt.string(R.string.clean_value_bpm, fmt.int(it.avgBpm))} · ${stringResource(R.string.clean_zone_n, max(1, zoneSet.zoneNumber(it.avgBpm)))}" }
            ?: stringResource(R.string.clean_chart_touch_hint),
        style = CleanType.callout,
        color = pick?.let { zoneColor(max(1, zoneSet.zoneNumber(it.avgBpm))) } ?: CleanColors.textTertiary,
    )
    Spacer(Modifier.height(8.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CleanDims.chartHeight)
            .semantics { contentDescription = description }
            .pointerInput(buckets) {
                fun index(x: Float): Int =
                    ((x / (size.width - 34.dp.toPx())) * (buckets.size - 1)).toInt().coerceIn(0, buckets.lastIndex)
                detectTapGestures { pos ->
                    val i = index(pos.x)
                    tick()
                    selected = if (currentSelected == i) null else i
                }
            }
            .pointerInput(buckets) {
                fun index(x: Float): Int =
                    ((x / (size.width - 34.dp.toPx())) * (buckets.size - 1)).toInt().coerceIn(0, buckets.lastIndex)
                var last = -1
                detectHorizontalDragGestures(
                    onDragStart = { pos -> last = index(pos.x); tick(); selected = last },
                    onHorizontalDrag = { change, _ ->
                        val i = index(change.position.x)
                        if (i != last) { last = i; tick(); selected = i }
                    },
                )
            },
    ) {
        val axisW = 34.dp.toPx()
        val axisH = 18.dp.toPx()
        val w = size.width - axisW
        val h = size.height - axisH
        fun yOf(bpm: Double) = h - ((bpm - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) * h
        fun xOf(i: Int) = if (buckets.size <= 1) 0f else i.toFloat() / (buckets.size - 1) * w
        zoneSet.zones.forEach { z ->
            val top = yOf(min(z.upper, hi))
            val bottom = yOf(max(z.lower, lo))
            if (bottom > top) drawRect(zoneColor(z.number).copy(alpha = 0.10f), topLeft = Offset(0f, top), size = Size(w, bottom - top))
            if (z.lower in lo..hi) {
                val y = yOf(z.lower)
                drawLine(CleanColors.gridLine, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                val layout = measurer.measure(z.lower.toInt().toString(), labelStyle)
                drawText(layout, topLeft = Offset(w + 6.dp.toPx(), y - layout.size.height / 2f))
            }
        }
        val line = Path().apply {
            buckets.forEachIndexed { i, b ->
                val p = Offset(xOf(i), yOf(b.avgBpm))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        clipRect(right = w) {
            drawPath(
                line,
                Brush.verticalGradient(
                    listOf(CleanColors.zone5, CleanColors.zone4, CleanColors.zone3, CleanColors.zone2, CleanColors.zone1),
                    startY = 0f,
                    endY = h,
                ),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        listOf(0, buckets.lastIndex / 2, buckets.lastIndex).distinct().forEach { i ->
            val layout = measurer.measure(fmt.time(buckets[i].bucket), labelStyle)
            val x = (xOf(i) - layout.size.width / 2f).coerceIn(0f, max(0f, w - layout.size.width))
            drawText(layout, topLeft = Offset(x, h + 4.dp.toPx()))
        }
        pick?.let { b ->
            val i = buckets.indexOf(b)
            val x = xOf(i)
            drawLine(CleanColors.glassRimBright, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(x, yOf(b.avgBpm)))
        }
    }
}

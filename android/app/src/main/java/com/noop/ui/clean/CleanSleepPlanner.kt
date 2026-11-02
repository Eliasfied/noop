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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.RestScorer
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// MARK: - Sleep planner and regularity
//
// The planner answers "when should I go to bed tonight": the next wake (the phone wake window if it fires
// next, else the strap alarm, else the wearer's usual wake time), minus the SAME sleep need the sleep
// score and the need card use, stretched by the wearer's usual efficiency so it plans time in bed rather
// than time asleep. Regularity shows the classic Sleep tab's bedtime-regularity score with the nights it
// comes from, as bed-to-wake bars on a clock.

internal enum class WakeSource { PHONE_ALARM, STRAP_ALARM, USUAL }

internal data class SleepPlan(
    val wake: LocalDateTime,
    val source: WakeSource,
    val bedtime: LocalDateTime,
    val inBedMin: Double,
    val efficiencyPct: Double?,
)

/** The alarm minute that fires on [date] for a weekday set (empty = every day) and per-day overrides. */
internal fun alarmMinuteOn(date: LocalDate, enabled: Boolean, base: Int, weekdays: Set<Int>, overrides: Map<Int, Int>): Int? {
    if (!enabled) return null
    val dow = calendarDayOfWeek(date)
    if (weekdays.isNotEmpty() && dow !in weekdays) return null
    return overrides[dow] ?: base
}

/** The next alarm instant after [now] within today and tomorrow, or null. */
internal fun nextAlarm(now: LocalDateTime, minuteOn: (LocalDate) -> Int?): LocalDateTime? =
    listOf(now.toLocalDate(), now.toLocalDate().plusDays(1)).firstNotNullOfOrNull { date ->
        minuteOn(date)?.let { date.atStartOfDay().plusMinutes(it.toLong()) }?.takeIf { it.isAfter(now) }
    }

@Composable
internal fun rememberSleepPlan(vm: AppViewModel, days: List<DailyMetric>, spans: List<Pair<Long, Long>>): SleepPlan? {
    val phoneOn by vm.phoneAlarmEnabled.collectAsStateWithLifecycle()
    val phoneTarget by vm.phoneAlarmTargetMinutes.collectAsStateWithLifecycle()
    val phoneDays by vm.phoneAlarmWeekdays.collectAsStateWithLifecycle()
    val phoneOverrides by vm.phoneAlarmDayOverrides.collectAsStateWithLifecycle()
    val strapOn by vm.smartAlarmEnabled.collectAsStateWithLifecycle()
    val strapMinutes by vm.smartAlarmMinutes.collectAsStateWithLifecycle()
    val strapDays by vm.smartAlarmWeekdays.collectAsStateWithLifecycle()
    val strapOverrides by vm.smartAlarmDayOverrides.collectAsStateWithLifecycle()
    val now = LocalDateTime.now().withSecond(0).withNano(0)
    return remember(phoneOn, phoneTarget, phoneDays, phoneOverrides, strapOn, strapMinutes, strapDays, strapOverrides, days, spans, now.hour) {
        val phone = nextAlarm(now) { alarmMinuteOn(it, phoneOn, phoneTarget, phoneDays, phoneOverrides) }
        val strap = nextAlarm(now) { alarmMinuteOn(it, strapOn, strapMinutes, strapDays, strapOverrides) }
        val usual = medianMinute(spans.map { minuteOfDay(it.second) })?.let { nextAt(it, now) }
        val (wake, source) = when {
            phone != null -> phone to WakeSource.PHONE_ALARM
            strap != null -> strap to WakeSource.STRAP_ALARM
            usual != null -> usual to WakeSource.USUAL
            else -> return@remember null
        }
        val efficiencies = days.takeLast(14).mapNotNull { efficiencyPercent(it.efficiency) }.sorted()
        val eff = efficiencies.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
        val need = RestScorer.defaultSleepNeedHours * 60.0
        val bedtime = plannedBedtime(wake, need, eff)
        SleepPlan(wake, source, bedtime, Duration.between(bedtime, wake).toMinutes().toDouble(), eff)
    }
}

@Composable
internal fun SleepPlannerCard(plan: SleepPlan?, fmt: CleanFormat, onOpenAlarms: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier, tint = CleanColors.sleep) {
        Text(stringResource(R.string.clean_planner_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_planner_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(14.dp))
        if (plan == null) {
            Text(stringResource(R.string.clean_planner_none), style = CleanType.callout, color = CleanColors.textTertiary)
        } else {
            val zone = ZoneId.systemDefault()
            fun epoch(t: LocalDateTime) = t.atZone(zone).toEpochSecond()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fmt.time(epoch(plan.bedtime)), style = CleanType.bigNumber, color = CleanColors.sleepBright)
                    Text(stringResource(R.string.clean_planner_bed), style = CleanType.caption, color = CleanColors.textTertiary)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = CleanColors.textTertiary, modifier = Modifier.padding(horizontal = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(fmt.time(epoch(plan.wake)), style = CleanType.bigNumber, color = CleanColors.textPrimary)
                    Text(
                        stringResource(
                            when (plan.source) {
                                WakeSource.PHONE_ALARM -> R.string.clean_planner_wake_phone
                                WakeSource.STRAP_ALARM -> R.string.clean_planner_wake_strap
                                WakeSource.USUAL -> R.string.clean_planner_wake_usual
                            },
                        ),
                        style = CleanType.caption,
                        color = CleanColors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val need = RestScorer.defaultSleepNeedHours * 60.0
            Text(
                plan.efficiencyPct?.let {
                    stringResource(R.string.clean_planner_detail, fmt.hoursMinutes(need), fmt.percent(it), fmt.hoursMinutes(plan.inBedMin))
                } ?: stringResource(R.string.clean_planner_detail_no_eff, fmt.hoursMinutes(need)),
                style = CleanType.callout,
                color = CleanColors.textSecondary,
            )
            val now = LocalDateTime.now()
            if (now.isAfter(plan.bedtime.minusMinutes(30)) && now.isBefore(plan.wake)) {
                val left = Duration.between(now, plan.wake).toMinutes().toDouble() * ((plan.efficiencyPct ?: 100.0) / 100.0)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.clean_planner_now, fmt.hoursMinutes(left)),
                    style = CleanType.callout,
                    color = if (left < need) CleanColors.watch else CleanColors.good,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.Rounded.Alarm, stringResource(R.string.clean_planner_alarms), onOpenAlarms, Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.clean_planner_alarms), style = CleanType.callout, color = CleanColors.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary)
        }
    }
}

/** Minutes since 18:00 for a wall-clock instant, so a night's onset and wake fall on one rising axis. */
internal fun eveningMinutes(epochSec: Long): Int = (minuteOfDay(epochSec) - 18 * 60 + 1440) % 1440

@Composable
internal fun RegularityCard(
    regularity: Double?,
    typical: Double?,
    spans: List<Pair<Long, Long>>,
    fmt: CleanFormat,
    modifier: Modifier = Modifier,
) {
    var selected by remember(spans) { mutableStateOf<Int?>(null) }
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_regularity_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_regularity_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(12.dp))
        if (regularity == null || spans.size < 3) {
            Text(stringResource(R.string.clean_regularity_none), style = CleanType.callout, color = CleanColors.textTertiary)
            return@GlassCard
        }
        val pick = selected?.let { spans.getOrNull(it) }
        if (pick != null) {
            val wakeDay = Instant.ofEpochSecond(pick.second).atZone(ZoneId.systemDefault()).toLocalDate()
            Text(
                "${fmt.longDate(wakeDay)} · ${fmt.time(pick.first)} – ${fmt.time(pick.second)}",
                style = CleanType.headline,
                color = CleanColors.sleepBright,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmt.percent(regularity), style = CleanType.bigNumber, color = CleanColors.sleepBright)
                typical?.let {
                    Text(
                        "  " + stringResource(R.string.clean_regularity_typical, fmt.percent(it)),
                        style = CleanType.caption,
                        color = CleanColors.textTertiary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SleepWindowChart(spans, fmt, selected) { selected = it }
        Spacer(Modifier.height(10.dp))
        Legend {
            LegendItem(CleanColors.sleep, stringResource(R.string.clean_regularity_legend_night))
            LegendItem(CleanColors.textPrimary, stringResource(R.string.clean_regularity_legend_median), dashed = true)
        }
    }
}

/**
 * The trailing nights as vertical bars on a clock: the top of a bar is falling asleep, the bottom waking
 * up. Equal bars at equal heights are a regular rhythm; the dashed lines are the median onset and wake.
 */
@Composable
private fun SleepWindowChart(spans: List<Pair<Long, Long>>, fmt: CleanFormat, selected: Int?, onSelect: (Int?) -> Unit) {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val tick = rememberTick()
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val onsets = spans.map { eveningMinutes(it.first) }
    val wakes = spans.map { s -> eveningMinutes(s.second).let { if (it < eveningMinutes(s.first)) it + 1440 else it } }
    val top = floor((onsets.min() - 30) / 60.0) * 60
    val bottom = (floor((wakes.max() + 30) / 60.0) + 1) * 60
    val medianOnset = medianMinute(onsets)
    val medianWake = medianMinute(wakes)
    val labelStyle = TextStyle(fontSize = CleanType.micro.fontSize, color = CleanColors.textTertiary)
    val description = stringResource(R.string.clean_regularity_title)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CleanDims.chartHeight)
            .semantics { contentDescription = description }
            .pointerInput(spans) {
                fun index(x: Float) = (x / ((size.width - 40.dp.toPx()) / spans.size)).toInt().coerceIn(0, spans.lastIndex)
                detectTapGestures { pos ->
                    val i = index(pos.x)
                    tick()
                    currentOnSelect(if (currentSelected == i) null else i)
                }
            }
            .pointerInput(spans) {
                fun index(x: Float) = (x / ((size.width - 40.dp.toPx()) / spans.size)).toInt().coerceIn(0, spans.lastIndex)
                var last = -1
                detectHorizontalDragGestures(
                    onDragStart = { pos -> last = index(pos.x); tick(); currentOnSelect(last) },
                    onHorizontalDrag = { change, _ ->
                        val i = index(change.position.x)
                        if (i != last) { last = i; tick(); currentOnSelect(i) }
                    },
                )
            },
    ) {
        val axisW = 40.dp.toPx()
        val axisH = 18.dp.toPx()
        val w = size.width - axisW
        val h = size.height - axisH
        fun yOf(m: Double) = ((m - top) / (bottom - top)).toFloat().coerceIn(0f, 1f) * h
        // Hour grid, labelled with the clock time.
        var m = top
        while (m <= bottom) {
            val y = yOf(m)
            drawLine(CleanColors.gridLine, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            if (((m / 60).toInt()) % 2 == 0) {
                val clock = ((m.toInt() + 18 * 60) % 1440) / 60
                val layout = measurer.measure(String.format(fmt.locale, "%02d:00", clock), labelStyle)
                drawText(layout, topLeft = Offset(w + 6.dp.toPx(), y - layout.size.height / 2f))
            }
            m += 60
        }
        val slot = w / spans.size
        val barW = min(slot * 0.5f, 16.dp.toPx())
        spans.indices.forEach { i ->
            val cx = slot * i + slot / 2f
            val y0 = yOf(onsets[i].toDouble())
            val y1 = yOf(wakes[i].toDouble())
            val dim = selected != null && selected != i
            drawRoundRect(
                Brush.verticalGradient(listOf(CleanColors.sleepBright, CleanColors.sleepDeep), startY = y0, endY = y1),
                topLeft = Offset(cx - barW / 2f, y0),
                size = Size(barW, max(y1 - y0, 2f)),
                cornerRadius = CornerRadius(barW / 2f),
                alpha = if (dim) 0.3f else 1f,
            )
            val wakeDay = Instant.ofEpochSecond(spans[i].second).atZone(ZoneId.systemDefault()).toLocalDate()
            if (spans.size <= 7 || (spans.size - 1 - i) % 2 == 0) {
                val layout = measurer.measure(fmt.weekday(wakeDay), labelStyle)
                val x = (cx - layout.size.width / 2f).coerceIn(0f, max(0f, w - layout.size.width))
                drawText(layout, topLeft = Offset(x, h + 4.dp.toPx()))
            }
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        listOfNotNull(medianOnset, medianWake).forEach { med ->
            val y = yOf(med.toDouble())
            drawLine(Color.White.copy(alpha = 0.6f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
        }
    }
}

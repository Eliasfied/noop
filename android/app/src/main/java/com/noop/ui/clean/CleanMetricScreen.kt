package com.noop.ui.clean

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.noop.R
import com.noop.analytics.SkinTempDisplay
import com.noop.data.DailyMetric
import com.noop.data.HrBucket
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import com.noop.ui.logicalDayStartEpochSecond
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.max

// MARK: - Clean metric detail
//
// What a Home number tile opens: heart rate through today (the curve the tile's "now" is the end of), or
// one nightly vital / daily activity figure over the last weeks. A vital's normal range is the same band
// the tile's bar draws (vitalsFor), so the tile and its history never place the normal differently.

/** The metrics a Home tile can open. */
internal enum class CleanMetric { HEART, HRV, RESTING_HR, RESPIRATION, SKIN_TEMP, SPO2, STEPS, KCAL }

internal fun CleanMetric.vitalKind(): VitalKind? = when (this) {
    CleanMetric.HRV -> VitalKind.HRV
    CleanMetric.RESTING_HR -> VitalKind.RESTING_HR
    CleanMetric.RESPIRATION -> VitalKind.RESPIRATION
    CleanMetric.SKIN_TEMP -> VitalKind.SKIN_TEMP
    CleanMetric.SPO2 -> VitalKind.SPO2
    else -> null
}

internal fun VitalKind.metric(): CleanMetric? = CleanMetric.entries.firstOrNull { it.vitalKind() == this }

/** The daily value a metric's history plots, read the way the tile reads it. */
internal fun CleanMetric.pick(row: DailyMetric): Double? = when (this) {
    CleanMetric.HEART, CleanMetric.RESTING_HR -> row.restingHr?.toDouble()
    CleanMetric.HRV -> row.avgHrv
    CleanMetric.RESPIRATION -> row.respRateBpm
    CleanMetric.SKIN_TEMP -> row.skinTempDevC?.takeIf { SkinTempDisplay.kind(it) == SkinTempDisplay.Kind.DEVIATION }
    CleanMetric.SPO2 -> row.spo2Pct
    CleanMetric.STEPS -> row.steps?.toDouble()
    CleanMetric.KCAL -> row.activeKcalEst
}

/** Today's heart rate in five-minute buckets, refreshed every minute while the page is visible. */
@Composable
private fun rememberHeartToday(vm: AppViewModel): List<HrBucket>? {
    val lifecycleOwner = LocalLifecycleOwner.current
    var buckets by remember { mutableStateOf<List<HrBucket>?>(null) }
    LaunchedEffect(vm.activeStrapId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis() / 1000L
                val from = logicalDayStartEpochSecond(ZonedDateTime.now())
                buckets = runCatching { vm.repo.hrBucketsUnion(vm.activeStrapId, from, now, 300L) }
                    .getOrDefault(emptyList())
                delay(60_000L)
            }
        }
    }
    return buckets
}

@Composable
private fun metricTitle(metric: CleanMetric): String = when (metric) {
    CleanMetric.HEART -> stringResource(R.string.clean_tile_heart_rate)
    CleanMetric.STEPS -> stringResource(R.string.clean_steps)
    CleanMetric.KCAL -> stringResource(R.string.clean_kcal)
    else -> vitalName(metric.vitalKind()!!)
}

@Composable
private fun metricExplain(metric: CleanMetric): String = stringResource(
    when (metric) {
        CleanMetric.HEART, CleanMetric.RESTING_HR -> R.string.clean_metric_rhr_explain
        CleanMetric.HRV -> R.string.clean_metric_hrv_explain
        CleanMetric.RESPIRATION -> R.string.clean_metric_resp_explain
        CleanMetric.SKIN_TEMP -> R.string.clean_metric_skin_explain
        CleanMetric.SPO2 -> R.string.clean_metric_spo2_explain
        CleanMetric.STEPS -> R.string.clean_metric_steps_explain
        CleanMetric.KCAL -> R.string.clean_metric_kcal_explain
    },
)

private fun metricAccent(metric: CleanMetric): Color = when (metric) {
    CleanMetric.HEART, CleanMetric.RESTING_HR -> CleanColors.recoveryLow
    CleanMetric.HRV -> CleanColors.recoveryHigh
    CleanMetric.STEPS -> CleanColors.strainBright
    CleanMetric.KCAL -> CleanColors.watch
    else -> CleanColors.trend
}

@Composable
internal fun CleanMetricScreen(vm: AppViewModel, metric: CleanMetric, onBack: () -> Unit, onOpenLive: () -> Unit) {
    val today = rememberCleanToday(vm)
    val fmt = rememberCleanFormat()
    var range by rememberSaveable { mutableStateOf(HistoryRange.MONTH) }
    val end = LocalDate.parse(today.todayKey)
    val accent = metricAccent(metric)
    val vitals = remember(today) { today.vitalsRow?.let { vitalsFor(it, today) }.orEmpty() }
    val ranges = listOf(HistoryRange.WEEK, HistoryRange.MONTH, HistoryRange.QUARTER)

    CleanScreen(title = metricTitle(metric), accent = accent, onBack = onBack) {
        if (metric == CleanMetric.HEART) {
            HeartTodayCard(vm, fmt, onOpenLive, Modifier.cleanReveal(1))
        }
        // The nightly vital as the tile shows it, then its history against the same normal band.
        val historyKind = if (metric == CleanMetric.HEART) VitalKind.RESTING_HR else metric.vitalKind()
        if (historyKind != null) {
            val stat = vitals.firstOrNull { it.kind == historyKind }
            if (stat != null) {
                if (metric != CleanMetric.HEART) VitalTile(stat, fmt, Modifier.fillMaxWidth().cleanReveal(1))
            }
            val low = stat?.bandLow
            val high = stat?.bandHigh
            HistoryLineCard(
                title = if (metric == CleanMetric.HEART) stringResource(R.string.clean_metric_rhr_history) else stringResource(R.string.clean_history),
                explanation = metricExplain(metric),
                days = today.days,
                end = end,
                range = range,
                ranges = ranges,
                onRange = { range = it },
                pick = { metric.pick(it) },
                color = accent,
                valueText = { v -> vitalNumber(historyKind, v, fmt).let { ValueText(it.first, it.second) } },
                tickLabel = { v -> if (historyKind == VitalKind.RESPIRATION || historyKind == VitalKind.SKIN_TEMP) fmt.decimal(v) else fmt.int(v) },
                fmt = fmt,
                band = if (low != null && high != null) low..high else null,
                modifier = Modifier.cleanReveal(2),
                legend = {
                    Legend {
                        LegendItem(accent, stringResource(R.string.clean_metric_legend_night))
                        if (low != null && high != null) LegendItem(CleanColors.track, stringResource(R.string.clean_band_normal), band = true)
                        LegendItem(CleanColors.textPrimary, stringResource(R.string.clean_legend_average), dashed = true)
                    }
                },
            )
            Text(
                stringResource(R.string.clean_metric_nightly_note),
                style = CleanType.caption,
                color = CleanColors.textTertiary,
                modifier = Modifier.cleanReveal(3),
            )
        } else {
            val present = remember(today.days) { today.days.mapNotNull { metric.pick(it) } }
            val top = max(present.maxOrNull() ?: 0.0, if (metric == CleanMetric.STEPS) 8_000.0 else 400.0)
            val ticks = niceTicks(top * 1.1, 0.0)
            HistoryBarsCard(
                title = stringResource(R.string.clean_history),
                explanation = metricExplain(metric),
                days = today.days,
                end = end,
                range = range,
                ranges = ranges,
                onRange = { range = it },
                pick = { metric.pick(it) },
                color = { accent },
                yMax = ticks.lastOrNull() ?: top,
                yTicks = ticks,
                yLabel = { v -> if (v >= 1_000) fmt.decimal(v / 1_000.0) + "k" else fmt.int(v) },
                valueText = { v -> ValueText(fmt.int(v), if (metric == CleanMetric.KCAL) "kcal" else null) },
                fmt = fmt,
                accent = accent,
                modifier = Modifier.cleanReveal(1),
            )
        }
    }
}

/** Heart rate through today with the wearer's zones behind it, today's low / average / high, and Live. */
@Composable
private fun HeartTodayCard(vm: AppViewModel, fmt: CleanFormat, onOpenLive: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val zoneSet = remember { ProfileStore.from(context).hrZoneSet }
    val buckets = rememberHeartToday(vm)
    val title = stringResource(R.string.clean_metric_heart_today)
    Column(modifier) {
        GlassCard {
            Text(title, style = CleanType.headline, color = CleanColors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.clean_metric_heart_today_explain), style = CleanType.caption, color = CleanColors.textSecondary)
            Spacer(Modifier.height(12.dp))
            when {
                buckets == null -> Spacer(Modifier.height(CleanDims.chartHeight))
                buckets.size < 2 -> Text(
                    stringResource(R.string.clean_metric_heart_empty),
                    style = CleanType.callout,
                    color = CleanColors.textTertiary,
                )
                else -> {
                    WorkoutHrChart(buckets, zoneSet, fmt, description = title)
                    Spacer(Modifier.height(14.dp))
                    val stats = listOf(
                        R.string.clean_metric_low to buckets.minOf { it.minBpm },
                        R.string.clean_metric_avg to buckets.map { it.avgBpm }.average(),
                        R.string.clean_metric_high to buckets.maxOf { it.maxBpm },
                    )
                    Row(Modifier.fillMaxWidth()) {
                        stats.forEach { (label, bpm) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(fmt.string(R.string.clean_value_bpm, fmt.int(bpm)), style = CleanType.number, color = CleanColors.textPrimary)
                                Text(stringResource(label), style = CleanType.caption, color = CleanColors.textTertiary)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(CleanDims.rowGap))
        ClassicLinkCard(
            stringResource(R.string.clean_metric_open_live),
            stringResource(R.string.clean_metric_open_live_body),
            onOpenLive,
        )
    }
}

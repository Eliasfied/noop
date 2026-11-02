package com.noop.ui.clean

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.analytics.BaselineState
import com.noop.analytics.Baselines
import com.noop.analytics.RestScorer
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import java.time.LocalDate
import kotlin.math.abs

// MARK: - Clean Trends
//
// The same metrics over a longer view, one range for the whole page. Each card says in a sentence what
// the period averaged and how that compares with the period before, so the trend is stated, not left to
// be read off a slope.

/** Which way is good for a metric's change: up, down, or neither. */
private enum class Better { UP, DOWN, NEITHER }

@Composable
internal fun CleanTrendsScreen(vm: AppViewModel) {
    val today = rememberCleanToday(vm)
    val fmt = rememberCleanFormat()
    var range by rememberSaveable { mutableStateOf(HistoryRange.MONTH) }
    val end = LocalDate.parse(today.todayKey)
    val days = today.days
    val sleepScore: (DailyMetric) -> Double? = { today.sleepScoreOf(it) }
    val strainPick: (DailyMetric) -> Double? = { row -> row.strain?.let { round1(strainOn21(it)) } }
    val durationPick: (DailyMetric) -> Double? = { row -> row.totalSleepMin?.takeIf { it > 0 }?.let { it / 60.0 } }

    CleanScreen(
        title = stringResource(R.string.clean_tab_trends),
        subtitle = stringResource(R.string.clean_trends_subtitle),
        accent = CleanColors.trend,
    ) {
        GlassSegmented(
            options = HistoryRange.entries.map { stringResource(it.labelRes) },
            selected = range.ordinal,
            onSelect = { range = HistoryRange.entries[it] },
            accent = CleanColors.trend,
            modifier = Modifier.cleanReveal(1),
        )
        SummaryCard(days, end, range, fmt, sleepScore, strainPick, durationPick, Modifier.cleanReveal(2))

        HistoryBarsCard(
            title = stringResource(R.string.clean_recovery),
            explanation = trendExplanation(R.string.clean_trend_recovery_explain, days, end, range, fmt, { it.recovery }) {
                fmt.percent(shownPercent(it).toDouble())
            },
            days = days, end = end, range = range,
            pick = { it.recovery },
            color = ::zoneColor,
            yMax = 100.0,
            yTicks = listOf(0.0, 34.0, 67.0, 100.0),
            yLabel = { fmt.int(it) },
            valueText = { ValueText(fmt.int(shownPercent(it).toDouble()), "%") },
            fmt = fmt,
            accent = CleanColors.recoveryHigh,
            modifier = Modifier.cleanReveal(3),
        )
        HistoryBarsCard(
            title = stringResource(R.string.clean_strain),
            explanation = trendExplanation(R.string.clean_trend_strain_explain, days, end, range, fmt, strainPick) { fmt.decimal(it) },
            days = days, end = end, range = range,
            pick = strainPick,
            color = { strainLevelColor(strainLevel(it)) },
            yMax = STRAIN_MAX,
            yTicks = listOf(0.0, 10.0, 14.0, 18.0, 21.0),
            yLabel = { fmt.int(it) },
            valueText = { ValueText(fmt.decimal(it), null) },
            fmt = fmt,
            accent = CleanColors.strain,
            modifier = Modifier.cleanReveal(4),
        )
        HistoryBarsCard(
            title = stringResource(R.string.clean_sleep_performance),
            explanation = trendExplanation(R.string.clean_trend_sleep_explain, days, end, range, fmt, sleepScore) { fmt.percent(it) },
            days = days, end = end, range = range,
            pick = sleepScore,
            color = { CleanColors.sleep },
            yMax = 100.0,
            yTicks = listOf(0.0, 50.0, 100.0),
            yLabel = { fmt.int(it) },
            valueText = { ValueText(fmt.int(it), "%") },
            fmt = fmt,
            accent = CleanColors.sleep,
            modifier = Modifier.cleanReveal(5),
        )
        HistoryBarsCard(
            title = stringResource(R.string.clean_sleep_chart_title),
            explanation = trendExplanation(R.string.clean_trend_duration_explain, days, end, range, fmt, durationPick) {
                fmt.hoursMinutes(it * 60.0)
            },
            days = days, end = end, range = range,
            pick = durationPick,
            color = { h -> if (h >= RestScorer.defaultSleepNeedHours) CleanColors.sleepBright else CleanColors.sleepDeep },
            yMax = 10.0,
            yTicks = listOf(0.0, 4.0, 8.0, 12.0),
            yLabel = { fmt.string(R.string.clean_fmt_hours_short, fmt.int(it)) },
            valueText = { ValueText(fmt.hoursMinutes(it * 60.0), null) },
            fmt = fmt,
            accent = CleanColors.sleep,
            reference = RestScorer.defaultSleepNeedHours,
            referenceLabel = stringResource(R.string.clean_legend_need),
            modifier = Modifier.cleanReveal(6),
        )
        BaselineLineCard(
            title = stringResource(R.string.clean_vital_hrv),
            base = R.string.clean_trend_hrv_explain,
            days = days, end = end, range = range, fmt = fmt,
            pick = { it.avgHrv },
            state = today.baselines?.hrv,
            color = CleanColors.recoveryHigh,
            valueText = { ValueText(fmt.int(it), fmt.string(R.string.clean_unit_ms)) },
            summaryText = { fmt.string(R.string.clean_value_ms, fmt.int(it)) },
            tickLabel = { fmt.int(it) },
            modifier = Modifier.cleanReveal(7),
        )
        BaselineLineCard(
            title = stringResource(R.string.clean_vital_rhr),
            base = R.string.clean_trend_rhr_explain,
            days = days, end = end, range = range, fmt = fmt,
            pick = { it.restingHr?.toDouble() },
            state = today.baselines?.restingHR,
            color = CleanColors.recoveryLow,
            valueText = { ValueText(fmt.int(it), fmt.string(R.string.clean_unit_bpm)) },
            summaryText = { fmt.string(R.string.clean_value_bpm, fmt.int(it)) },
            tickLabel = { fmt.int(it) },
            modifier = Modifier.cleanReveal(8),
        )
        BaselineLineCard(
            title = stringResource(R.string.clean_vital_resp),
            base = R.string.clean_trend_resp_explain,
            days = days, end = end, range = range, fmt = fmt,
            pick = { it.respRateBpm },
            state = today.baselines?.resp,
            color = CleanColors.trend,
            valueText = { ValueText(fmt.decimal(it), fmt.string(R.string.clean_unit_rpm)) },
            summaryText = { fmt.string(R.string.clean_value_rpm, fmt.decimal(it)) },
            tickLabel = { fmt.int(it) },
            modifier = Modifier.cleanReveal(9),
        )
        HonestyNote(Modifier.cleanReveal(10))
    }
}

/** A card's explanation followed by one sentence on the period's average against the period before. */
@Composable
private fun trendExplanation(
    base: Int,
    days: List<DailyMetric>,
    end: LocalDate,
    range: HistoryRange,
    fmt: CleanFormat,
    pick: (DailyMetric) -> Double?,
    format: (Double) -> String,
): String {
    val summary = remember(days, end, range) { periodSummary(days, end, range.days, pick) }
    val mean = summary.mean ?: return stringResource(base)
    val prev = summary.previousMean
    val sentence = when {
        prev == null -> stringResource(R.string.clean_trend_avg_only, format(mean))
        format(mean) == format(prev) -> stringResource(R.string.clean_trend_same, format(mean), range.days)
        else -> stringResource(R.string.clean_trend_vs_prev, format(mean), format(prev), range.days)
    }
    return stringResource(base) + " " + sentence
}

@Composable
private fun BaselineLineCard(
    title: String,
    base: Int,
    days: List<DailyMetric>,
    end: LocalDate,
    range: HistoryRange,
    fmt: CleanFormat,
    pick: (DailyMetric) -> Double?,
    state: BaselineState?,
    color: Color,
    valueText: (Double) -> ValueText,
    summaryText: (Double) -> String,
    tickLabel: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val band = state?.takeIf { it.usable }?.let {
        val sigma = Baselines.sigma(it)
        (it.baseline - sigma)..(it.baseline + sigma)
    }
    HistoryLineCard(
        title = title,
        explanation = trendExplanation(base, days, end, range, fmt, pick, summaryText),
        days = days, end = end, range = range,
        pick = pick,
        color = color,
        valueText = valueText,
        tickLabel = tickLabel,
        fmt = fmt,
        band = band,
        modifier = modifier,
        legend = {
            Legend {
                if (band != null) LegendItem(color.copy(alpha = 0.35f), stringResource(R.string.clean_band_normal), band = true)
                LegendItem(CleanColors.textPrimary, stringResource(R.string.clean_legend_average), dashed = true)
            }
        },
    )
}

@Composable
private fun SummaryCard(
    days: List<DailyMetric>,
    end: LocalDate,
    range: HistoryRange,
    fmt: CleanFormat,
    sleepScore: (DailyMetric) -> Double?,
    strainPick: (DailyMetric) -> Double?,
    durationPick: (DailyMetric) -> Double?,
    modifier: Modifier = Modifier,
) {
    val recovery = remember(days, end, range) { periodSummary(days, end, range.days) { it.recovery } }
    val strain = remember(days, end, range) { periodSummary(days, end, range.days, strainPick) }
    val sleep = remember(days, end, range) { periodSummary(days, end, range.days, sleepScore) }
    val duration = remember(days, end, range) { periodSummary(days, end, range.days, durationPick) }
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_avg_period, range.days), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_vs_previous, range.days), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            SummaryTile(stringResource(R.string.clean_recovery), recovery, Better.UP, CleanColors.recoveryHigh, Modifier.weight(1f),
                value = { fmt.percent(shownPercent(it).toDouble()) }, change = { fmt.signed(it) })
            SummaryTile(stringResource(R.string.clean_strain), strain, Better.NEITHER, CleanColors.strainBright, Modifier.weight(1f),
                value = { fmt.decimal(it) }, change = { fmt.signed(it, 1) })
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            SummaryTile(stringResource(R.string.clean_sleep_performance), sleep, Better.UP, CleanColors.sleepBright, Modifier.weight(1f),
                value = { fmt.percent(it) }, change = { fmt.signed(it) })
            SummaryTile(stringResource(R.string.clean_sleep_chart_title), duration, Better.UP, CleanColors.sleep, Modifier.weight(1f),
                value = { fmt.hoursMinutes(it * 60.0) }, change = { h ->
                    val sign = if (h < 0) MINUS else "+"
                    sign + fmt.duration(abs(h) * 60.0)
                })
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    summary: PeriodSummary,
    better: Better,
    color: Color,
    modifier: Modifier = Modifier,
    value: (Double) -> String,
    change: (Double) -> String,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = CleanType.label, color = CleanColors.textTertiary, maxLines = 1)
        Text(summary.mean?.let(value) ?: "–", style = CleanType.number, color = color)
        val delta = summary.change
        if (delta != null) {
            val tone = when {
                abs(delta) < 1e-9 || better == Better.NEITHER -> CleanColors.textSecondary
                (delta > 0) == (better == Better.UP) -> CleanColors.good
                else -> CleanColors.watch
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (abs(delta) >= 1e-9) {
                    Icon(
                        if (delta > 0) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(change(delta), style = CleanType.caption, color = tone)
            }
        } else {
            Text(stringResource(R.string.clean_no_previous), style = CleanType.caption, color = CleanColors.textTertiary)
        }
    }
}

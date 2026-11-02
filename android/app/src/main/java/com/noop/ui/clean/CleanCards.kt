package com.noop.ui.clean

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.data.DailyMetric
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

// MARK: - History cards
//
// The day-series cards every clean page shares. A card shows one metric over a chosen range, its period
// average as a dashed line, and the selected (or summary) value in the headline, so a page reads the same
// way wherever it is.

/** The ranges a history card can show. */
internal enum class HistoryRange(val days: Int, val labelRes: Int) {
    WEEK(7, R.string.clean_range_7),
    MONTH(30, R.string.clean_range_30),
    QUARTER(90, R.string.clean_range_90),
}

/** The x-axis label for day [i] of [n]: weekdays for a week, thinned dates beyond that. */
internal fun axisLabel(i: Int, n: Int, day: LocalDate, fmt: CleanFormat): String? =
    if (n <= 7) fmt.weekday(day) else thinnedLabel(i, n, labelStep(n)) { fmt.axisDate(day) }

/** A value with its unit, split so the headline can size them differently. */
internal data class ValueText(val value: String, val unit: String?)

@Composable
private fun RangePicker(ranges: List<HistoryRange>, range: HistoryRange, onRange: (HistoryRange) -> Unit, accent: Color) {
    if (ranges.size < 2) return
    GlassSegmented(
        options = ranges.map { stringResource(it.labelRes) },
        selected = ranges.indexOf(range).coerceAtLeast(0),
        onSelect = { onRange(ranges[it]) },
        accent = accent,
    )
    Spacer(Modifier.height(14.dp))
}

/**
 * One metric as daily bars over [range], with its period average dashed across.
 *
 * @param color the colour of a bar for its value (zones, levels, or one domain colour).
 * @param valueText how a value reads in the headline.
 * @param reference an optional second dashed line (a target), labelled [referenceLabel].
 */
@Composable
internal fun HistoryBarsCard(
    title: String,
    explanation: String,
    days: List<DailyMetric>,
    end: LocalDate,
    range: HistoryRange,
    pick: (DailyMetric) -> Double?,
    color: (Double) -> Color,
    yMax: Double,
    yTicks: List<Double>,
    yLabel: (Double) -> String,
    valueText: (Double) -> ValueText,
    fmt: CleanFormat,
    accent: Color,
    modifier: Modifier = Modifier,
    ranges: List<HistoryRange> = emptyList(),
    onRange: (HistoryRange) -> Unit = {},
    reference: Double? = null,
    referenceLabel: String? = null,
    legend: (@Composable () -> Unit)? = null,
) {
    val values = remember(days, end, range) { dayValues(days, end, range.days, pick) }
    val present = values.mapNotNull { it.value }
    val average = present.takeIf { it.isNotEmpty() }?.average()
    val noData = stringResource(R.string.clean_no_data_day)
    val avgCaption = stringResource(R.string.clean_avg_period, range.days)
    val emptyCaption = stringResource(R.string.clean_no_data_period)
    val maxValue = max(yMax, (present.maxOrNull() ?: 0.0))
    ChartCard(
        title = title,
        explanation = explanation,
        resetKey = range to values,
        modifier = modifier,
        legend = legend,
        headline = { selected ->
            if (selected != null) {
                val dv = values[selected]
                val v = dv.value
                if (v == null) {
                    ChartHeadline("–", null, "${fmt.longDate(dv.day)} · $noData", CleanColors.textTertiary)
                } else {
                    val t = valueText(v)
                    ChartHeadline(t.value, t.unit, fmt.longDate(dv.day), color(v))
                }
            } else if (average != null) {
                val t = valueText(average)
                ChartHeadline("Ø ${t.value}", t.unit, avgCaption, CleanColors.textPrimary)
            } else {
                ChartHeadline("–", null, emptyCaption, CleanColors.textTertiary)
            }
        },
    ) { selected, onSelect ->
        RangePicker(ranges, range, onRange, accent)
        GlassBarChart(
            points = values.map { BarPoint(it.value, it.value?.let(color) ?: CleanColors.track) },
            yMax = maxValue,
            yTicks = yTicks,
            yLabel = yLabel,
            xLabel = { i -> axisLabel(i, values.size, values[i].day, fmt) },
            selected = selected,
            onSelect = onSelect,
            average = average,
            averageLabel = average?.let { "Ø ${valueText(it).value}" },
            reference = reference,
            referenceLabel = referenceLabel,
            referenceColor = accent,
            description = title,
        )
    }
}

/**
 * One metric as a daily line over [range], with the wearer's normal range shaded as [band] when known.
 */
@Composable
internal fun HistoryLineCard(
    title: String,
    explanation: String,
    days: List<DailyMetric>,
    end: LocalDate,
    range: HistoryRange,
    pick: (DailyMetric) -> Double?,
    color: Color,
    valueText: (Double) -> ValueText,
    tickLabel: (Double) -> String,
    fmt: CleanFormat,
    modifier: Modifier = Modifier,
    band: ClosedFloatingPointRange<Double>? = null,
    ranges: List<HistoryRange> = emptyList(),
    onRange: (HistoryRange) -> Unit = {},
    legend: (@Composable () -> Unit)? = null,
) {
    val values = remember(days, end, range) { dayValues(days, end, range.days, pick) }
    val present = values.mapNotNull { it.value }
    val average = present.takeIf { it.isNotEmpty() }?.average()
    val noData = stringResource(R.string.clean_no_data_day)
    val avgCaption = stringResource(R.string.clean_avg_period, range.days)
    val emptyCaption = stringResource(R.string.clean_no_data_period)
    val bandLabel = stringResource(R.string.clean_band_normal)
    // The y range covers every value and the band, with a little headroom either side.
    val lo = listOfNotNull(present.minOrNull(), band?.start).minOrNull() ?: 0.0
    val hi = listOfNotNull(present.maxOrNull(), band?.endInclusive).maxOrNull() ?: 1.0
    val pad = max((hi - lo) * 0.18, 1.0)
    val ticks = niceTicks(hi + pad, max(0.0, lo - pad))
    val yMin = min(ticks.firstOrNull() ?: lo, lo - pad * 0.5)
    val yMax = max(ticks.lastOrNull() ?: hi, hi + pad * 0.5)
    ChartCard(
        title = title,
        explanation = explanation,
        resetKey = range to values,
        modifier = modifier,
        legend = legend,
        headline = { selected ->
            if (selected != null) {
                val dv = values[selected]
                val v = dv.value
                if (v == null) {
                    ChartHeadline("–", null, "${fmt.longDate(dv.day)} · $noData", CleanColors.textTertiary)
                } else {
                    val t = valueText(v)
                    ChartHeadline(t.value, t.unit, fmt.longDate(dv.day), color)
                }
            } else if (average != null) {
                val t = valueText(average)
                ChartHeadline("Ø ${t.value}", t.unit, avgCaption, CleanColors.textPrimary)
            } else {
                ChartHeadline("–", null, emptyCaption, CleanColors.textTertiary)
            }
        },
    ) { selected, onSelect ->
        RangePicker(ranges, range, onRange, color)
        GlassLineChart(
            points = values.map { LinePoint(it.value) },
            yMin = yMin,
            yMax = yMax,
            yTicks = ticks.filter { it in yMin..yMax },
            yLabel = tickLabel,
            xLabel = { i -> axisLabel(i, values.size, values[i].day, fmt) },
            color = color,
            selected = selected,
            onSelect = onSelect,
            band = band,
            bandLabel = band?.let { bandLabel },
            average = average,
            averageLabel = average?.let { "Ø ${valueText(it).value}" },
            description = title,
        )
    }
}

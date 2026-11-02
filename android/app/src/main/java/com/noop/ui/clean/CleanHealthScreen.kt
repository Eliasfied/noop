package com.noop.ui.clean

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.IllnessSignalEngine
import com.noop.analytics.VitalityEngine
import com.noop.data.DailyMetric
import com.noop.data.MetricSeriesRow
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Clean Health
//
// The long view: Body Age and Vitality (the weekly VitalityEngine values IntelligenceEngine stores), what
// moves them, Fitness Age and VO2max (FitnessAgeEngine, also stored weekly), and the illness heads-up the
// AppViewModel already evaluates (V5HealthSignals). This page only READS those stored results, exactly as
// the classic Health hub does, and never recomputes a headline.

/** Everything the health page shows, read from the stored weekly series. */
internal data class HealthRead(
    val bodyAge: MetricSeriesRow?,
    val vitality: MetricSeriesRow?,
    val bodyAgeSeries: List<MetricSeriesRow>,
    val fitnessAge: MetricSeriesRow?,
    val vo2max: MetricSeriesRow?,
    val vo2maxSeries: List<MetricSeriesRow>,
)

@Composable
internal fun rememberHealthRead(vm: AppViewModel, days: List<DailyMetric>): HealthRead? {
    var read by remember { mutableStateOf<HealthRead?>(null) }
    LaunchedEffect(days, vm.activeStrapId) {
        read = runCatching {
            val id = vm.activeStrapId
            HealthRead(
                bodyAge = vm.repo.latestMetricComputedUnion(id, "body_age"),
                vitality = vm.repo.latestMetricComputedUnion(id, "vitality"),
                bodyAgeSeries = vm.repo.metricSeriesComputedUnion(id, "body_age", "0000-01-01", "9999-12-31").sortedBy { it.day },
                fitnessAge = vm.repo.latestMetricComputedUnion(id, "fitness_age"),
                vo2max = vm.repo.latestMetricComputedUnion(id, "vo2max_est"),
                vo2maxSeries = vm.repo.metricSeriesComputedUnion(id, "vo2max_est", "0000-01-01", "9999-12-31").sortedBy { it.day },
            )
        }.getOrNull()
    }
    return read
}

/**
 * The "what moves it" rows for Vitality, computed exactly as the classic Health hub computes them (median
 * resting HR and HRV, mean sleep and steps over the last 7 rows), so the two pages explain the stored
 * number the same way.
 */
internal fun vitalityContributions(days: List<DailyMetric>, age: Int): List<VitalityEngine.Contribution> {
    val last7 = days.takeLast(7)
    val nights = last7.mapNotNull { it.totalSleepMin }.map { it / 60.0 }.filter { it > 0 }
    val hrvs = last7.mapNotNull { it.avgHrv }
    val rhrs = last7.mapNotNull { it.restingHr }.map { it.toDouble() }
    val steps = last7.mapNotNull { it.steps }.map { it.toDouble() }
    fun mean(a: List<Double>): Double? = if (a.isEmpty()) null else a.average()
    fun median(a: List<Double>): Double? {
        if (a.isEmpty()) return null
        val s = a.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }
    return VitalityEngine.contributions(
        VitalityEngine.Inputs(
            chronoAge = age.toDouble(),
            restingHR = median(rhrs),
            sleepHours = mean(nights),
            sleepConsistency = VitalityEngine.sleepConsistency(nights),
            rmssd = median(hrvs),
            rmssdNorm = VitalityEngine.rmssdNorm(age.toDouble()),
            steps = mean(steps),
        ),
    )
}

/** The change of a weekly series over roughly [days] days: latest minus the last value at least that old. */
internal fun seriesChange(series: List<MetricSeriesRow>, days: Long = 30): Double? {
    val latest = series.lastOrNull() ?: return null
    val cutoff = LocalDate.parse(latest.day).minusDays(days).toString()
    val before = series.lastOrNull { it.day <= cutoff } ?: return null
    return latest.value - before.value
}

@Composable
private fun factorName(key: String, fallback: String): String = when (key) {
    "rhr" -> stringResource(R.string.clean_vital_rhr)
    "vo2max" -> stringResource(R.string.clean_health_factor_vo2)
    "sleep" -> stringResource(R.string.clean_vital_sleep)
    "consistency" -> stringResource(R.string.clean_part_regularity)
    "hrv" -> stringResource(R.string.clean_vital_hrv)
    "steps" -> stringResource(R.string.clean_steps)
    else -> fallback
}

internal fun illnessColor(level: IllnessSignalEngine.Level): Color = when (level) {
    IllnessSignalEngine.Level.QUIET -> CleanColors.good
    IllnessSignalEngine.Level.MILD, IllnessSignalEngine.Level.SUPPRESSED -> CleanColors.watch
    IllnessSignalEngine.Level.RAISED, IllnessSignalEngine.Level.ALREADY_UNWELL -> CleanColors.bad
}

@Composable
internal fun illnessTitle(level: IllnessSignalEngine.Level): String = stringResource(
    when (level) {
        IllnessSignalEngine.Level.QUIET -> R.string.clean_illness_quiet_title
        IllnessSignalEngine.Level.MILD -> R.string.clean_illness_mild_title
        IllnessSignalEngine.Level.RAISED -> R.string.clean_illness_raised_title
        IllnessSignalEngine.Level.SUPPRESSED -> R.string.clean_illness_suppressed_title
        IllnessSignalEngine.Level.ALREADY_UNWELL -> R.string.clean_illness_unwell_title
    },
)

@Composable
private fun illnessBody(level: IllnessSignalEngine.Level): String = stringResource(
    when (level) {
        IllnessSignalEngine.Level.QUIET -> R.string.clean_illness_quiet_body
        IllnessSignalEngine.Level.MILD -> R.string.clean_illness_mild_body
        IllnessSignalEngine.Level.RAISED -> R.string.clean_illness_raised_body
        IllnessSignalEngine.Level.SUPPRESSED -> R.string.clean_illness_suppressed_body
        IllnessSignalEngine.Level.ALREADY_UNWELL -> R.string.clean_illness_unwell_body
    },
)

/** The engine's own signal phrases, localised; an unknown phrase passes through. */
@Composable
private fun signalLabel(raw: String): String = when (raw) {
    "RHR up" -> stringResource(R.string.clean_illness_signal_rhr)
    "skin temp up" -> stringResource(R.string.clean_illness_signal_skin)
    "HRV down" -> stringResource(R.string.clean_illness_signal_hrv)
    "respiration up" -> stringResource(R.string.clean_illness_signal_resp)
    else -> raw
}

@Composable
internal fun CleanHealthScreen(vm: AppViewModel, onBack: () -> Unit, onOpenClassic: () -> Unit, onAsk: (String) -> Unit) {
    val fmt = rememberCleanFormat()
    val context = LocalContext.current
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val signals by vm.v5Signals.collectAsStateWithLifecycle()
    val read = rememberHealthRead(vm, days)
    val age = remember { ProfileStore.from(context).age }
    val contributions = remember(days, age) { vitalityContributions(days, age) }

    CleanScreen(
        title = stringResource(R.string.clean_health_title),
        subtitle = stringResource(R.string.clean_health_subtitle),
        accent = CleanColors.trend,
        onBack = onBack,
    ) {
        BodyAgeHero(read, age, fmt, Modifier.cleanReveal(1))
        read?.bodyAge?.value?.takeIf { age > 0 }?.let {
            AskCoachButton(stringResource(R.string.clean_coach_q_health, fmt.decimal(it), age), onAsk, Modifier.cleanReveal(1))
        }
        if (contributions.isNotEmpty() && read?.bodyAge != null) {
            ContributionsCard(contributions, Modifier.cleanReveal(2))
        }
        read?.bodyAgeSeries?.takeIf { it.size >= 2 }?.let { series ->
            WeeklySeriesCard(
                title = stringResource(R.string.clean_health_body_age_trend),
                explanation = stringResource(R.string.clean_health_body_age_trend_explain),
                series = series,
                color = CleanColors.trend,
                valueText = { ValueText(fmt.decimal(it), stringResource0(fmt, R.string.clean_years)) },
                fmt = fmt,
                modifier = Modifier.cleanReveal(3),
            )
        }
        FitnessCard(read, fmt, Modifier.cleanReveal(4))
        read?.vo2maxSeries?.takeIf { it.size >= 2 }?.let { series ->
            WeeklySeriesCard(
                title = stringResource(R.string.clean_health_vo2_trend),
                explanation = stringResource(R.string.clean_health_vo2_trend_explain),
                series = series,
                color = CleanColors.strainBright,
                valueText = { ValueText(fmt.decimal(it), stringResource0(fmt, R.string.clean_unit_vo2)) },
                fmt = fmt,
                modifier = Modifier.cleanReveal(5),
            )
        }
        IllnessCard(signals?.illness, Modifier.cleanReveal(6))
        ClassicLinkCard(
            stringResource(R.string.clean_health_classic_title),
            stringResource(R.string.clean_health_classic_body),
            onOpenClassic,
            Modifier.cleanReveal(7),
        )
        HonestyNote(Modifier.cleanReveal(8))
    }
}

@Composable
private fun BodyAgeHero(read: HealthRead?, age: Int, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val bodyAge = read?.bodyAge?.value
    GlassCard(modifier, tint = CleanColors.trend) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.clean_health_body_age).uppercase(), style = CleanType.label, color = CleanColors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Text(bodyAge?.let { fmt.decimal(it) } ?: "–", style = CleanType.heroNumber, color = CleanColors.textPrimary)
            if (bodyAge == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.clean_health_body_age_none),
                    style = CleanType.callout,
                    color = CleanColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }
            if (age > 0) {
                val delta = age - bodyAge
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        abs(delta) < 0.05 -> stringResource(R.string.clean_health_same_age, age)
                        delta > 0 -> stringResource(R.string.clean_health_younger, fmt.decimal(delta), age)
                        else -> stringResource(R.string.clean_health_older, fmt.decimal(-delta), age)
                    },
                    style = CleanType.headline,
                    color = if (delta >= 0) CleanColors.good else CleanColors.watch,
                    textAlign = TextAlign.Center,
                )
            }
            read.bodyAgeSeries.let(::seriesChange)?.let { change ->
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.clean_health_change_30, fmt.signed(change, 1)),
                    style = CleanType.callout,
                    color = CleanColors.textSecondary,
                )
            }
            read.vitality?.value?.let { v ->
                Spacer(Modifier.height(12.dp))
                GlassChip(stringResource(R.string.clean_health_vitality, v.roundToInt()), CleanColors.trend)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.clean_health_body_age_explain, fmt.dayMonth(LocalDate.parse(read.bodyAge!!.day))),
                style = CleanType.caption,
                color = CleanColors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ContributionsCard(contributions: List<VitalityEngine.Contribution>, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_health_drivers_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_health_drivers_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.clean_health_ages), style = CleanType.micro, color = CleanColors.watch, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.clean_health_rejuvenates), style = CleanType.micro, color = CleanColors.good)
        }
        Spacer(Modifier.height(6.dp))
        val sorted = contributions.sortedByDescending { abs(it.lnHazard) }
        val maxAbs = sorted.maxOf { abs(it.lnHazard) }.coerceAtLeast(1e-6)
        sorted.forEachIndexed { i, c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(factorName(c.key, c.label), style = CleanType.body, color = CleanColors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    stringResource(if (c.lnHazard <= 0) R.string.clean_health_helps else R.string.clean_health_hurts),
                    style = CleanType.caption,
                    color = if (c.lnHazard <= 0) CleanColors.good else CleanColors.watch,
                )
            }
            Spacer(Modifier.height(6.dp))
            // Negative hazard keeps you younger, so it grows to the right like every other "helps" bar.
            DriverBar((-c.lnHazard / maxAbs * 100).roundToInt(), 100)
            if (i < sorted.lastIndex) HorizontalDivider(color = CleanColors.hairline, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

/** A weekly stored series as a line, one point per stored week, labelled by its date. */
@Composable
private fun WeeklySeriesCard(
    title: String,
    explanation: String,
    series: List<MetricSeriesRow>,
    color: Color,
    valueText: (Double) -> ValueText,
    fmt: CleanFormat,
    modifier: Modifier = Modifier,
) {
    val shown = series.takeLast(16)
    val values = shown.map { it.value }
    val lo = values.min()
    val hi = values.max()
    val pad = kotlin.math.max((hi - lo) * 0.25, 0.5)
    val ticks = niceTicks(hi + pad, lo - pad)
    ChartCard(
        title = title,
        explanation = explanation,
        resetKey = shown,
        modifier = modifier,
        headline = { selected ->
            val row = selected?.let { shown[it] } ?: shown.last()
            val t = valueText(row.value)
            ChartHeadline(t.value, t.unit, fmt.longDate(LocalDate.parse(row.day)), color)
        },
    ) { selected, onSelect ->
        GlassLineChart(
            points = shown.map { LinePoint(it.value) },
            yMin = kotlin.math.min(ticks.firstOrNull() ?: lo, lo - pad),
            yMax = kotlin.math.max(ticks.lastOrNull() ?: hi, hi + pad),
            yTicks = ticks,
            yLabel = { fmt.decimal(it, 0) },
            xLabel = { i -> if (i == 0 || i == shown.lastIndex || i == shown.size / 2) fmt.axisDate(LocalDate.parse(shown[i].day)) else null },
            color = color,
            selected = selected,
            onSelect = onSelect,
            description = title,
        )
    }
}

@Composable
private fun FitnessCard(read: HealthRead?, fmt: CleanFormat, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_health_fitness_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_health_fitness_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    read?.fitnessAge?.value?.let { fmt.string(R.string.clean_value_years, fmt.int(it)) } ?: "–",
                    style = CleanType.bigNumber,
                    color = CleanColors.textPrimary,
                )
                Text(stringResource(R.string.clean_health_fitness_age), style = CleanType.caption, color = CleanColors.textTertiary)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(read?.vo2max?.value?.let { fmt.decimal(it) } ?: "–", style = CleanType.bigNumber, color = CleanColors.strainBright)
                    if (read?.vo2max != null) {
                        Text(
                            " " + stringResource(R.string.clean_unit_vo2),
                            style = CleanType.unit,
                            color = CleanColors.textSecondary,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Text(stringResource(R.string.clean_health_vo2), style = CleanType.caption, color = CleanColors.textTertiary)
            }
        }
        if (read?.fitnessAge == null && read?.vo2max == null) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.clean_health_fitness_none), style = CleanType.callout, color = CleanColors.textTertiary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IllnessCard(result: IllnessSignalEngine.Result?, modifier: Modifier = Modifier) {
    val level = result?.level
    GlassCard(modifier, tint = level?.let(::illnessColor)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.HealthAndSafety,
                null,
                tint = level?.let(::illnessColor) ?: CleanColors.textSecondary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.clean_illness_label).uppercase(), style = CleanType.label, color = CleanColors.textTertiary)
                Text(
                    level?.let { illnessTitle(it) } ?: stringResource(R.string.clean_loading),
                    style = CleanType.title,
                    color = CleanColors.textPrimary,
                )
            }
        }
        if (level != null) {
            Spacer(Modifier.height(8.dp))
            Text(illnessBody(level), style = CleanType.callout, color = CleanColors.textSecondary)
            if (result.firedSignals.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.firedSignals.forEach { GlassChip(signalLabel(it), illnessColor(level)) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.clean_illness_disclaimer), style = CleanType.caption, color = CleanColors.textTertiary)
    }
}

/** The compact Home row for the health page: Body Age and the heads-up level at a glance. */
@Composable
internal fun HealthGlanceCard(vm: AppViewModel, days: List<DailyMetric>, fmt: CleanFormat, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val read = rememberHealthRead(vm, days)
    val signals by vm.v5Signals.collectAsStateWithLifecycle()
    val age = remember { ProfileStore.from(context).age }
    val level = signals?.illness?.level
    GlassCard(modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.clean_health_title).uppercase(), style = CleanType.label, color = CleanColors.textTertiary)
                Spacer(Modifier.height(4.dp))
                val bodyAge = read?.bodyAge?.value
                Text(
                    if (bodyAge != null) stringResource(R.string.clean_health_glance_age, fmt.decimal(bodyAge))
                    else stringResource(R.string.clean_health_glance_none),
                    style = CleanType.headline,
                    color = CleanColors.textPrimary,
                )
                if (bodyAge != null && age > 0) {
                    val delta = age - bodyAge
                    Text(
                        if (delta >= 0) stringResource(R.string.clean_health_younger, fmt.decimal(delta), age)
                        else stringResource(R.string.clean_health_older, fmt.decimal(-delta), age),
                        style = CleanType.caption,
                        color = if (delta >= 0) CleanColors.good else CleanColors.watch,
                    )
                }
            }
            if (level != null) {
                GlassChip(illnessTitle(level), illnessColor(level))
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

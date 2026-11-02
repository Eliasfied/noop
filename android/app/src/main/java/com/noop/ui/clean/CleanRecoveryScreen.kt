package com.noop.ui.clean

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.analytics.ChargeDriver
import com.noop.analytics.ChargeDriverLabel
import com.noop.analytics.RecoveryScorer
import com.noop.ui.AppViewModel
import com.noop.ui.recoveryChargeDrivers
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Clean Recovery detail
//
// The recovery dial from Home, opened up: the score and how it compares with the wearer's own month,
// what moved it (the same driver rows the classic Charge breakdown scores), how it has gone lately, and
// how the number is put together.

internal fun zoneColor(raw: Double): Color = recoveryDialColors(raw).bright

@Composable
internal fun zoneName(zone: RecoveryZone): String = stringResource(
    when (zone) {
        RecoveryZone.HIGH -> R.string.clean_zone_high
        RecoveryZone.MID -> R.string.clean_zone_mid
        RecoveryZone.LOW -> R.string.clean_zone_low
    },
)

@Composable
internal fun CleanRecoveryScreen(vm: AppViewModel, onBack: () -> Unit, onAsk: (String) -> Unit) {
    val today = rememberCleanToday(vm)
    val fmt = rememberCleanFormat()
    var range by rememberSaveable { mutableStateOf(HistoryRange.WEEK) }
    val scored = today.recovery as? RecoveryRead.Scored
    val end = LocalDate.parse(today.todayKey)
    val subtitle = scored?.let { fmt.longDate(LocalDate.parse(it.row.day)) }

    CleanScreen(
        title = stringResource(R.string.clean_recovery),
        subtitle = subtitle,
        accent = recoveryAccent(today.recoveryValue),
        onBack = onBack,
    ) {
        RecoveryHero(today, fmt, Modifier.cleanReveal(1))
        if (scored != null) AskCoachButton(recoveryQuestion(today, fmt), onAsk, Modifier.cleanReveal(1))
        if (scored != null) {
            DriversCard(today, scored, fmt, Modifier.cleanReveal(2))
        }
        HistoryBarsCard(
            title = stringResource(R.string.clean_history),
            explanation = stringResource(R.string.clean_recovery_chart_explain),
            days = today.days,
            end = end,
            range = range,
            ranges = listOf(HistoryRange.WEEK, HistoryRange.MONTH, HistoryRange.QUARTER),
            onRange = { range = it },
            pick = { it.recovery },
            color = ::zoneColor,
            yMax = 100.0,
            yTicks = listOf(0.0, 34.0, 67.0, 100.0),
            yLabel = { fmt.int(it) },
            valueText = { ValueText(fmt.int(shownPercent(it).toDouble()), "%") },
            fmt = fmt,
            accent = recoveryAccent(today.recoveryValue),
            modifier = Modifier.cleanReveal(3),
            legend = {
                Legend {
                    LegendItem(CleanColors.recoveryHigh, stringResource(R.string.clean_legend_zone_high))
                    LegendItem(CleanColors.recoveryMid, stringResource(R.string.clean_legend_zone_mid))
                    LegendItem(CleanColors.recoveryLow, stringResource(R.string.clean_legend_zone_low))
                    LegendItem(CleanColors.textPrimary, stringResource(R.string.clean_legend_average), dashed = true)
                }
            },
        )
        val hrvBase = today.baselines?.hrv?.takeIf { it.usable }
        HistoryLineCard(
            title = stringResource(R.string.clean_hrv_chart_title),
            explanation = stringResource(R.string.clean_hrv_chart_explain),
            days = today.days,
            end = end,
            range = range,
            pick = { it.avgHrv },
            color = CleanColors.recoveryHigh,
            valueText = { ValueText(fmt.int(it), stringResource0(fmt, R.string.clean_unit_ms)) },
            tickLabel = { fmt.int(it) },
            fmt = fmt,
            band = hrvBase?.let {
                val sigma = com.noop.analytics.Baselines.sigma(it)
                (it.baseline - sigma)..(it.baseline + sigma)
            },
            modifier = Modifier.cleanReveal(4),
            legend = {
                Legend {
                    LegendItem(CleanColors.recoveryHigh.copy(alpha = 0.35f), stringResource(R.string.clean_band_normal), band = true)
                    LegendItem(CleanColors.textPrimary, stringResource(R.string.clean_legend_average), dashed = true)
                }
            },
        )
        HowRecoveryWorksCard(fmt, Modifier.cleanReveal(5))
        HonestyNote(Modifier.cleanReveal(6))
    }
}

/** A plain resource string through the formatter (for use inside non-composable lambdas). */
internal fun stringResource0(fmt: CleanFormat, id: Int): String = fmt.string(id)

@Composable
private fun RecoveryHero(today: CleanToday, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val recovery = today.recovery
    val value = (recovery as? RecoveryRead.Scored)?.value
    GlassCard(modifier, tint = value?.let(::zoneColor)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreDial(
                fraction = value?.let { shownPercent(it) / 100f },
                colors = recoveryDialColors(value),
                size = CleanDims.heroDial,
                stroke = CleanDims.heroDialStroke,
            ) { reveal ->
                if (value != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text((shownPercent(value) * reveal).roundToInt().toString(), style = CleanType.heroNumber, color = CleanColors.textPrimary)
                            Text("%", style = CleanType.title, color = CleanColors.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
                        }
                        Text(stringResource(R.string.clean_recovery).uppercase(), style = CleanType.label, color = CleanColors.textSecondary)
                    }
                } else {
                    Icon(Icons.Rounded.SelfImprovement, null, tint = CleanColors.textSecondary, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            when (recovery) {
                is RecoveryRead.Scored -> {
                    val zone = recoveryZone(recovery.value)
                    GlassChip(zoneName(zone), zoneColor(recovery.value))
                    Spacer(Modifier.height(10.dp))
                    val day = LocalDate.parse(recovery.row.day)
                    val mean = trailingMean(today.days, day, 30) { it.recovery }
                    if (mean != null) {
                        val diff = shownPercent(recovery.value) - shownPercent(mean)
                        val meanText = fmt.percent(shownPercent(mean).toDouble())
                        Text(
                            when {
                                diff > 0 -> stringResource(R.string.clean_vs_average_above, diff, meanText)
                                diff < 0 -> stringResource(R.string.clean_vs_average_below, -diff, meanText)
                                else -> stringResource(R.string.clean_vs_average_same, meanText)
                            },
                            style = CleanType.callout,
                            color = CleanColors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (recovery.carried) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                if (recovery.stale) R.string.clean_carried_stale else R.string.clean_carried_fresh,
                                fmt.dayMonth(day),
                            ),
                            style = CleanType.caption,
                            color = CleanColors.textTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                is RecoveryRead.Calibrating -> {
                    Text(stringResource(R.string.clean_dial_calibrating), style = CleanType.headline, color = CleanColors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(calibratingText(recovery.nightsRemaining), style = CleanType.callout, color = CleanColors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.clean_calibrating_explain), style = CleanType.caption, color = CleanColors.textTertiary, textAlign = TextAlign.Center)
                }
                RecoveryRead.NoData -> {
                    Text(stringResource(R.string.clean_recovery_no_data), style = CleanType.callout, color = CleanColors.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun driverName(label: ChargeDriverLabel): String = stringResource(
    when (label) {
        ChargeDriverLabel.HEART_RATE_VARIABILITY -> R.string.clean_vital_hrv
        ChargeDriverLabel.RESTING_HEART_RATE -> R.string.clean_vital_rhr
        ChargeDriverLabel.SLEEP_QUALITY -> R.string.clean_sleep_performance
        ChargeDriverLabel.RESPIRATORY_RATE -> R.string.clean_vital_resp
        ChargeDriverLabel.SKIN_TEMPERATURE -> R.string.clean_vital_skin
    },
)

/** The driver's measured value against what it was scored against, in words. */
@Composable
private fun driverDetail(d: ChargeDriver, fmt: CleanFormat): String {
    val value = when (d.label) {
        ChargeDriverLabel.HEART_RATE_VARIABILITY -> vitalValue(VitalKind.HRV, d.value, fmt)
        ChargeDriverLabel.RESTING_HEART_RATE -> vitalValue(VitalKind.RESTING_HR, d.value, fmt)
        ChargeDriverLabel.RESPIRATORY_RATE -> vitalValue(VitalKind.RESPIRATION, d.value, fmt)
        ChargeDriverLabel.SLEEP_QUALITY -> fmt.percent(d.value)
        ChargeDriverLabel.SKIN_TEMPERATURE -> vitalValue(VitalKind.SKIN_TEMP, d.value, fmt)
    }
    val base = d.baseline
    return when {
        d.label == ChargeDriverLabel.SLEEP_QUALITY ->
            stringResource(R.string.clean_driver_sleep_detail, value, fmt.percent(RecoveryScorer.sleepPerfCenter * 100.0))
        d.label == ChargeDriverLabel.SKIN_TEMPERATURE ->
            stringResource(R.string.clean_driver_skin_detail, value)
        base != null -> {
            val baseText = when (d.label) {
                ChargeDriverLabel.HEART_RATE_VARIABILITY -> vitalValue(VitalKind.HRV, base, fmt)
                ChargeDriverLabel.RESTING_HEART_RATE -> vitalValue(VitalKind.RESTING_HR, base, fmt)
                else -> vitalValue(VitalKind.RESPIRATION, base, fmt)
            }
            stringResource(R.string.clean_driver_vs_normal, value, baseText)
        }
        else -> value
    }
}

@Composable
private fun DriversCard(today: CleanToday, scored: RecoveryRead.Scored, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val drivers = remember(today.baselines, scored.row) { recoveryChargeDrivers(today.baselines, scored.row) }
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_drivers_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_drivers_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(14.dp))
        if (drivers.isEmpty()) {
            Text(stringResource(R.string.clean_drivers_empty), style = CleanType.callout, color = CleanColors.textTertiary)
            return@GlassCard
        }
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.clean_drivers_lowered), style = CleanType.micro, color = CleanColors.watch, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.clean_drivers_lifted), style = CleanType.micro, color = CleanColors.good)
        }
        Spacer(Modifier.height(8.dp))
        val maxAbs = drivers.maxOf { abs(it.deltaPoints) }.coerceAtLeast(5)
        drivers.forEachIndexed { i, d ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(driverName(d.label), style = CleanType.body, color = CleanColors.textPrimary)
                        Text(driverDetail(d, fmt), style = CleanType.caption, color = CleanColors.textTertiary)
                    }
                    val points = d.deltaPoints
                    Text(
                        stringResource(R.string.clean_points, fmt.signed(points.toDouble())),
                        style = CleanType.smallNumber,
                        color = when {
                            points > 0 -> CleanColors.good
                            points < 0 -> CleanColors.watch
                            else -> CleanColors.textSecondary
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                DriverBar(d.deltaPoints, maxAbs)
            }
            if (i < drivers.lastIndex) HorizontalDivider(color = CleanColors.hairline, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

@Composable
private fun HowRecoveryWorksCard(fmt: CleanFormat, modifier: Modifier = Modifier) {
    val parts = listOf(
        Triple(R.string.clean_vital_hrv, RecoveryScorer.wHRV, CleanColors.recoveryHigh),
        Triple(R.string.clean_vital_rhr, RecoveryScorer.wRHR, CleanColors.recoveryLow),
        Triple(R.string.clean_sleep_performance, RecoveryScorer.wSleep, CleanColors.sleep),
        Triple(R.string.clean_vital_resp, RecoveryScorer.wResp, CleanColors.trend),
        Triple(R.string.clean_vital_skin, RecoveryScorer.wSkinTemp, CleanColors.watch),
    )
    val total = parts.sumOf { it.second }
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_recovery_how_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.clean_recovery_how_body), style = CleanType.callout, color = CleanColors.textSecondary)
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(CleanDims.pillRadius))) {
            var x = 0f
            val gap = 2.dp.toPx()
            parts.forEach { (_, w, c) ->
                val width = (w / total).toFloat() * size.width
                drawRect(c, topLeft = Offset(x, 0f), size = Size((width - gap).coerceAtLeast(0f), size.height))
                x += width
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { (name, w, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(c)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(name), style = CleanType.callout, color = CleanColors.textPrimary, modifier = Modifier.weight(1f))
                    Text(fmt.percent(w / total * 100.0), style = CleanType.smallNumber, color = CleanColors.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.clean_weight_extra), style = CleanType.caption, color = CleanColors.textTertiary)
    }
}

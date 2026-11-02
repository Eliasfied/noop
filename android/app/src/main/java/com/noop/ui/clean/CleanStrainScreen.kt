package com.noop.ui.clean

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FitnessCenter
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.data.WorkoutRow
import com.noop.ui.AppViewModel
import com.noop.ui.WorkoutEditing
import com.noop.ui.localDayString
import java.time.LocalDate

// MARK: - Clean Strain detail
//
// Today's strain on the 0-21 axis, set against the band today's recovery suggests, what produced it, and
// how the last weeks compare. The level bands and the target band are the same ones Home draws.

@Composable
internal fun strainLevelName(level: StrainLevel): String = stringResource(
    when (level) {
        StrainLevel.LIGHT -> R.string.clean_level_light
        StrainLevel.MODERATE -> R.string.clean_level_moderate
        StrainLevel.HIGH -> R.string.clean_level_high
        StrainLevel.ALL_OUT -> R.string.clean_level_all_out
    },
)

@Composable
internal fun CleanStrainScreen(vm: AppViewModel, onBack: () -> Unit, onOpenWorkouts: () -> Unit) {
    val today = rememberCleanToday(vm)
    val fmt = rememberCleanFormat()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(HistoryRange.WEEK) }
    val end = LocalDate.parse(today.todayKey)
    val todays = remember(workouts, today.todayKey) {
        workouts.filter { localDayString(it.startTs) == today.todayKey }.sortedBy { it.startTs }
    }

    CleanScreen(
        title = stringResource(R.string.clean_strain),
        subtitle = fmt.relativeDay(end),
        accent = CleanColors.strain,
        onBack = onBack,
    ) {
        StrainHero(today, fmt, Modifier.cleanReveal(1))
        TargetCard(today, fmt, Modifier.cleanReveal(2))
        WorkoutsCard(todays, today, fmt, onOpenWorkouts, Modifier.cleanReveal(3))
        HistoryBarsCard(
            title = stringResource(R.string.clean_history),
            explanation = stringResource(R.string.clean_strain_chart_explain),
            days = today.days,
            end = end,
            range = range,
            ranges = listOf(HistoryRange.WEEK, HistoryRange.MONTH, HistoryRange.QUARTER),
            onRange = { range = it },
            pick = { row -> row.strain?.let { round1(strainOn21(it)) } },
            color = { strainLevelColor(strainLevel(it)) },
            yMax = STRAIN_MAX,
            yTicks = listOf(0.0, 10.0, 14.0, 18.0, 21.0),
            yLabel = { fmt.int(it) },
            valueText = { ValueText(fmt.decimal(it), null) },
            fmt = fmt,
            accent = CleanColors.strain,
            modifier = Modifier.cleanReveal(4),
            legend = {
                Legend {
                    StrainLevel.entries.forEach { LegendItem(strainLevelColor(it), strainLevelName(it)) }
                    LegendItem(CleanColors.textPrimary, stringResource(R.string.clean_legend_average), dashed = true)
                }
            },
        )
        GlassCard(Modifier.cleanReveal(5)) {
            Text(stringResource(R.string.clean_strain_how_title), style = CleanType.headline, color = CleanColors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.clean_strain_how_body), style = CleanType.callout, color = CleanColors.textSecondary)
        }
        HonestyNote(Modifier.cleanReveal(6))
    }
}

@Composable
private fun StrainHero(today: CleanToday, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val strain = today.strain21
    val target = strainTarget(today.recoveryValue)
    GlassCard(modifier, tint = CleanColors.strain) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreDial(
                fraction = strain?.let { (it / STRAIN_MAX).toFloat() },
                colors = strainDialColors,
                size = CleanDims.heroDial,
                stroke = CleanDims.heroDialStroke,
                target = target?.let { (it.low / STRAIN_MAX).toFloat()..(it.high / STRAIN_MAX).toFloat() },
            ) { reveal ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        strain?.let { fmt.decimal(it * reveal) } ?: "–",
                        style = CleanType.heroNumber,
                        color = if (strain == null) CleanColors.textTertiary else CleanColors.textPrimary,
                    )
                    Text(stringResource(R.string.clean_dial_of_21).uppercase(), style = CleanType.label, color = CleanColors.textSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (strain != null) {
                GlassChip(strainLevelName(strainLevel(strain)), strainLevelColor(strainLevel(strain)))
                if (target != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (strainTargetStatus(strain, target)) {
                            TargetStatus.BELOW -> stringResource(R.string.clean_target_below, fmt.decimal(target.low - strain))
                            TargetStatus.WITHIN -> stringResource(R.string.clean_target_within)
                            TargetStatus.ABOVE -> stringResource(R.string.clean_target_above)
                        },
                        style = CleanType.callout,
                        color = CleanColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(stringResource(R.string.clean_strain_no_data), style = CleanType.callout, color = CleanColors.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TargetCard(today: CleanToday, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val target = strainTarget(today.recoveryValue)
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_strain_target_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(if (target != null) R.string.clean_strain_target_explain else R.string.clean_strain_no_target),
            style = CleanType.caption,
            color = CleanColors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        if (target != null) {
            Text(
                stringResource(R.string.clean_verdict_target, target.low, target.high),
                style = CleanType.headline,
                color = CleanColors.strainBright,
            )
            Spacer(Modifier.height(4.dp))
        }
        StrainScale(today.strain21, target, tickLabel = { it.toString() })
        Spacer(Modifier.height(4.dp))
        Legend {
            StrainLevel.entries.forEach { LegendItem(strainLevelColor(it), strainLevelName(it)) }
        }
        today.recoveryValue?.let { rec ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.clean_target_from_recovery, fmt.percent(shownPercent(rec).toDouble())),
                style = CleanType.caption,
                color = CleanColors.textTertiary,
            )
        }
    }
}

@Composable
private fun WorkoutsCard(
    workouts: List<WorkoutRow>,
    today: CleanToday,
    fmt: CleanFormat,
    onOpenWorkouts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier, onClick = onOpenWorkouts) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.clean_workouts_today), style = CleanType.headline, color = CleanColors.textPrimary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.clean_workouts_open), style = CleanType.caption, color = CleanColors.strainBright)
            Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.strainBright, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (workouts.isEmpty()) {
            Text(stringResource(R.string.clean_workouts_none), style = CleanType.callout, color = CleanColors.textTertiary)
        } else {
            workouts.forEachIndexed { i, w ->
                WorkoutLine(w, fmt)
                if (i < workouts.lastIndex) HorizontalDivider(color = CleanColors.hairline, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        val row = today.todayRow
        if (row?.steps != null || row?.activeKcalEst != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.steps?.let {
                    Text(stringResource(R.string.clean_steps_value, fmt.int(it.toDouble())), style = CleanType.caption, color = CleanColors.textSecondary)
                }
                row.activeKcalEst?.let {
                    Text(stringResource(R.string.clean_kcal_value, fmt.int(it)), style = CleanType.caption, color = CleanColors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun WorkoutLine(w: WorkoutRow, fmt: CleanFormat) {
    val strain = w.strain?.let { round1(strainOn21(it)) }
    val minutes = (w.durationS ?: (w.endTs - w.startTs).toDouble()) / 60.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(CleanColors.strain.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.FitnessCenter, null, tint = CleanColors.strainBright, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                sportLabel(w.sport),
                style = CleanType.body,
                color = CleanColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${fmt.time(w.startTs)} · ${fmt.duration(minutes)}",
                style = CleanType.caption,
                color = CleanColors.textTertiary,
            )
        }
        if (strain != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(CleanDims.pillRadius))
                    .background(strainLevelColor(strainLevel(strain)).copy(alpha = 0.22f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(fmt.decimal(strain), style = CleanType.smallNumber, color = CleanColors.textPrimary)
            }
        }
    }
}

/** A workout's sport as a readable label; detected bouts read as a generic activity. */
@Composable
private fun sportLabel(sport: String): String =
    if (sport == "detected") stringResource(R.string.clean_activity_generic) else WorkoutEditing.displaySport(sport)

/** The tone of a strain value: the level colour. */
internal fun strainColor(strain21: Double): Color = strainLevelColor(strainLevel(strain21))

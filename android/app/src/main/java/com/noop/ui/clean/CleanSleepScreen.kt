package com.noop.ui.clean

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.analytics.RestScorer
import com.noop.ui.AppViewModel
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// MARK: - Clean Sleep
//
// One night at a time: its score and the plain facts (asleep, in bed, onset, wake), slept time against
// the need the score uses, the night as a timeline, how the stages split, and which parts made the score.
// The numbers are the classic Sleep tab's: the same night pick, the same stage model, the same score.

@Composable
internal fun CleanSleepScreen(
    vm: AppViewModel,
    onOpenClassicSleep: () -> Unit,
    onOpenAlarms: () -> Unit,
    onAsk: (String) -> Unit,
) {
    val today = rememberCleanToday(vm)
    val fmt = rememberCleanFormat()
    val nights = rememberCleanNights(vm, today.days)
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val count = nights?.count ?: 0
    val night = remember(nights, offset) { nights?.night(offset.coerceIn(0, max(count - 1, 0))) }
    var range by rememberSaveable { mutableStateOf(HistoryRange.WEEK) }
    val spans = remember(nights) { nights?.spans().orEmpty() }
    val plan = rememberSleepPlan(vm, today.days, spans)
    val latestNight = remember(nights) { nights?.night(0) }

    CleanScreen(
        title = stringResource(R.string.clean_sleep),
        subtitle = night?.let { fmt.longDate(LocalDate.parse(it.dayKey)) },
        accent = CleanColors.sleep,
    ) {
        NightSwitcher(
            label = when {
                night == null -> stringResource(R.string.clean_last_night)
                offset == 0 -> stringResource(R.string.clean_last_night)
                else -> stringResource(R.string.clean_night_of, fmt.dayMonth(LocalDate.parse(night.dayKey)))
            },
            window = night?.let { stringResource(R.string.clean_asleep_window, fmt.time(it.onset), fmt.time(it.wake)) },
            canOlder = offset < count - 1,
            canNewer = offset > 0,
            onOlder = { offset++ },
            onNewer = { offset-- },
            modifier = Modifier.cleanReveal(1),
        )
        if (night == null) {
            GlassCard(Modifier.cleanReveal(2)) {
                Text(stringResource(R.string.clean_sleep_none), style = CleanType.callout, color = CleanColors.textSecondary)
            }
        } else {
            SleepHero(night, fmt, Modifier.cleanReveal(2))
            NeedCard(night, fmt, Modifier.cleanReveal(3))
            (night.row?.totalSleepMin ?: night.stages?.asleep)?.let { asleep ->
                AskCoachButton(
                    stringResource(R.string.clean_coach_q_sleep, fmt.hoursMinutes(asleep), night.score?.let { fmt.percent(it) } ?: "–"),
                    onAsk,
                    Modifier.cleanReveal(3),
                )
            }
            TimelineCard(night, fmt, Modifier.cleanReveal(4))
            StagesCard(night, fmt, Modifier.cleanReveal(5))
            BreakdownCard(night, fmt, Modifier.cleanReveal(6))
        }
        SleepPlannerCard(plan, fmt, onOpenAlarms, Modifier.cleanReveal(6))
        RegularityCard(latestNight?.regularity, latestNight?.regularityTypical, spans, fmt, Modifier.cleanReveal(6))
        val needHours = RestScorer.defaultSleepNeedHours
        HistoryBarsCard(
            title = stringResource(R.string.clean_sleep_chart_title),
            explanation = stringResource(R.string.clean_sleep_chart_explain),
            days = today.days,
            end = LocalDate.parse(today.todayKey),
            range = range,
            ranges = listOf(HistoryRange.WEEK, HistoryRange.MONTH, HistoryRange.QUARTER),
            onRange = { range = it },
            pick = { row -> row.totalSleepMin?.takeIf { it > 0 }?.let { it / 60.0 } },
            color = { h -> if (h >= needHours) CleanColors.sleepBright else CleanColors.sleepDeep },
            yMax = 10.0,
            yTicks = listOf(0.0, 4.0, 8.0, 12.0),
            yLabel = { fmt.string(R.string.clean_fmt_hours_short, fmt.int(it)) },
            valueText = { ValueText(fmt.hoursMinutes(it * 60.0), null) },
            fmt = fmt,
            accent = CleanColors.sleep,
            reference = needHours,
            referenceLabel = stringResource(R.string.clean_legend_need),
            modifier = Modifier.cleanReveal(7),
            legend = {
                Legend {
                    LegendItem(CleanColors.sleepBright, stringResource(R.string.clean_legend_need_met))
                    LegendItem(CleanColors.sleepDeep, stringResource(R.string.clean_legend_need_short))
                    LegendItem(CleanColors.sleep, stringResource(R.string.clean_legend_need), dashed = true)
                    LegendItem(CleanColors.textPrimary, stringResource(R.string.clean_legend_average), dashed = true)
                }
            },
        )
        GlassCard(Modifier.cleanReveal(8), onClick = onOpenClassicSleep) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = CleanColors.sleepBright, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.clean_classic_sleep_title), style = CleanType.body, color = CleanColors.textPrimary)
                    Text(stringResource(R.string.clean_classic_sleep_body), style = CleanType.caption, color = CleanColors.textTertiary)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary)
            }
        }
        HonestyNote(Modifier.cleanReveal(9))
    }
}

@Composable
private fun NightSwitcher(
    label: String,
    window: String?,
    canOlder: Boolean,
    canNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(CleanDims.pillRadius))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            Icons.Rounded.ChevronLeft,
            stringResource(R.string.clean_night_older),
            onClick = { if (canOlder) onOlder() },
            modifier = Modifier.alpha(if (canOlder) 1f else 0.3f),
        )
        AnimatedContent(
            targetState = label to window,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "nightLabel",
        ) { (l, w) ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(l, style = CleanType.headline, color = CleanColors.textPrimary)
                if (w != null) Text(w, style = CleanType.caption, color = CleanColors.textTertiary)
            }
        }
        GlassIconButton(
            Icons.Rounded.ChevronRight,
            stringResource(R.string.clean_night_newer),
            onClick = { if (canNewer) onNewer() },
            modifier = Modifier.alpha(if (canNewer) 1f else 0.3f),
        )
    }
}

@Composable
private fun SleepHero(night: CleanNight, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val score = night.score
    val asleep = night.row?.totalSleepMin ?: night.stages?.asleep
    val inBed = (night.wake - night.onset) / 60.0
    GlassCard(modifier, tint = CleanColors.sleep) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreDial(
                fraction = score?.let { (it / 100.0).toFloat() },
                colors = sleepDialColors,
                size = 190.dp,
                stroke = 14.dp,
            ) { reveal ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            score?.let { (it.roundToInt() * reveal).roundToInt().toString() } ?: "–",
                            style = CleanType.heroNumber,
                            color = CleanColors.textPrimary,
                        )
                        if (score != null) {
                            Text("%", style = CleanType.title, color = CleanColors.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }
                    Text(stringResource(R.string.clean_sleep_performance).uppercase(), style = CleanType.label, color = CleanColors.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Fact(stringResource(R.string.clean_fact_asleep), asleep?.let { fmt.hoursMinutes(it) } ?: "–", Modifier.weight(1f))
                Fact(stringResource(R.string.clean_fact_in_bed), fmt.hoursMinutes(inBed), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Fact(stringResource(R.string.clean_fact_fell_asleep), fmt.time(night.onset), Modifier.weight(1f))
                Fact(stringResource(R.string.clean_fact_woke), fmt.time(night.wake), Modifier.weight(1f))
            }
            val chips = buildList {
                if (night.importedScore) add(R.string.clean_chip_imported)
                if (night.sparse) add(R.string.clean_chip_sparse)
                if (night.row?.sleepHrOnly == true) add(R.string.clean_chip_hr_only)
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    chips.forEach { GlassChip(stringResource(it), CleanColors.watch) }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = CleanType.number, color = CleanColors.textPrimary)
        Text(label, style = CleanType.caption, color = CleanColors.textTertiary)
    }
}

@Composable
private fun NeedCard(night: CleanNight, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val asleep = night.row?.totalSleepMin ?: night.stages?.asleep ?: return
    val need = RestScorer.defaultSleepNeedHours * 60.0
    val diff = asleep - need
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_need_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_need_explain, fmt.hoursMinutes(need)), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.clean_need_of, fmt.hoursMinutes(asleep), fmt.hoursMinutes(need)),
                style = CleanType.number,
                color = CleanColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(fmt.percent(asleep / need * 100.0), style = CleanType.number, color = CleanColors.sleepBright)
        }
        Spacer(Modifier.height(8.dp))
        NeedBar(asleep, need, CleanColors.sleepBright)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                abs(diff) < 1.0 -> stringResource(R.string.clean_need_met)
                diff < 0 -> stringResource(R.string.clean_need_short, fmt.duration(-diff))
                else -> stringResource(R.string.clean_need_over, fmt.duration(diff))
            },
            style = CleanType.callout,
            color = if (diff < -1.0) CleanColors.watch else CleanColors.good,
        )
    }
}

@Composable
private fun TimelineCard(night: CleanNight, fmt: CleanFormat, modifier: Modifier = Modifier) {
    var scrub by remember(night.dayKey) { mutableStateOf<Long?>(null) }
    val stageAt = scrub?.let { ts -> night.runs.firstOrNull { ts in it.start until it.end }?.stage }
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_hypnogram_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_hypnogram_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(10.dp))
        if (night.runs.isEmpty()) {
            Text(stringResource(R.string.clean_hypnogram_none), style = CleanType.callout, color = CleanColors.textTertiary)
            return@GlassCard
        }
        val headline = when {
            scrub == null -> stringResource(R.string.clean_hypnogram_hint)
            stageAt == null -> fmt.time(scrub!!)
            else -> "${fmt.time(scrub!!)} · ${stageName(stageAt)}"
        }
        Text(headline, style = CleanType.headline, color = stageAt?.let(::stageColor) ?: CleanColors.textSecondary)
        Spacer(Modifier.height(10.dp))
        SleepHypnogram(
            runs = night.runs,
            onset = night.onset,
            wake = night.wake,
            stageLabel = { stageNameText(it, fmt) },
            timeLabel = { fmt.hour(it) },
            scrubTs = scrub,
            onScrub = { scrub = it },
            description = stringResource(R.string.clean_hypnogram_title),
        )
    }
}

/** A stage's name outside composition (canvas labels). */
internal fun stageNameText(stage: SleepStage, fmt: CleanFormat): String = fmt.string(
    when (stage) {
        SleepStage.AWAKE -> R.string.clean_stage_awake
        SleepStage.REM -> R.string.clean_stage_rem
        SleepStage.LIGHT -> R.string.clean_stage_light
        SleepStage.DEEP -> R.string.clean_stage_deep
    },
)

@Composable
private fun stageDescription(stage: SleepStage): String = stringResource(
    when (stage) {
        SleepStage.AWAKE -> R.string.clean_stage_awake_desc
        SleepStage.REM -> R.string.clean_stage_rem_desc
        SleepStage.LIGHT -> R.string.clean_stage_light_desc
        SleepStage.DEEP -> R.string.clean_stage_deep_desc
    },
)

@Composable
private fun StagesCard(night: CleanNight, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val stages = night.stages ?: return
    val total = stages.total.takeIf { it > 0 } ?: return
    val rows = listOf(
        Triple(SleepStage.DEEP, stages.deep, night.typicalDeepMin),
        Triple(SleepStage.REM, stages.rem, night.typicalRemMin),
        Triple(SleepStage.LIGHT, stages.light, night.typicalLightMin),
        Triple(SleepStage.AWAKE, stages.awake, null),
    )
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_stages_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_stages_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(14.dp))
        StageShareBar(rows.map { it.first to it.second })
        Spacer(Modifier.height(14.dp))
        rows.forEachIndexed { i, (stage, minutes, typical) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(stageColor(stage), size = 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stageName(stage), style = CleanType.body, color = CleanColors.textPrimary)
                    Text(stageDescription(stage), style = CleanType.caption, color = CleanColors.textTertiary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmt.duration(minutes), style = CleanType.smallNumber, color = CleanColors.textPrimary)
                    val share = fmt.percent(minutes / total * 100.0)
                    Text(
                        if (typical != null) "$share · ${stringResource(R.string.clean_stage_typical, fmt.duration(typical))}" else share,
                        style = CleanType.caption,
                        color = CleanColors.textTertiary,
                    )
                }
            }
            if (i < rows.lastIndex) HorizontalDivider(color = CleanColors.hairline, modifier = Modifier.padding(vertical = 9.dp))
        }
    }
}

@Composable
private fun BreakdownCard(night: CleanNight, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val row = night.row ?: return
    if (night.score == null) return
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_breakdown_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        val breakdown = if (night.importedScore) null else sleepBreakdown(row)
        if (breakdown == null) {
            Text(stringResource(R.string.clean_breakdown_unavailable), style = CleanType.callout, color = CleanColors.textTertiary)
            return@GlassCard
        }
        Text(
            stringResource(R.string.clean_breakdown_explain, fmt.percent(breakdown.score)),
            style = CleanType.caption,
            color = CleanColors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        val parts = listOf(
            Part(R.string.clean_part_duration, breakdown.duration, RestScorer.wDuration, breakdown.durationPoints, null),
            Part(R.string.clean_part_efficiency, breakdown.efficiency, RestScorer.wEfficiency, breakdown.efficiencyPoints, null),
            Part(R.string.clean_part_restorative, breakdown.restorative, RestScorer.wRestorative, breakdown.restorativePoints, null),
            Part(R.string.clean_part_regularity, breakdown.regularity, RestScorer.wConsistency, breakdown.regularityPoints, R.string.clean_part_regularity_note),
        )
        parts.forEachIndexed { i, part ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(part.name), style = CleanType.body, color = CleanColors.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.clean_part_points, fmt.decimal(part.points, 0), fmt.int(part.weight * 100.0)),
                        style = CleanType.smallNumber,
                        color = CleanColors.textSecondary,
                    )
                }
                Text(
                    stringResource(R.string.clean_part_weight, fmt.percent(part.weight * 100.0)) +
                        (part.note?.let { " · " + stringResource(it) } ?: ""),
                    style = CleanType.caption,
                    color = CleanColors.textTertiary,
                )
                Spacer(Modifier.height(6.dp))
                ScoreTrack(part.score / 100.0, if (part.note != null) CleanColors.textTertiary else CleanColors.sleepBright)
            }
            if (i < parts.lastIndex) Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.clean_breakdown_footer, fmt.hoursMinutes(breakdown.needMin)),
            style = CleanType.caption,
            color = CleanColors.textTertiary,
            textAlign = TextAlign.Start,
        )
    }
}

private data class Part(val name: Int, val score: Double, val weight: Double, val points: Double, val note: Int?)

/** A thin 0..1 track with a filled part, for a sub-score. */
@Composable
private fun ScoreTrack(fraction: Double, color: androidx.compose.ui.graphics.Color) {
    val shown by androidx.compose.animation.core.animateFloatAsState(
        fraction.toFloat().coerceIn(0f, 1f),
        tween(900, easing = CleanMotion.emphasized),
        label = "scoreTrack",
    )
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(CleanColors.track, cornerRadius = r)
        drawRoundRect(color, size = androidx.compose.ui.geometry.Size(size.width * shown, size.height), cornerRadius = r)
    }
}

package com.noop.ui.clean

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.noop.R
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Clean Home
//
// One glance answers three questions: how recovered am I, how much have I done, how did I sleep. The
// verdict underneath turns the first answer into a plan for the day and shows it against the second, and
// the vitals list says which of last night's signals were unusual for THIS wearer.

/** The dial colours for a recovery value. */
internal fun recoveryDialColors(raw: Double?): DialColors = when (raw?.let(::recoveryZone)) {
    RecoveryZone.HIGH -> DialColors(CleanColors.recoveryHighDeep, CleanColors.recoveryHigh)
    RecoveryZone.MID -> DialColors(CleanColors.recoveryMidDeep, CleanColors.recoveryMid)
    RecoveryZone.LOW -> DialColors(CleanColors.recoveryLowDeep, CleanColors.recoveryLow)
    null -> DialColors(CleanColors.textTertiary, CleanColors.textSecondary)
}

internal val strainDialColors = DialColors(CleanColors.strainDeep, CleanColors.strainBright)
internal val sleepDialColors = DialColors(CleanColors.sleepDeep, CleanColors.sleepBright)

/** The accent a screen's aurora takes from the recovery state. */
internal fun recoveryAccent(raw: Double?): Color = recoveryDialColors(raw).bright

@Composable
internal fun CleanHomeScreen(
    vm: AppViewModel,
    onOpenRecovery: () -> Unit,
    onOpenStrain: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenStress: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenHealth: () -> Unit,
    onAsk: (String) -> Unit,
    onOpenCoach: () -> Unit,
    onOpenLive: () -> Unit,
) {
    val today = rememberCleanToday(vm)
    val link = rememberLinkStatus(vm)
    val fmt = rememberCleanFormat()
    val nights = rememberCleanNights(vm, today.days)
    val lastNight = remember(nights) { nights?.night(0) }
    val recoveryValue = today.recoveryValue
    val stress = rememberStressRead(vm, today.days)
    val journal = rememberJournalToday(vm)

    CleanScreen(
        title = stringResource(R.string.clean_home_title),
        subtitle = fmt.longDate(LocalDate.now()),
        accent = recoveryAccent(recoveryValue),
        trailing = { LinkPill(link, fmt, onOpenDevices) },
    ) {
        DialsCard(today, fmt, onOpenRecovery, onOpenStrain, onOpenSleep, Modifier.cleanReveal(1))
        NumbersGrid(vm, today, fmt, onOpenLive, Modifier.cleanReveal(2))
        VerdictCard(today, fmt, Modifier.cleanReveal(2), onClick = onOpenStrain)
        BriefingCard(today, fmt, onAsk, onOpenCoach, Modifier.cleanReveal(3))
        QuickTiles(stress, journal, fmt, onOpenStress, onOpenJournal, Modifier.cleanReveal(3))
        HealthGlanceCard(vm, today.days, fmt, onOpenHealth, Modifier.cleanReveal(3))
        LastNightCard(lastNight, fmt, onOpenSleep, Modifier.cleanReveal(5))
        HonestyNote(Modifier.cleanReveal(7))
    }
}

/** How much of today's logbook is filled in: answered items out of the visible catalogue. */
internal data class JournalToday(val answered: Int, val total: Int)

@Composable
internal fun rememberJournalToday(vm: AppViewModel): JournalToday? {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by remember { androidx.compose.runtime.mutableStateOf<JournalToday?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        state = runCatching {
            val key = com.noop.ui.journalDayKey(0L)
            val imported = vm.repo.journal("my-whoop", "0000-01-01", "9999-12-31").map { it.question }.distinct()
            val items = com.noop.ui.resolveJournalItems(imported, com.noop.ui.loadJournalCatalogItems(context))
            val answered = vm.repo.journal(com.noop.ui.JOURNAL_DEVICE_ID, key, key).map { it.question }.toSet()
            JournalToday(items.count { it.canonical in answered }, items.size)
        }.getOrNull()
    }
    return state
}

@Composable
private fun QuickTiles(
    stress: StressRead,
    journal: JournalToday?,
    fmt: CleanFormat,
    onOpenStress: () -> Unit,
    onOpenJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CleanDims.rowGap)) {
        val score = stress.model?.score
        GlassCard(Modifier.weight(1f), tint = score?.let(::stressColor), padding = 16.dp, onClick = onOpenStress) {
            Text(stringResource(R.string.clean_stress_title).uppercase(), style = CleanType.label, color = CleanColors.textTertiary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(score?.let { fmt.decimal(it) } ?: "–", style = CleanType.bigNumber, color = CleanColors.textPrimary)
                Text(
                    " / 3",
                    style = CleanType.unit,
                    color = CleanColors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                score?.let { stressBandName(it) } ?: stringResource(R.string.clean_dial_no_data),
                style = CleanType.caption,
                color = score?.let(::stressColor) ?: CleanColors.textTertiary,
            )
        }
        GlassCard(Modifier.weight(1f), tint = CleanColors.auroraViolet, padding = 16.dp, onClick = onOpenJournal) {
            Text(stringResource(R.string.clean_journal_title).uppercase(), style = CleanType.label, color = CleanColors.textTertiary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(journal?.answered?.toString() ?: "–", style = CleanType.bigNumber, color = CleanColors.textPrimary)
                if (journal != null) {
                    Text(
                        " / ${journal.total}",
                        style = CleanType.unit,
                        color = CleanColors.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Text(
                stringResource(
                    if (journal != null && journal.answered > 0) R.string.clean_journal_tile_logged
                    else R.string.clean_journal_tile_empty,
                ),
                style = CleanType.caption,
                color = CleanColors.textTertiary,
            )
        }
    }
}

// MARK: Header link pill

@Composable
private fun LinkPill(link: LinkStatus, fmt: CleanFormat, onClick: () -> Unit) {
    val text = when {
        link.syncing -> stringResource(R.string.clean_link_syncing)
        link.connected && link.batteryPct != null -> stringResource(R.string.clean_link_connected_battery, link.batteryPct)
        link.connected -> stringResource(R.string.clean_link_connected)
        else -> stringResource(R.string.clean_link_disconnected)
    }
    val color = when {
        link.syncing -> CleanColors.strainBright
        link.connected -> CleanColors.good
        else -> CleanColors.textTertiary
    }
    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .glass(RoundedCornerShape(CleanDims.pillRadius))
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(color, pulsing = link.connected || link.syncing)
        Spacer(Modifier.width(4.dp))
        Text(text, style = CleanType.caption, color = CleanColors.textSecondary, maxLines = 1)
    }
}

// MARK: Dials

@Composable
private fun DialsCard(
    today: CleanToday,
    fmt: CleanFormat,
    onOpenRecovery: () -> Unit,
    onOpenStrain: () -> Unit,
    onOpenSleep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier, padding = 14.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 8.dp
            val dialSize = min((maxWidth - gap * 2) / 3, 116.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RecoveryDialTile(today.recovery, fmt, dialSize, onOpenRecovery)
                StrainDialTile(today.strain21, fmt, dialSize, onOpenStrain)
                SleepDialTile(today.sleep, fmt, dialSize, onOpenSleep)
            }
        }
    }
}

@Composable
private fun DialTile(
    label: String,
    caption: String?,
    size: Dp,
    description: String,
    onClick: () -> Unit,
    dial: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Column(
        modifier = Modifier
            .width(size + 6.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(22.dp))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        dial()
        Spacer(Modifier.height(8.dp))
        Text(label.uppercase(), style = CleanType.label, color = CleanColors.textSecondary, maxLines = 1)
        Text(
            caption ?: " ",
            style = CleanType.micro,
            color = CleanColors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DialNumber(text: String, unit: String?, color: Color = CleanColors.textPrimary) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, style = CleanType.dialNumber, color = color)
        if (unit != null) {
            Text(unit, style = CleanType.unit, color = CleanColors.textSecondary, modifier = Modifier.padding(bottom = 5.dp, start = 1.dp))
        }
    }
}

@Composable
private fun RecoveryDialTile(recovery: RecoveryRead, fmt: CleanFormat, size: Dp, onClick: () -> Unit) {
    val label = stringResource(R.string.clean_recovery)
    when (recovery) {
        is RecoveryRead.Scored -> {
            val shown = shownPercent(recovery.value)
            val caption = if (recovery.carried) {
                stringResource(R.string.clean_as_of, fmt.dayMonth(LocalDate.parse(recovery.row.day)))
            } else {
                null
            }
            DialTile(label, caption, size, "$label ${fmt.percent(shown.toDouble())}", onClick) {
                ScoreDial(shown / 100f, recoveryDialColors(recovery.value), size, CleanDims.homeDialStroke) { reveal ->
                    DialNumber((shown * reveal).roundToInt().toString(), "%")
                }
            }
        }
        is RecoveryRead.Calibrating -> {
            val caption = stringResource(R.string.clean_dial_calibrating)
            DialTile(label, caption, size, "$label $caption", onClick) {
                ScoreDial(null, recoveryDialColors(null), size, CleanDims.homeDialStroke) {
                    Icon(Icons.Rounded.SelfImprovement, null, tint = CleanColors.textSecondary, modifier = Modifier.size(26.dp))
                }
            }
        }
        RecoveryRead.NoData -> {
            val caption = stringResource(R.string.clean_dial_no_data)
            DialTile(label, caption, size, "$label $caption", onClick) {
                ScoreDial(null, recoveryDialColors(null), size, CleanDims.homeDialStroke) {
                    DialNumber("–", null, CleanColors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun StrainDialTile(strain21: Double?, fmt: CleanFormat, size: Dp, onClick: () -> Unit) {
    val label = stringResource(R.string.clean_strain)
    val caption = if (strain21 == null) stringResource(R.string.clean_dial_no_data) else stringResource(R.string.clean_dial_of_21)
    DialTile(label, caption, size, "$label ${strain21?.let { fmt.decimal(it) } ?: caption}", onClick) {
        ScoreDial(
            strain21?.let { (it / STRAIN_MAX).toFloat() },
            strainDialColors,
            size,
            CleanDims.homeDialStroke,
            delayMillis = 120,
        ) { reveal ->
            if (strain21 == null) DialNumber("–", null, CleanColors.textTertiary)
            else DialNumber(fmt.decimal(strain21 * reveal), null)
        }
    }
}

@Composable
private fun SleepDialTile(sleep: SleepRead, fmt: CleanFormat, size: Dp, onClick: () -> Unit) {
    val label = stringResource(R.string.clean_sleep)
    val value = sleep.value
    val caption = when {
        value == null -> stringResource(R.string.clean_dial_no_data)
        sleep.carried && sleep.dayKey != null -> stringResource(R.string.clean_as_of, fmt.dayMonth(LocalDate.parse(sleep.dayKey)))
        else -> null
    }
    DialTile(label, caption, size, "$label ${value?.let { fmt.percent(it) } ?: caption}", onClick) {
        ScoreDial(value?.let { (it / 100.0).toFloat() }, sleepDialColors, size, CleanDims.homeDialStroke, delayMillis = 240) { reveal ->
            if (value == null) DialNumber("–", null, CleanColors.textTertiary)
            else DialNumber((value.roundToInt() * reveal).roundToInt().toString(), "%")
        }
    }
}

// MARK: Verdict

@Composable
internal fun VerdictCard(today: CleanToday, fmt: CleanFormat, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val recovery = today.recoveryValue
    val verdict = dayVerdict(recovery)
    val target = strainTarget(recovery)
    val (titleRes, bodyRes, icon) = when (verdict) {
        DayVerdict.PUSH -> Triple(R.string.clean_verdict_push_title, R.string.clean_verdict_push_body, Icons.Rounded.Bolt)
        DayVerdict.MAINTAIN -> Triple(R.string.clean_verdict_maintain_title, R.string.clean_verdict_maintain_body, Icons.AutoMirrored.Rounded.DirectionsRun)
        DayVerdict.RECOVER -> Triple(R.string.clean_verdict_recover_title, R.string.clean_verdict_recover_body, Icons.Rounded.SelfImprovement)
        null -> Triple(R.string.clean_verdict_none_title, R.string.clean_verdict_none_body, Icons.Rounded.Bedtime)
    }
    val accent = recoveryAccent(recovery)
    GlassCard(modifier, tint = if (verdict != null) accent else null, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.clean_verdict_label).uppercase(), style = CleanType.label, color = CleanColors.textTertiary)
                Text(stringResource(titleRes), style = CleanType.title, color = CleanColors.textPrimary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(bodyRes), style = CleanType.callout, color = CleanColors.textSecondary)
        val scored = today.recovery as? RecoveryRead.Scored
        if (scored != null && scored.carried) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.clean_verdict_based_on, fmt.dayMonth(LocalDate.parse(scored.row.day))),
                style = CleanType.caption,
                color = CleanColors.textTertiary,
            )
        }
        if (target != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.clean_verdict_target, target.low, target.high),
                style = CleanType.headline,
                color = CleanColors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            StrainScale(today.strain21, target, tickLabel = { it.toString() })
            val strain = today.strain21
            if (strain != null) {
                val status = when (strainTargetStatus(strain, target)) {
                    TargetStatus.BELOW -> stringResource(R.string.clean_target_below, fmt.decimal(target.low - strain))
                    TargetStatus.WITHIN -> stringResource(R.string.clean_target_within)
                    TargetStatus.ABOVE -> stringResource(R.string.clean_target_above)
                }
                Text(
                    stringResource(R.string.clean_target_now, fmt.decimal(strain)) + " · " + status,
                    style = CleanType.caption,
                    color = CleanColors.textSecondary,
                )
            }
        }
    }
}

// MARK: Vitals

internal fun vitalIcon(kind: VitalKind): ImageVector = when (kind) {
    VitalKind.HRV -> Icons.Rounded.MonitorHeart
    VitalKind.RESTING_HR -> Icons.Rounded.Favorite
    VitalKind.RESPIRATION -> Icons.Rounded.Air
    VitalKind.SLEEP_DURATION -> Icons.Rounded.Bedtime
    VitalKind.SKIN_TEMP -> Icons.Rounded.Thermostat
    VitalKind.SPO2 -> Icons.Rounded.WaterDrop
}

internal fun toneColor(tone: VitalTone): Color = when (tone) {
    VitalTone.GOOD -> CleanColors.good
    VitalTone.NORMAL -> CleanColors.textPrimary
    VitalTone.WATCH -> CleanColors.watch
}

/** The vitals of [row], each judged against the wearer's normal range. */
internal fun vitalsFor(row: DailyMetric, today: CleanToday): List<VitalStat> {
    val day = LocalDate.parse(row.day)
    val days = today.days
    return listOfNotNull(
        baselineVital(VitalKind.HRV, row.avgHrv, today.baselines?.hrv, higherIsBetter = true),
        baselineVital(VitalKind.RESTING_HR, row.restingHr?.toDouble(), today.baselines?.restingHR, higherIsBetter = false),
        baselineVital(VitalKind.RESPIRATION, row.respRateBpm, today.baselines?.resp, higherIsBetter = false),
        historyVital(
            VitalKind.SLEEP_DURATION,
            row.totalSleepMin?.takeIf { it > 0 },
            trailingValues(days, day, 30) { it.totalSleepMin?.takeIf { m -> m > 0 } },
            higherIsBetter = true,
            minSpread = 20.0,
        ),
        skinTempVital(row.skinTempDevC, trailingValues(days, day, 30) { it.skinTempDevC }),
        historyVital(VitalKind.SPO2, row.spo2Pct, trailingValues(days, day, 30) { it.spo2Pct }, higherIsBetter = true, minSpread = 0.5),
    )
}

@Composable
internal fun vitalName(kind: VitalKind): String = stringResource(
    when (kind) {
        VitalKind.HRV -> R.string.clean_vital_hrv
        VitalKind.RESTING_HR -> R.string.clean_vital_rhr
        VitalKind.RESPIRATION -> R.string.clean_vital_resp
        VitalKind.SLEEP_DURATION -> R.string.clean_vital_sleep
        VitalKind.SKIN_TEMP -> R.string.clean_vital_skin
        VitalKind.SPO2 -> R.string.clean_vital_spo2
    },
)

/** A vital's value with its unit, e.g. "124 ms", "14,5 /min", "7:12 h", "+0,3 °C". */
internal fun vitalValue(kind: VitalKind, value: Double, fmt: CleanFormat): String = when (kind) {
    VitalKind.HRV -> fmt.string(R.string.clean_value_ms, fmt.int(value))
    VitalKind.RESTING_HR -> fmt.string(R.string.clean_value_bpm, fmt.int(value))
    VitalKind.RESPIRATION -> fmt.string(R.string.clean_value_rpm, fmt.decimal(value))
    VitalKind.SLEEP_DURATION -> fmt.hoursMinutes(value)
    VitalKind.SKIN_TEMP -> fmt.string(R.string.clean_value_celsius, fmt.signed(value, 1))
    VitalKind.SPO2 -> fmt.percent(value)
}

/** A difference in a vital's unit, unsigned, e.g. "12 ms", "24 min", "0,3 °C". */
internal fun vitalDelta(kind: VitalKind, delta: Double, fmt: CleanFormat): String {
    val d = abs(delta)
    return when (kind) {
        VitalKind.HRV -> fmt.string(R.string.clean_value_ms, fmt.int(d))
        VitalKind.RESTING_HR -> fmt.string(R.string.clean_value_bpm, fmt.int(d))
        VitalKind.RESPIRATION -> fmt.string(R.string.clean_value_rpm, fmt.decimal(d))
        VitalKind.SLEEP_DURATION -> fmt.duration(d)
        VitalKind.SKIN_TEMP -> fmt.string(R.string.clean_value_celsius, fmt.decimal(d))
        VitalKind.SPO2 -> fmt.string(R.string.clean_value_points, fmt.decimal(d))
    }
}

/** The bounds of a normal range in the vital's own format, without a sign on skin temperature. */
private fun vitalBound(kind: VitalKind, v: Double, fmt: CleanFormat): String = when (kind) {
    VitalKind.HRV, VitalKind.RESTING_HR -> fmt.int(v)
    VitalKind.RESPIRATION -> fmt.decimal(v)
    VitalKind.SLEEP_DURATION -> fmt.hoursMinutes(v)
    VitalKind.SKIN_TEMP -> fmt.signed(v, 1)
    VitalKind.SPO2 -> fmt.decimal(v)
}

// MARK: Last night

@Composable
private fun LastNightCard(night: CleanNight?, fmt: CleanFormat, onOpenSleep: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionLabel(stringResource(R.string.clean_last_night))
        Spacer(Modifier.height(6.dp))
        GlassCard(onClick = onOpenSleep) {
            if (night == null) {
                Text(stringResource(R.string.clean_sleep_none), style = CleanType.callout, color = CleanColors.textSecondary)
                return@GlassCard
            }
            val asleep = night.row?.totalSleepMin ?: night.stages?.asleep
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        asleep?.let { fmt.hoursMinutes(it) } ?: "–",
                        style = CleanType.bigNumber,
                        color = CleanColors.textPrimary,
                    )
                    Text(
                        stringResource(R.string.clean_asleep_window, fmt.time(night.onset), fmt.time(night.wake)),
                        style = CleanType.caption,
                        color = CleanColors.textTertiary,
                    )
                }
                night.score?.let { score ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmt.percent(score), style = CleanType.number, color = CleanColors.sleepBright)
                        Text(stringResource(R.string.clean_sleep_performance), style = CleanType.caption, color = CleanColors.textTertiary)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary, modifier = Modifier.padding(start = 6.dp))
            }
            night.stages?.let { stages ->
                Spacer(Modifier.height(14.dp))
                StageShareBar(
                    listOf(
                        SleepStage.DEEP to stages.deep,
                        SleepStage.REM to stages.rem,
                        SleepStage.LIGHT to stages.light,
                        SleepStage.AWAKE to stages.awake,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Legend {
                    LegendItem(stageColor(SleepStage.DEEP), "${stageName(SleepStage.DEEP)} ${fmt.duration(stages.deep)}")
                    LegendItem(stageColor(SleepStage.REM), "${stageName(SleepStage.REM)} ${fmt.duration(stages.rem)}")
                    LegendItem(stageColor(SleepStage.LIGHT), "${stageName(SleepStage.LIGHT)} ${fmt.duration(stages.light)}")
                    LegendItem(stageColor(SleepStage.AWAKE), "${stageName(SleepStage.AWAKE)} ${fmt.duration(stages.awake)}")
                }
            }
        }
    }
}

@Composable
internal fun stageName(stage: SleepStage): String = stringResource(
    when (stage) {
        SleepStage.AWAKE -> R.string.clean_stage_awake
        SleepStage.REM -> R.string.clean_stage_rem
        SleepStage.LIGHT -> R.string.clean_stage_light
        SleepStage.DEEP -> R.string.clean_stage_deep
    },
)

// MARK: Honesty note

@Composable
internal fun HonestyNote(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.clean_honesty_note),
        style = CleanType.caption,
        color = CleanColors.textTertiary,
        modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** "About N more nights" for a calibrating recovery. */
@Composable
internal fun calibratingText(nights: Int): String =
    pluralStringResource(R.plurals.clean_calibrating_nights, nights, nights)

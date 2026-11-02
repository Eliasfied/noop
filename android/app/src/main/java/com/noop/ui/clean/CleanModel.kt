package com.noop.ui.clean

import com.noop.analytics.BaselineState
import com.noop.analytics.Baselines
import com.noop.analytics.RestScorer
import com.noop.analytics.SkinTempDisplay
import com.noop.data.DailyMetric
import com.noop.ui.EffortScale
import com.noop.ui.OptimalStrainRange
import com.noop.ui.UnitFormatter
import com.noop.ui.canonicalStage
import com.noop.ui.optimalStrainRange
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// MARK: - Clean design read model
//
// Pure, Compose-free helpers behind the clean screens. Nothing here scores anything: every number is read
// from the stored rows the classic screens read, and the few derived readings (zones, the day verdict, the
// sleep-score breakdown, period averages) are display mappings over those numbers. Each reading has ONE
// resolver here, so a dial, its detail page and the day verdict cannot disagree about the same fact.

// MARK: Recovery

/** The three recovery bands. The thresholds are the ones [optimalStrainRange] already uses. */
internal enum class RecoveryZone { LOW, MID, HIGH }

/** The whole percent a recovery reads as on screen. Zones and targets resolve from THIS, not the raw value,
 *  so a 66.6 shown as "67 %" can never be coloured and advised as a 66. */
internal fun shownPercent(raw: Double): Int = raw.roundToInt().coerceIn(0, 100)

internal fun recoveryZone(raw: Double): RecoveryZone {
    val shown = shownPercent(raw)
    return when {
        shown >= 67 -> RecoveryZone.HIGH
        shown >= 34 -> RecoveryZone.MID
        else -> RecoveryZone.LOW
    }
}

/** The one-line advice of the day, derived from the recovery band alone so it always matches the dial. */
internal enum class DayVerdict { PUSH, MAINTAIN, RECOVER }

internal fun dayVerdict(rawRecovery: Double?): DayVerdict? = rawRecovery?.let {
    when (recoveryZone(it)) {
        RecoveryZone.HIGH -> DayVerdict.PUSH
        RecoveryZone.MID -> DayVerdict.MAINTAIN
        RecoveryZone.LOW -> DayVerdict.RECOVER
    }
}

/** The suggested strain band for a recovery, resolved from the SHOWN percent through the shipped
 *  [optimalStrainRange] mapping, so the verdict card and the strain page name the same band. */
internal fun strainTarget(rawRecovery: Double?): OptimalStrainRange? =
    optimalStrainRange(rawRecovery?.let { shownPercent(it).toDouble() })

// MARK: Strain

/** The 0-21 axis the clean design shows strain on. */
internal const val STRAIN_MAX = 21.0

/** A stored 0-100 Effort value on the 0-21 axis, through the shipped formatter's conversion factor. */
internal fun strainOn21(stored: Double): Double =
    UnitFormatter.effortValue(stored, EffortScale.WHOOP).coerceIn(0.0, STRAIN_MAX)

/** One decimal, the precision strain is shown at; levels and targets read this. */
internal fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0

internal enum class StrainLevel(val from: Double, val to: Double) {
    LIGHT(0.0, 10.0),
    MODERATE(10.0, 14.0),
    HIGH(14.0, 18.0),
    ALL_OUT(18.0, STRAIN_MAX),
}

internal fun strainLevel(strain21: Double): StrainLevel {
    val v = round1(strain21)
    return when {
        v >= 18.0 -> StrainLevel.ALL_OUT
        v >= 14.0 -> StrainLevel.HIGH
        v >= 10.0 -> StrainLevel.MODERATE
        else -> StrainLevel.LIGHT
    }
}

internal enum class TargetStatus { BELOW, WITHIN, ABOVE }

internal fun strainTargetStatus(strain21: Double, target: OptimalStrainRange): TargetStatus {
    val v = round1(strain21)
    return when {
        v < target.low -> TargetStatus.BELOW
        v > target.high -> TargetStatus.ABOVE
        else -> TargetStatus.WITHIN
    }
}

// MARK: Vitals against the personal normal range

internal enum class VitalKind { HRV, RESTING_HR, RESPIRATION, SLEEP_DURATION, SKIN_TEMP, SPO2 }

/** Where a vital sits against the wearer's own normal range, read in the direction that matters for it. */
internal enum class VitalTone { GOOD, NORMAL, WATCH }

/**
 * One vital for the day, with the personal normal range it is judged against. [normal] is the centre
 * and [bandLow]..[bandHigh] the range one robust standard deviation either side; all three are null while
 * the baseline is still being learned, in which case the tone is [VitalTone.NORMAL] and no arrow is drawn.
 */
internal data class VitalStat(
    val kind: VitalKind,
    val value: Double,
    val normal: Double?,
    val bandLow: Double?,
    val bandHigh: Double?,
    val tone: VitalTone,
) {
    val delta: Double? get() = normal?.let { value - it }
}

/** [higherIsBetter] null means any large move is worth a look (skin temperature). */
internal fun toneFor(z: Double, higherIsBetter: Boolean?): VitalTone = when {
    abs(z) <= 1.0 -> VitalTone.NORMAL
    higherIsBetter == null -> VitalTone.WATCH
    (z > 0.0) == higherIsBetter -> VitalTone.GOOD
    else -> VitalTone.WATCH
}

/**
 * A vital judged against one of the Charge baselines (HRV, resting HR, respiration) — the SAME baselines
 * the recovery score and its drivers were computed against, so the arrow here and the driver row on the
 * recovery page always point the same way.
 */
internal fun baselineVital(
    kind: VitalKind,
    value: Double?,
    state: BaselineState?,
    higherIsBetter: Boolean?,
): VitalStat? {
    val v = value ?: return null
    val s = state?.takeIf { it.usable } ?: return VitalStat(kind, v, null, null, null, VitalTone.NORMAL)
    val sigma = Baselines.sigma(s)
    val z = (v - s.baseline) / sigma
    return VitalStat(kind, v, s.baseline, s.baseline - sigma, s.baseline + sigma, toneFor(z, higherIsBetter))
}

/** Nights of history a sample-based normal range needs before it is shown. */
internal const val MIN_HISTORY_NIGHTS = 5

/**
 * A vital judged against its own recent history (sleep duration, SpO2): centre = mean, range = one sample
 * standard deviation, floored at [minSpread] so a very regular history does not flag every small move.
 */
internal fun historyVital(
    kind: VitalKind,
    value: Double?,
    history: List<Double>,
    higherIsBetter: Boolean?,
    minSpread: Double,
): VitalStat? {
    val v = value ?: return null
    if (history.size < MIN_HISTORY_NIGHTS) return VitalStat(kind, v, null, null, null, VitalTone.NORMAL)
    val mean = history.average()
    val sd = sqrt(history.sumOf { (it - mean) * (it - mean) } / (history.size - 1)).coerceAtLeast(minSpread)
    val z = (v - mean) / sd
    return VitalStat(kind, v, mean, mean - sd, mean + sd, toneFor(z, higherIsBetter))
}

/**
 * Skin temperature as a deviation. The stored value is already relative to the personal nightly baseline,
 * so the centre is 0 and the range is the root-mean-square of recent deviations (floored at 0.2 °C).
 * Absolute wrist temperatures (imports write those into the same column) are excluded by magnitude, the
 * rule [SkinTempDisplay.kind] applies everywhere else.
 */
internal fun skinTempVital(deviation: Double?, history: List<Double>): VitalStat? {
    val v = deviation?.takeIf { SkinTempDisplay.kind(it) == SkinTempDisplay.Kind.DEVIATION } ?: return null
    val devs = history.filter { SkinTempDisplay.kind(it) == SkinTempDisplay.Kind.DEVIATION }
    if (devs.size < MIN_HISTORY_NIGHTS) return VitalStat(VitalKind.SKIN_TEMP, v, null, null, null, VitalTone.NORMAL)
    val rms = sqrt(devs.sumOf { it * it } / devs.size).coerceAtLeast(0.2)
    return VitalStat(VitalKind.SKIN_TEMP, v, 0.0, -rms, rms, toneFor(v / rms, null))
}

// MARK: Sleep

/** Efficiency as a percentage, whichever scale the row stored it on. */
internal fun efficiencyPercent(stored: Double?): Double? =
    stored?.let { if (it <= 1.0) it * 100.0 else it }

/**
 * The four parts of the sleep score, recomputed from the SAME inputs and constants
 * [RestScorer.restFromDaily] uses (default need, neutral regularity), so the rows always add up to the
 * score above them. [CleanModelTest] pins `score == RestScorer.restFromDaily(row)` over a spread of rows.
 * Each part is 0-100; its share of the score is part x weight.
 */
internal data class SleepBreakdown(
    val asleepMin: Double,
    val needMin: Double,
    val duration: Double,
    val efficiency: Double,
    val restorative: Double,
    val regularity: Double,
    val score: Double,
) {
    val durationPoints: Double get() = duration * RestScorer.wDuration
    val efficiencyPoints: Double get() = efficiency * RestScorer.wEfficiency
    val restorativePoints: Double get() = restorative * RestScorer.wRestorative
    val regularityPoints: Double get() = regularity * RestScorer.wConsistency
}

/**
 * Null when the row cannot be scored, and also when its efficiency is stored as a percentage (some imports
 * do): the score clamps that input to 100 %, so a breakdown would show a different efficiency than the
 * night's own figure. Those nights keep the score and simply skip the breakdown.
 */
internal fun sleepBreakdown(row: DailyMetric): SleepBreakdown? {
    val tstMin = row.totalSleepMin ?: return null
    val eff = row.efficiency ?: return null
    if (tstMin <= 0.0 || eff > 1.0) return null
    val asleepSec = tstMin * 60.0
    val needHours = RestScorer.defaultSleepNeedHours
    val duration = min(100.0, asleepSec / 3600.0 / needHours * 100.0)
    val efficiency = (eff * 100.0).coerceIn(0.0, 100.0)
    val deepSec = (row.deepMin ?: 0.0) * 60.0
    val remSec = (row.remMin ?: 0.0) * 60.0
    val restorativeShare = (deepSec + remSec) / asleepSec
    val deepAdequacy = ((deepSec / asleepSec) / RestScorer.deepShareTarget).coerceIn(0.0, 1.0)
    val deepFactor = RestScorer.deepFloorFactor + (1.0 - RestScorer.deepFloorFactor) * deepAdequacy
    val restorative = min(100.0, restorativeShare / RestScorer.restorativeTargetShare * 100.0) * deepFactor
    val regularity = (RestScorer.NEUTRAL_CONSISTENCY * 100.0).coerceIn(0.0, 100.0)
    val weighted = RestScorer.wDuration * duration + RestScorer.wEfficiency * efficiency +
        RestScorer.wRestorative * restorative + RestScorer.wConsistency * regularity
    return SleepBreakdown(
        asleepMin = tstMin,
        needMin = needHours * 60.0,
        duration = duration,
        efficiency = efficiency,
        restorative = restorative,
        regularity = regularity,
        score = (weighted * 100.0).roundToInt() / 100.0,
    )
}

internal enum class SleepStage { AWAKE, REM, LIGHT, DEEP }

internal fun sleepStageOf(raw: String): SleepStage? = when (canonicalStage(raw)) {
    "awake" -> SleepStage.AWAKE
    "rem" -> SleepStage.REM
    "light" -> SleepStage.LIGHT
    "deep" -> SleepStage.DEEP
    else -> null
}

/** One contiguous run of a stage, wall-clock seconds. */
internal data class StageRun(val stage: SleepStage, val start: Long, val end: Long)

/**
 * Per-epoch segments folded into contiguous runs. A gap between two runs of the same stage up to
 * [joinGapSec] is joined, so the chart draws a block rather than a comb of 30-second slivers.
 */
internal fun stageRuns(segments: List<Triple<String, Long, Long>>, joinGapSec: Long = 60L): List<StageRun> {
    val out = ArrayList<StageRun>()
    for ((raw, start, end) in segments.sortedBy { it.second }) {
        val stage = sleepStageOf(raw) ?: continue
        if (end <= start) continue
        val last = out.lastOrNull()
        if (last != null && last.stage == stage && start - last.end <= joinGapSec) {
            out[out.lastIndex] = last.copy(end = maxOf(last.end, end))
        } else {
            out.add(StageRun(stage, start, end))
        }
    }
    return out
}

// MARK: Day series and period summaries

/** One calendar day of a series. Missing days stay in the list as null, so a chart shows the gap. */
internal data class DayValue(val day: LocalDate, val value: Double?)

/** [count] consecutive calendar days ending on [end] (oldest first), each with [pick] of that day's row. */
internal fun dayValues(
    days: List<DailyMetric>,
    end: LocalDate,
    count: Int,
    pick: (DailyMetric) -> Double?,
): List<DayValue> {
    val byDay = days.associateBy { it.day }
    return (count - 1 downTo 0).map { back ->
        val day = end.minusDays(back.toLong())
        DayValue(day, byDay[day.toString()]?.let(pick))
    }
}

/** The mean of a window and of the equally long window before it. */
internal data class PeriodSummary(val mean: Double?, val previousMean: Double?, val count: Int) {
    val change: Double? get() = if (mean != null && previousMean != null) mean - previousMean else null
    val changeRatio: Double?
        get() = if (mean != null && previousMean != null && previousMean != 0.0) mean / previousMean - 1.0 else null
}

internal fun periodSummary(
    days: List<DailyMetric>,
    end: LocalDate,
    count: Int,
    pick: (DailyMetric) -> Double?,
): PeriodSummary {
    val all = dayValues(days, end, count * 2, pick)
    val current = all.drop(count).mapNotNull { it.value }
    val previous = all.take(count).mapNotNull { it.value }
    return PeriodSummary(
        mean = current.takeIf { it.isNotEmpty() }?.average(),
        previousMean = previous.takeIf { it.isNotEmpty() }?.average(),
        count = current.size,
    )
}

/** Mean of [pick] over the [window] days strictly BEFORE [day]; null with fewer than 3 values. */
internal fun trailingMean(
    days: List<DailyMetric>,
    day: LocalDate,
    window: Int,
    pick: (DailyMetric) -> Double?,
): Double? {
    val values = dayValues(days, day.minusDays(1), window, pick).mapNotNull { it.value }
    return values.takeIf { it.size >= 3 }?.average()
}

/** Values of [pick] over the [window] days strictly before [day], oldest first, missing days dropped. */
internal fun trailingValues(
    days: List<DailyMetric>,
    day: LocalDate,
    window: Int,
    pick: (DailyMetric) -> Double?,
): List<Double> = dayValues(days, day.minusDays(1), window, pick).mapNotNull { it.value }

// MARK: Number formatting

/** A minus sign that lines up with the plus sign in tabular figures. */
internal const val MINUS = "−"

internal fun formatDecimal(value: Double, decimals: Int, locale: Locale): String =
    String.format(locale, "%.${decimals}f", value).replace("-", MINUS)

/** "+1,2" / "−1,2"; zero reads without a sign. */
internal fun formatSigned(value: Double, decimals: Int, locale: Locale): String {
    val body = String.format(locale, "%.${decimals}f", abs(value))
    val zero = String.format(locale, "%.${decimals}f", 0.0)
    return when {
        body == zero -> body
        value > 0 -> "+$body"
        else -> "$MINUS$body"
    }
}

/** Whole minutes split into hours and minutes, rounding the total first so 59.6 min reads 1:00. */
internal fun hoursAndMinutes(minutes: Double): Pair<Int, Int> {
    val total = minutes.roundToInt().coerceAtLeast(0)
    return total / 60 to total % 60
}

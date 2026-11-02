package com.noop.ui.clean

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.AnalyticsEngine
import com.noop.analytics.Baselines
import com.noop.analytics.ChargeBaselines
import com.noop.analytics.RestScorer
import com.noop.data.DailyMetric
import com.noop.data.SleepSession
import com.noop.data.WhoopRepository
import com.noop.ui.AppViewModel
import com.noop.ui.ClockPrefs
import com.noop.ui.ImportedSleepSeries
import com.noop.ui.NoopPrefs
import com.noop.ui.ScoreState
import com.noop.ui.Stages
import com.noop.ui.buildSleepModel
import com.noop.ui.consistencyNightSpans
import com.noop.ui.freshRestScore
import com.noop.ui.heroDisplay
import com.noop.ui.heroPerformanceScore
import com.noop.ui.lastScoredRecoveryDay
import com.noop.ui.logicalDayKeyNow
import com.noop.ui.napSleepMinutesByDay
import com.noop.ui.recoveryCalibrationNights
import com.noop.ui.resolveTodayRow
import com.noop.ui.scoreStateForToday
import com.noop.ui.selectNight
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// MARK: - Clean design data funnel
//
// Every clean screen reads "today" through [rememberCleanToday] and a night through [rememberCleanNights],
// so the Home dial, the detail page it opens and the verdict under it are the same resolution of the same
// rows. Both reuse the classic helpers for the hard parts (which row is today, when a score is carried,
// which sleep block is the main night), so the two designs never answer those questions differently.

/** The recovery the clean screens show for today. */
internal sealed interface RecoveryRead {
    /** A score to show. [carried] when it is a prior night's (today is not scored yet); [stale] when that
     *  night is older than the carry-freshness window. */
    data class Scored(val value: Double, val row: DailyMetric, val carried: Boolean, val stale: Boolean) : RecoveryRead

    /** The HRV baseline is still learning: about [nightsRemaining] more nights. */
    data class Calibrating(val nightsRemaining: Int) : RecoveryRead

    /** Nothing for today and nothing recent to carry. */
    data object NoData : RecoveryRead
}

/** The sleep score the Home dial shows, and which night it belongs to. */
internal data class SleepRead(val value: Double?, val dayKey: String?, val carried: Boolean, val imported: Boolean)

/** The one resolution of "today" every clean screen reads. */
internal data class CleanToday(
    val days: List<DailyMetric>,
    val todayKey: String,
    val todayRow: DailyMetric?,
    val recovery: RecoveryRead,
    val strainStored: Double?,
    val sleep: SleepRead,
    val baselines: ChargeBaselines.Resolved?,
    val importedPerf: Map<String, Double>,
) {
    /**
     * The row the vitals describe: the night the recovery dial is showing; else today's row when it has
     * any vitals; else the latest earlier night that does (while recovery calibrates, today's row is often
     * still empty, and the vitals of the last measured night are what there is to show).
     */
    val vitalsRow: DailyMetric? get() = (recovery as? RecoveryRead.Scored)?.row
        ?: todayRow?.takeIf(::hasVitals)
        ?: days.lastOrNull { it.day <= todayKey && hasVitals(it) }

    /** Today's strain on the 0-21 axis, one decimal. */
    val strain21: Double? get() = strainStored?.let { round1(strainOn21(it)) }

    /** The recovery value the verdict, the zone and the strain target read. */
    val recoveryValue: Double? get() = (recovery as? RecoveryRead.Scored)?.value

    /** The sleep score of any day, the same source rule as the dial (export figure first). */
    fun sleepScoreOf(row: DailyMetric): Double? = importedPerf[row.day] ?: RestScorer.restFromDaily(row)
}

/** Whether [row] carries any nightly vital the number tiles show. */
internal fun hasVitals(row: DailyMetric): Boolean =
    row.avgHrv != null || row.restingHr != null || row.respRateBpm != null ||
        row.skinTempDevC != null || row.spo2Pct != null

/**
 * Pure resolution of [CleanToday], using the classic Today helpers for the today row
 * ([resolveTodayRow]), the calibration count ([recoveryCalibrationNights]), the carry-over
 * ([lastScoredRecoveryDay]) and their precedence ([scoreStateForToday]).
 */
internal fun resolveCleanToday(
    days: List<DailyMetric>,
    today: DailyMetric?,
    baselines: ChargeBaselines.Resolved?,
    importedPerf: Map<String, Double>,
    hrvEpoch: Double,
    logicalKey: String,
    localKey: String,
): CleanToday {
    val todayRow = resolveTodayRow(days, logicalKey, localKey) ?: today
    val todayKey = todayRow?.day ?: logicalKey
    val nowKey = maxOf(logicalKey, localKey)
    val todayScored = todayRow?.recovery != null
    // Null baselines mean "not resolved yet" (the flow's first frame), not "no history": counting an
    // empty history there would flash "Calibrating" on every launch before the real count arrives.
    val calibrating = if (baselines == null) null else recoveryCalibrationNights(
        baselines.hrvHistory.values,
        baselines.hrvHistory.dayKeys,
        hasRecovery = todayScored,
        hrvBaselineEpoch = hrvEpoch,
    )
    val carriedDay = lastScoredRecoveryDay(
        days = days,
        selectedDayKey = todayKey,
        isToday = true,
        todayScored = todayScored,
        isCalibrating = calibrating != null,
        today = nowKey,
    )
    val recovery = when (val state = scoreStateForToday(todayRow?.recovery, calibrating, carriedDay, today = nowKey)) {
        is ScoreState.Scored -> todayRow?.let { RecoveryRead.Scored(state.value, it, carried = false, stale = false) }
        is ScoreState.CarriedLastNight -> carriedDay?.recovery?.let {
            RecoveryRead.Scored(it, carriedDay, carried = true, stale = state.stale)
        }
        is ScoreState.Calibrating -> RecoveryRead.Calibrating(state.nightsRemaining)
        ScoreState.NeedsStrap -> null
    } ?: RecoveryRead.NoData

    fun score(row: DailyMetric): Double? = importedPerf[row.day] ?: RestScorer.restFromDaily(row)
    val ownSleep = todayRow?.let(::score)
    val lastSleepRow = days.lastOrNull { it.day < todayKey && it.day <= nowKey && score(it) != null }
    val sleepValue = freshRestScore(
        todayValue = ownSleep,
        lastDay = lastSleepRow?.day,
        lastValue = lastSleepRow?.let(::score),
        isTodaySelected = true,
        today = nowKey,
    )
    val sleepDay = when {
        ownSleep != null -> todayRow?.day
        sleepValue != null -> lastSleepRow?.day
        else -> null
    }
    val sleep = SleepRead(
        value = sleepValue,
        dayKey = sleepDay,
        carried = ownSleep == null && sleepValue != null,
        imported = sleepDay != null && importedPerf[sleepDay] != null,
    )
    return CleanToday(
        days = days,
        todayKey = todayKey,
        todayRow = todayRow,
        recovery = recovery,
        strainStored = todayRow?.strain,
        sleep = sleep,
        baselines = baselines,
        importedPerf = importedPerf,
    )
}

/** Loads the export's sleep-performance figures (the canonical import namespace, as the classic screens). */
private suspend fun loadImportedSeries(vm: AppViewModel): ImportedSleepSeries {
    suspend fun load(key: String) = runCatching {
        vm.repo.metricSeries("my-whoop", key, "0000-00-00", "9999-99-99")
    }.getOrDefault(emptyList()).associate { it.day to it.value }
    return ImportedSleepSeries(
        performance = load("sleep_performance"),
        consistency = load("sleep_consistency"),
        needMin = load("sleep_need_min"),
        debtMin = load("sleep_debt_min"),
    )
}

/** Today's resolution for the clean screens, recomputed whenever the underlying rows change. */
@Composable
internal fun rememberCleanToday(vm: AppViewModel): CleanToday {
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val baselines by vm.chargeBaselines.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hrvEpoch = remember { NoopPrefs.of(context).getLong(Baselines.hrvBaselineEpochKey, 0L).toDouble() }
    var importedPerf by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(days) { importedPerf = loadImportedSeries(vm).performance }
    val logicalKey = logicalDayKeyNow()
    val localKey = LocalDate.now().toString()
    return remember(today, days, baselines, importedPerf, hrvEpoch, logicalKey, localKey) {
        resolveCleanToday(days, today, baselines, importedPerf, hrvEpoch, logicalKey, localKey)
    }
}

/** The strap link as the header pill shows it. Projected so a heart-rate packet does not recompose. */
internal data class LinkStatus(val connected: Boolean, val syncing: Boolean, val batteryPct: Int?, val lastSyncAt: Long?)

@Composable
internal fun rememberLinkStatus(vm: AppViewModel): LinkStatus {
    val flow = remember(vm) {
        vm.live.map {
            LinkStatus(
                connected = it.connected,
                syncing = it.backfilling || it.analyzingHistory,
                batteryPct = it.batteryPct?.let { pct -> pct.toInt().coerceIn(0, 100) },
                lastSyncAt = it.lastSyncAt,
            )
        }.distinctUntilChanged()
    }
    val status by flow.collectAsStateWithLifecycle(LinkStatus(false, false, null, null))
    return status
}

// MARK: Nights

/** One night as the clean Sleep page shows it. */
internal data class CleanNight(
    val dayKey: String,
    val row: DailyMetric?,
    val onset: Long,
    val wake: Long,
    val runs: List<StageRun>,
    val stages: Stages?,
    val score: Double?,
    val importedScore: Boolean,
    val sparse: Boolean,
    val typicalDeepMin: Double?,
    val typicalRemMin: Double?,
    val typicalLightMin: Double?,
    /** Bedtime regularity 0-100 as the classic Sleep tab scores it (latest and mean), full history. */
    val regularity: Double?,
    val regularityTypical: Double?,
)

/** The loaded sleep blocks plus what is needed to pick a night out of them. */
internal class CleanNights(
    private val sleeps: List<SleepSession>,
    private val days: List<DailyMetric>,
    private val imported: ImportedSleepSeries,
    private val habitualMidsleep: Long?,
    private val is24h: Boolean,
) {
    /** The browsable nights, newest first, grouped by the local day they end on (as the classic tab). */
    private val navDays: List<List<SleepSession>> = sleeps.groupBy { localEndDay(it.endTs) }
        .toSortedMap(reverseOrder())
        .map { (_, blocks) -> blocks.sortedBy { it.effectiveStartTs } }

    private val napMinutes: Map<String, Double> by lazy { napSleepMinutesByDay(sleeps, habitualMidsleep) }

    val count: Int get() = navDays.size

    /** The night [offset] nights back from the newest (0 = last night), or null when there is none. */
    fun night(offset: Int): CleanNight? {
        if (navDays.isEmpty()) return null
        val night = selectNight(navDays, days, offset.coerceIn(0, navDays.size - 1), habitualMidsleep, emptyMap(), is24h)
            ?: return null
        val model = buildSleepModel(
            days, night.session, imported,
            selectedDay = night.dayKey,
            heroStages = night.groupStages,
            heroSegments = night.groupSegments,
            napSleepMinByDay = napMinutes,
            sessions = sleeps,
            is24h = is24h,
        )
        val display = heroDisplay(model, night)
        val segments = display?.hypnogramSegments.orEmpty().map { Triple(it.stage, it.start, it.end) }
        val group = night.heroGroup.ifEmpty { listOf(night.session) }
        return CleanNight(
            dayKey = night.dayKey,
            row = days.lastOrNull { it.day == night.dayKey },
            onset = night.heroOnsetTs ?: night.session.effectiveStartTs,
            wake = night.heroWakeTs ?: night.session.endTs,
            runs = stageRuns(segments),
            stages = display?.stages,
            score = heroPerformanceScore(night, days, imported),
            importedScore = imported.performance[night.dayKey] != null,
            sparse = group.any { it.stagingSparse == true },
            typicalDeepMin = model?.typicalDeepMin,
            typicalRemMin = model?.typicalRemMin,
            typicalLightMin = model?.typicalLightMin,
            regularity = model?.consistency?.latest,
            regularityTypical = model?.consistency?.typical,
        )
    }

    /** The trailing nights' bed-to-wake spans, bridged the way the hero bridges a night. */
    fun spans(limit: Int = 14): List<Pair<Long, Long>> = consistencyNightSpans(sleeps, habitualMidsleep, limit)

    private fun localEndDay(ts: Long): String {
        val offsetSec = (java.util.TimeZone.getDefault().getOffset(ts * 1000) / 1000).toLong()
        return AnalyticsEngine.dayString(ts, offsetSec)
    }
}

/**
 * The night list for the clean screens, loaded the way the classic Sleep tab loads it: the active strap's
 * imported and computed blocks, merged per local wake-day with the richness rule, ordered by onset.
 */
@Composable
internal fun rememberCleanNights(vm: AppViewModel, days: List<DailyMetric>): CleanNights? {
    val context = LocalContext.current
    val is24h = ClockPrefs.uses24Hour(context)
    var sleeps by remember { mutableStateOf<List<SleepSession>?>(null) }
    var habitual by remember { mutableStateOf<Long?>(null) }
    var imported by remember { mutableStateOf(ImportedSleepSeries()) }
    LaunchedEffect(days, vm.activeStrapId) {
        val strap = vm.activeStrapId
        habitual = runCatching { vm.repo.habitualMidsleepSec(strap) }.getOrNull()
        imported = loadImportedSeries(vm)
        sleeps = runCatching {
            val now = System.currentTimeMillis() / 1000L
            val importedBlocks = vm.repo.sleepSessionsUnion(strap, 0L, now)
            val computedBlocks = vm.repo.computedSleepSessionsUnion(strap, 0L, now)
            WhoopRepository.mergeSleepRichness(importedBlocks, computedBlocks) { block ->
                val offsetSec = (java.util.TimeZone.getDefault().getOffset(block.endTs * 1000) / 1000).toLong()
                AnalyticsEngine.dayString(block.endTs, offsetSec)
            }.sortedBy { it.effectiveStartTs }
        }.getOrDefault(emptyList())
    }
    val loaded = sleeps ?: return null
    return remember(loaded, days, imported, habitual, is24h) { CleanNights(loaded, days, imported, habitual, is24h) }
}

// MARK: Formatting

/** Locale-aware formatting for the clean screens: numbers, durations, clock times and dates. */
internal class CleanFormat(private val context: Context, val locale: Locale, private val is24h: Boolean) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale)
    private val hourFmt = DateTimeFormatter.ofPattern(if (is24h) "HH" else "h a", locale)
    private val dayMonthFmt = DateTimeFormatter.ofPattern(context.getString(R.string.clean_pattern_day_month), locale)
    private val axisFmt = DateTimeFormatter.ofPattern(context.getString(R.string.clean_pattern_axis_date), locale)
    private val longFmt = DateTimeFormatter.ofPattern(context.getString(R.string.clean_pattern_long_date), locale)

    fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    fun int(value: Double): String = String.format(locale, "%d", kotlin.math.round(value).toLong())
    fun decimal(value: Double, decimals: Int = 1): String = formatDecimal(value, decimals, locale)
    fun signed(value: Double, decimals: Int = 0): String = formatSigned(value, decimals, locale)
    fun percent(value: Double): String = context.getString(R.string.clean_fmt_percent, int(value))

    /** "7:12 h". */
    fun hoursMinutes(minutes: Double): String {
        val (h, m) = hoursAndMinutes(minutes)
        return context.getString(R.string.clean_fmt_hours_minutes, h, m)
    }

    /** "48 min", or "1:05 h" from an hour up. */
    fun duration(minutes: Double): String {
        val (h, m) = hoursAndMinutes(minutes)
        return if (h == 0) context.getString(R.string.clean_fmt_minutes, m) else hoursMinutes(minutes)
    }

    fun time(epochSec: Long): String = Instant.ofEpochSecond(epochSec).atZone(zone).format(timeFmt)
    fun hour(epochSec: Long): String = Instant.ofEpochSecond(epochSec).atZone(zone).format(hourFmt)
    /** The short weekday without its abbreviation dot ("Mo", "Mon"); java.time has no two-letter pattern. */
    fun weekday(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale).removeSuffix(".")
    fun dayMonth(date: LocalDate): String = date.format(dayMonthFmt)
    fun axisDate(date: LocalDate): String = date.format(axisFmt)
    fun longDate(date: LocalDate): String = date.format(longFmt).replaceFirstChar { it.titlecase(locale) }

    /** "Today" / "Yesterday" / the date, for naming a night or a day. */
    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> context.getString(R.string.clean_today)
        today.minusDays(1) -> context.getString(R.string.clean_yesterday)
        else -> dayMonth(date)
    }
}

@Composable
internal fun rememberCleanFormat(): CleanFormat {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val is24h = ClockPrefs.uses24Hour(context)
    return remember(context, locale, is24h) { CleanFormat(context, locale, is24h) }
}

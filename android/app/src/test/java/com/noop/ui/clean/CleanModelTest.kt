package com.noop.ui.clean

import com.noop.analytics.BaselineState
import com.noop.analytics.BaselineStatus
import com.noop.analytics.RestScorer
import com.noop.data.DailyMetric
import com.noop.ui.OptimalStrainRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Pure read-model rules behind the clean design (com.noop.ui.clean). */
class CleanModelTest {

    private fun row(day: String, block: DailyMetric.() -> DailyMetric = { this }) =
        DailyMetric(deviceId = "my-whoop", day = day).block()

    // MARK: Recovery zones, verdict, target

    @Test
    fun zonesResolveFromTheShownPercentNotTheRawValue() {
        // 66.6 reads "67 %" on screen, so it must be coloured and advised as the high band.
        assertEquals(RecoveryZone.HIGH, recoveryZone(66.6))
        assertEquals(RecoveryZone.MID, recoveryZone(66.4))
        assertEquals(RecoveryZone.MID, recoveryZone(33.5))
        assertEquals(RecoveryZone.LOW, recoveryZone(33.4))
        assertEquals(RecoveryZone.HIGH, recoveryZone(100.0))
        assertEquals(RecoveryZone.LOW, recoveryZone(0.0))
    }

    @Test
    fun verdictAndStrainTargetFollowTheSameBand() {
        assertEquals(DayVerdict.PUSH, dayVerdict(66.6))
        assertEquals(OptimalStrainRange(14, 18), strainTarget(66.6))
        assertEquals(DayVerdict.MAINTAIN, dayVerdict(50.0))
        assertEquals(OptimalStrainRange(10, 14), strainTarget(50.0))
        assertEquals(DayVerdict.RECOVER, dayVerdict(20.0))
        assertEquals(OptimalStrainRange(4, 10), strainTarget(20.0))
        assertNull(dayVerdict(null))
        assertNull(strainTarget(null))
    }

    // MARK: Strain

    @Test
    fun strainMapsTheStoredEffortAxisOntoTwentyOne() {
        assertEquals(21.0, strainOn21(100.0), 1e-9)
        assertEquals(10.5, strainOn21(50.0), 1e-9)
        assertEquals(0.0, strainOn21(-3.0), 1e-9)
    }

    @Test
    fun strainLevelsReadTheOneDecimalShownValue() {
        assertEquals(StrainLevel.LIGHT, strainLevel(9.94))
        assertEquals(StrainLevel.MODERATE, strainLevel(9.96)) // shown as 10.0
        assertEquals(StrainLevel.HIGH, strainLevel(14.0))
        assertEquals(StrainLevel.ALL_OUT, strainLevel(18.0))
        val target = OptimalStrainRange(10, 14)
        assertEquals(TargetStatus.BELOW, strainTargetStatus(9.94, target))
        assertEquals(TargetStatus.WITHIN, strainTargetStatus(9.96, target))
        assertEquals(TargetStatus.WITHIN, strainTargetStatus(14.04, target))
        assertEquals(TargetStatus.ABOVE, strainTargetStatus(14.06, target))
    }

    // MARK: Vitals

    private fun baseline(mean: Double, spread: Double) =
        BaselineState(baseline = mean, spread = spread, nValid = 20, nightsSinceUpdate = 0, status = BaselineStatus.TRUSTED)

    @Test
    fun baselineVitalsReadTheirToneInTheDirectionThatMatters() {
        val hrvBase = baseline(60.0, 8.0) // sigma = 10.024
        assertEquals(VitalTone.GOOD, baselineVital(VitalKind.HRV, 75.0, hrvBase, higherIsBetter = true)!!.tone)
        assertEquals(VitalTone.NORMAL, baselineVital(VitalKind.HRV, 65.0, hrvBase, higherIsBetter = true)!!.tone)
        assertEquals(VitalTone.WATCH, baselineVital(VitalKind.HRV, 45.0, hrvBase, higherIsBetter = true)!!.tone)
        val rhrBase = baseline(50.0, 3.0)
        assertEquals(VitalTone.GOOD, baselineVital(VitalKind.RESTING_HR, 44.0, rhrBase, higherIsBetter = false)!!.tone)
        assertEquals(VitalTone.WATCH, baselineVital(VitalKind.RESTING_HR, 56.0, rhrBase, higherIsBetter = false)!!.tone)
        val stat = baselineVital(VitalKind.HRV, 75.0, hrvBase, higherIsBetter = true)!!
        assertEquals(15.0, stat.delta!!, 1e-9)
        assertTrue(stat.bandLow!! < 60.0 && stat.bandHigh!! > 60.0)
    }

    @Test
    fun aLearningBaselineShowsTheValueWithoutARange() {
        val learning = BaselineState(50.0, 5.0, nValid = 2, nightsSinceUpdate = 0, status = BaselineStatus.CALIBRATING)
        val stat = baselineVital(VitalKind.HRV, 70.0, learning, higherIsBetter = true)!!
        assertNull(stat.normal)
        assertNull(stat.bandLow)
        assertEquals(VitalTone.NORMAL, stat.tone)
        assertNull(baselineVital(VitalKind.HRV, null, learning, higherIsBetter = true))
    }

    @Test
    fun skinTemperatureIgnoresAbsoluteImportsAndFlagsEitherDirection() {
        val history = listOf(0.1, -0.2, 0.15, -0.1, 0.05, 33.9) // the 33.9 is an absolute import, dropped
        val warm = skinTempVital(0.9, history)!!
        assertEquals(VitalTone.WATCH, warm.tone)
        assertEquals(0.0, warm.normal!!, 1e-9)
        assertEquals(VitalTone.WATCH, skinTempVital(-0.9, history)!!.tone)
        assertEquals(VitalTone.NORMAL, skinTempVital(0.1, history)!!.tone)
        assertNull(skinTempVital(34.2, history)) // an absolute reading is not a deviation
    }

    // MARK: Sleep breakdown oracle

    @Test
    fun sleepBreakdownAddsUpToTheStoredSleepScore() {
        var checked = 0
        for (tst in listOf(45.0, 180.0, 300.0, 390.0, 432.0, 480.0, 545.0, 640.0)) {
            for (eff in listOf(0.55, 0.78, 0.9, 0.97, 1.0)) {
                for ((deepShare, remShare) in listOf(0.0 to 0.0, 0.05 to 0.2, 0.13 to 0.22, 0.25 to 0.3, 0.4 to 0.5)) {
                    val r = row("2026-11-02") {
                        copy(totalSleepMin = tst, efficiency = eff, deepMin = tst * deepShare, remMin = tst * remShare)
                    }
                    val expected = RestScorer.restFromDaily(r)!!
                    val b = sleepBreakdown(r)!!
                    assertEquals("score for tst=$tst eff=$eff deep=$deepShare rem=$remShare", expected, b.score, 0.0)
                    val parts = b.durationPoints + b.efficiencyPoints + b.restorativePoints + b.regularityPoints
                    assertEquals(expected, parts, 0.006)
                    checked++
                }
            }
        }
        assertEquals(200, checked)
    }

    @Test
    fun sleepBreakdownSkipsRowsItCannotExplain() {
        assertNull(sleepBreakdown(row("2026-11-02") { copy(totalSleepMin = 420.0, efficiency = 93.0) }))
        assertNull(sleepBreakdown(row("2026-11-02") { copy(totalSleepMin = 420.0) }))
        assertNull(sleepBreakdown(row("2026-11-02") { copy(totalSleepMin = 0.0, efficiency = 0.9) }))
    }

    @Test
    fun efficiencyReadsEitherStoredScale() {
        assertEquals(93.0, efficiencyPercent(0.93)!!, 1e-9)
        assertEquals(93.0, efficiencyPercent(93.0)!!, 1e-9)
        assertNull(efficiencyPercent(null))
    }

    // MARK: Stage runs

    @Test
    fun stageRunsJoinEpochsAndDropUnknownStages() {
        val runs = stageRuns(
            listOf(
                Triple("light", 0L, 30L),
                Triple("light", 30L, 60L),
                Triple("deep", 60L, 90L),
                Triple("wake", 90L, 120L),
                Triple("unknown", 120L, 150L),
                Triple("rem", 150L, 180L),
                Triple("rem", 200L, 230L), // 20 s gap, joined
            ),
        )
        assertEquals(
            listOf(
                StageRun(SleepStage.LIGHT, 0, 60),
                StageRun(SleepStage.DEEP, 60, 90),
                StageRun(SleepStage.AWAKE, 90, 120),
                StageRun(SleepStage.REM, 150, 230),
            ),
            runs,
        )
    }

    // MARK: Day series and periods

    @Test
    fun dayValuesKeepMissingDaysAsGaps() {
        val days = listOf(
            row("2026-10-30") { copy(recovery = 40.0) },
            row("2026-11-01") { copy(recovery = 60.0) },
        )
        val values = dayValues(days, LocalDate.parse("2026-11-01"), 3) { it.recovery }
        assertEquals(listOf(40.0, null, 60.0), values.map { it.value })
        assertEquals(LocalDate.parse("2026-10-30"), values.first().day)
    }

    @Test
    fun periodSummaryComparesWithTheEquallyLongWindowBefore() {
        val days = (1..14).map { d -> row(LocalDate.parse("2026-10-19").plusDays(d.toLong()).toString()) { copy(recovery = d.toDouble()) } }
        val summary = periodSummary(days, LocalDate.parse("2026-11-02"), 7) { it.recovery }
        assertEquals(11.0, summary.mean!!, 1e-9) // days 8..14
        assertEquals(4.0, summary.previousMean!!, 1e-9) // days 1..7
        assertEquals(7.0, summary.change!!, 1e-9)
        assertEquals(7, summary.count)
    }

    // MARK: Formatting

    @Test
    fun signedNumbersUseTheLocaleAndATrueMinus() {
        assertEquals("+1,3", formatSigned(1.25, 1, Locale.GERMANY))
        assertEquals("${MINUS}2", formatSigned(-2.0, 0, Locale.GERMANY))
        assertEquals("0,0", formatSigned(-0.01, 1, Locale.GERMANY))
        assertEquals("+12", formatSigned(12.0, 0, Locale.US))
        assertEquals(1 to 0, hoursAndMinutes(59.6))
        assertEquals(7 to 12, hoursAndMinutes(432.0))
    }

    // MARK: Today resolution

    @Test
    fun aScoredTodayIsShownAsIs() {
        val days = listOf(
            row("2026-11-01") { copy(recovery = 55.0) },
            row("2026-11-02") { copy(recovery = 72.0, strain = 40.0, totalSleepMin = 420.0, efficiency = 0.9) },
        )
        val t = resolveCleanToday(days, null, null, emptyMap(), 0.0, "2026-11-02", "2026-11-02")
        val rec = t.recovery as RecoveryRead.Scored
        assertEquals(72.0, rec.value, 0.0)
        assertEquals(false, rec.carried)
        assertEquals(8.4, t.strain21!!, 1e-9)
        assertEquals(RestScorer.restFromDaily(days.last()), t.sleep.value)
        assertEquals(false, t.sleep.carried)
    }

    @Test
    fun anUnscoredTodayCarriesTheLastNightAndTheImportWinsForSleep() {
        val days = listOf(
            row("2026-11-01") { copy(recovery = 55.0, totalSleepMin = 400.0, efficiency = 0.88) },
            row("2026-11-02") { copy(strain = 10.0) },
        )
        val t = resolveCleanToday(days, null, null, mapOf("2026-11-01" to 91.0), 0.0, "2026-11-02", "2026-11-02")
        val rec = t.recovery as RecoveryRead.Scored
        assertEquals(55.0, rec.value, 0.0)
        assertEquals(true, rec.carried)
        assertEquals("2026-11-01", rec.row.day)
        assertEquals(91.0, t.sleep.value!!, 0.0)
        assertEquals(true, t.sleep.carried)
        assertEquals(true, t.sleep.imported)
        // The vitals describe the night the dial shows, not the unscored today.
        assertEquals("2026-11-01", t.vitalsRow!!.day)
    }

    @Test
    fun nothingRecentReadsAsNoData() {
        val t = resolveCleanToday(emptyList(), null, null, emptyMap(), 0.0, "2026-11-02", "2026-11-02")
        assertEquals(RecoveryRead.NoData, t.recovery)
        assertNull(t.sleep.value)
        assertNull(t.strain21)
    }

    @Test
    fun tabsLightTheirOwnRoutesAndMoreForEverythingElse() {
        assertEquals(0, cleanTabIndex(CleanRoutes.HOME))
        assertEquals(0, cleanTabIndex(CleanRoutes.RECOVERY))
        assertEquals(0, cleanTabIndex(CleanRoutes.STRAIN))
        assertEquals(1, cleanTabIndex(CleanRoutes.SLEEP))
        assertEquals(2, cleanTabIndex(CleanRoutes.TRENDS))
        assertEquals(3, cleanTabIndex(CleanRoutes.MORE))
        assertEquals(3, cleanTabIndex("workouts"))
        assertEquals(3, cleanTabIndex(null))
    }

    @Test
    fun aClassicScreenKeepsTheTabItWasOpenedFromLit() {
        // The graph entry at the bottom has no route.
        assertEquals(1, cleanActiveTab(listOf(null, CleanRoutes.HOME, CleanRoutes.SLEEP, "sleep")))
        assertEquals(3, cleanActiveTab(listOf(null, CleanRoutes.HOME, CleanRoutes.MORE, "settings", "devices")))
        assertEquals(0, cleanActiveTab(listOf(null, CleanRoutes.HOME, CleanRoutes.STRAIN, "workouts")))
        assertEquals(0, cleanActiveTab(listOf(null, CleanRoutes.HOME, "devices")))
        assertEquals(2, cleanActiveTab(listOf(null, CleanRoutes.HOME, CleanRoutes.TRENDS)))
        assertEquals(3, cleanActiveTab(listOf(null, "today")))
        // A clean page reached from More keeps More lit; reached from Home, Home.
        assertEquals(3, cleanActiveTab(listOf(null, CleanRoutes.HOME, CleanRoutes.MORE, CleanRoutes.STRESS)))
        assertEquals(0, cleanActiveTab(listOf(null, CleanRoutes.HOME, CleanRoutes.JOURNAL)))
    }

    @Test
    fun weeklySeriesChangeComparesWithTheLastValueAMonthOld() {
        fun r(day: String, v: Double) = com.noop.data.MetricSeriesRow("my-whoop-noop", day, "body_age", v)
        val series = listOf(r("2026-09-20", 31.0), r("2026-09-27", 30.8), r("2026-10-04", 30.5), r("2026-10-25", 30.1), r("2026-11-01", 29.9))
        // 30 days before 1 Nov is 2 Oct; the last stored week on or before it is 27 Sep (30.8).
        assertEquals(29.9 - 30.8, seriesChange(series)!!, 1e-9)
        assertNull(seriesChange(series.takeLast(1)))
        assertNull(seriesChange(emptyList()))
    }

    @Test
    fun bedtimePlansTimeInBedFromTheNeedAndTheUsualEfficiency() {
        val wake = java.time.LocalDateTime.of(2026, 11, 3, 6, 30)
        // 480 min of sleep at 90 % efficiency is 533 min in bed: 21:37 the evening before.
        assertEquals(java.time.LocalDateTime.of(2026, 11, 2, 21, 37), plannedBedtime(wake, 480.0, 90.0))
        // Unknown efficiency plans the need itself; absurd efficiencies are clamped to 60 %.
        assertEquals(java.time.LocalDateTime.of(2026, 11, 2, 22, 30), plannedBedtime(wake, 480.0, null))
        assertEquals(java.time.LocalDateTime.of(2026, 11, 2, 17, 10), plannedBedtime(wake, 480.0, 20.0))
    }

    @Test
    fun theNextWakeIsTodayWhileItIsStillAheadElseTomorrow() {
        val evening = java.time.LocalDateTime.of(2026, 11, 2, 22, 0)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 3, 6, 30), nextAt(390, evening))
        val early = java.time.LocalDateTime.of(2026, 11, 3, 2, 0)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 3, 6, 30), nextAt(390, early))
        assertEquals(390, medianMinute(listOf(400, 380, 390)))
        assertNull(medianMinute(emptyList()))
    }

    @Test
    fun alarmsHonourTheirWeekdaysInTheCalendarEncoding() {
        // 2 Nov 2026 is a Monday: Calendar.MONDAY == 2.
        val monday = LocalDate.parse("2026-11-02")
        assertEquals(2, calendarDayOfWeek(monday))
        assertEquals(1, calendarDayOfWeek(monday.plusDays(6))) // Sunday
        assertEquals(420, alarmMinuteOn(monday, true, 420, emptySet(), emptyMap()))
        assertNull(alarmMinuteOn(monday, true, 420, setOf(1, 7), emptyMap()))
        assertEquals(360, alarmMinuteOn(monday, true, 420, setOf(2), mapOf(2 to 360)))
        assertNull(alarmMinuteOn(monday, false, 420, emptySet(), emptyMap()))
        val now = java.time.LocalDateTime.of(2026, 11, 2, 23, 0)
        // Monday's alarm has passed; Tuesday (3) is not an alarm day, so nothing within two days.
        assertNull(nextAlarm(now) { alarmMinuteOn(it, true, 420, setOf(2), emptyMap()) })
        assertEquals(
            java.time.LocalDateTime.of(2026, 11, 3, 7, 0),
            nextAlarm(now) { alarmMinuteOn(it, true, 420, emptySet(), emptyMap()) },
        )
    }

    @Test
    fun theBriefIsShownAsPlainText() {
        val md = "## Today\n\n\n**Recovery** is high.\n- Train hard\n* Sleep early\n"
        assertEquals("Today\n\nRecovery is high.\n• Train hard\n• Sleep early", briefPlainText(md))
    }

    @Test
    fun niceTicksAreRoundAndCoverTheRange() {
        assertEquals(listOf(0.0, 20.0, 40.0, 60.0), niceTicks(60.0))
        val ticks = niceTicks(80.0, 30.0)
        assertTrue(ticks.first() >= 30.0 && ticks.last() <= 80.0)
        assertTrue(ticks.zipWithNext().all { (a, b) -> b > a })
    }
}

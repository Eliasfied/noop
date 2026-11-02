package com.noop.ui.clean

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.analytics.HrZoneSet
import com.noop.analytics.HrZones
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import com.noop.ui.WorkoutEditing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

// MARK: - Heart-rate zones
//
// Time in heart-rate zones, on the ONE display zone model the app uses everywhere else (live HR, workout
// splits, zone coaching): ProfileStore.hrZoneSet, five %HRmax zones from the user's max HR or their own
// boundaries. A day's split and a workout's split are therefore the same zones the Live screen buzzes on.

internal fun zoneColor(zone: Int): Color = when (zone) {
    1 -> CleanColors.zone1
    2 -> CleanColors.zone2
    3 -> CleanColors.zone3
    4 -> CleanColors.zone4
    else -> CleanColors.zone5
}

/** Minutes in zones 1..5 plus the zones they were binned against. */
internal data class ZoneSplit(val minutes: List<Double>, val zoneSet: HrZoneSet) {
    val lowMinutes: Double get() = minutes.take(3).sum()
    val highMinutes: Double get() = minutes.drop(3).sum()
    val total: Double get() = minutes.sum()
}

/**
 * Today's time in zones, from the day's banked heart rate (local midnight to now) across the active
 * strap's device union, binned with [HrZones.timeInZone]. Null until read; null minutes when no HR.
 */
@Composable
internal fun rememberDayZones(vm: AppViewModel, dayKey: String): ZoneSplit? {
    val context = LocalContext.current
    var split by remember(dayKey) { mutableStateOf<ZoneSplit?>(null) }
    LaunchedEffect(dayKey, vm.activeStrapId) {
        split = runCatching {
            withContext(Dispatchers.Default) {
                val zone = ZoneId.systemDefault()
                val day = LocalDate.parse(dayKey)
                val from = day.atStartOfDay(zone).toEpochSecond()
                val to = minOf(day.plusDays(1).atStartOfDay(zone).toEpochSecond(), System.currentTimeMillis() / 1000L)
                val samples = vm.repo.hrSamplesUnion(vm.activeStrapId, from, to, limit = 200_000)
                val zoneSet = ProfileStore.from(context).hrZoneSet
                val tiz = HrZones.timeInZone(samples, zoneSet)
                ZoneSplit(tiz.seconds.map { it / 60.0 }, zoneSet)
            }
        }.getOrNull()
    }
    return split
}

/** One stacked bar of zones 1..5, widths animated in. */
@Composable
internal fun ZoneBar(minutes: List<Double>, modifier: Modifier = Modifier) {
    val total = minutes.sum().takeIf { it > 0 } ?: return
    Canvas(modifier.fillMaxWidth().height(CleanDims.stageBarHeight).clip(RoundedCornerShape(CleanDims.pillRadius))) {
        var x = 0f
        val gap = 2.dp.toPx()
        minutes.forEachIndexed { i, m ->
            val w = (m / total).toFloat() * size.width
            if (w > gap) drawRect(zoneColor(i + 1), topLeft = Offset(x, 0f), size = Size(w - gap, size.height))
            x += w
        }
    }
}

/**
 * The zone card: WHOOP-style totals for zones 1-3 and 4-5, the split as one bar, and each zone's bpm
 * range with its minutes.
 */
@Composable
internal fun ZonesCard(
    title: String,
    explanation: String,
    split: ZoneSplit?,
    fmt: CleanFormat,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier) {
        Text(title, style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(explanation, style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(12.dp))
        if (split == null || split.total < 1.0) {
            Text(
                stringResource(if (split == null) R.string.clean_loading else R.string.clean_zones_none),
                style = CleanType.callout,
                color = CleanColors.textTertiary,
            )
            return@GlassCard
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(fmt.hoursMinutes(split.lowMinutes), style = CleanType.bigNumber, color = CleanColors.zone2)
                Text(stringResource(R.string.clean_zones_low), style = CleanType.caption, color = CleanColors.textTertiary)
            }
            Column(Modifier.weight(1f)) {
                Text(fmt.hoursMinutes(split.highMinutes), style = CleanType.bigNumber, color = CleanColors.zone4)
                Text(stringResource(R.string.clean_zones_high), style = CleanType.caption, color = CleanColors.textTertiary)
            }
        }
        Spacer(Modifier.height(12.dp))
        ZoneBar(split.minutes)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            split.zoneSet.zones.forEachIndexed { i, z ->
                val minutes = split.minutes.getOrElse(i) { 0.0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(zoneColor(z.number), size = 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.clean_zone_n, z.number),
                        style = CleanType.body,
                        color = CleanColors.textPrimary,
                        modifier = Modifier.width(72.dp),
                    )
                    Text(
                        stringResource(
                            R.string.clean_zone_range,
                            z.lower.roundToInt(),
                            z.upper.roundToInt(),
                            (z.lowerPct * 100).roundToInt(),
                            (z.upperPct * 100).roundToInt(),
                        ),
                        style = CleanType.caption,
                        color = CleanColors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(fmt.duration(minutes), style = CleanType.smallNumber, color = CleanColors.textPrimary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.clean_zones_basis, split.zoneSet.maxHR.roundToInt()),
            style = CleanType.caption,
            color = CleanColors.textTertiary,
        )
    }
}

/** Localised labels for the most common sports; anything else keeps the app's own sport name. */
private val sportLabels: Map<String, Int> = mapOf(
    "running" to R.string.clean_sport_running,
    "treadmill run" to R.string.clean_sport_treadmill_run,
    "walking" to R.string.clean_sport_walking,
    "treadmill walk" to R.string.clean_sport_treadmill_walk,
    "hiking" to R.string.clean_sport_hiking,
    "cycling" to R.string.clean_sport_cycling,
    "indoor cycle" to R.string.clean_sport_indoor_cycle,
    "pool swim" to R.string.clean_sport_swimming,
    "open-water swim" to R.string.clean_sport_open_water,
    "rowing" to R.string.clean_sport_rowing,
    "row machine" to R.string.clean_sport_rowing,
    "elliptical" to R.string.clean_sport_elliptical,
    "strength" to R.string.clean_sport_strength,
    "weightlifting" to R.string.clean_sport_strength,
    "traditional strength training" to R.string.clean_sport_strength,
    "functional strength training" to R.string.clean_sport_functional,
    "hiit" to R.string.clean_sport_hiit,
    "yoga" to R.string.clean_sport_yoga,
    "pilates" to R.string.clean_sport_pilates,
    "boxing" to R.string.clean_sport_boxing,
    "soccer" to R.string.clean_sport_soccer,
    "tennis" to R.string.clean_sport_tennis,
    "padel" to R.string.clean_sport_padel,
    "climbing" to R.string.clean_sport_climbing,
    "stretching" to R.string.clean_sport_stretching,
    "skiing" to R.string.clean_sport_skiing,
    "dancing" to R.string.clean_sport_dancing,
    "martial arts" to R.string.clean_sport_martial_arts,
    "other" to R.string.clean_sport_other,
    "detected" to R.string.clean_activity_generic,
    "activity" to R.string.clean_activity_generic,
)

/** A workout's sport as a readable, localised label. */
@Composable
internal fun sportLabel(sport: String): String {
    val shown = WorkoutEditing.displaySport(sport)
    return sportLabels[shown.trim().lowercase()]?.let { stringResource(it) } ?: shown
}

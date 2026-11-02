package com.noop.ui.clean

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.noop.R
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.rememberPoseStill
import kotlinx.coroutines.delay
import java.time.LocalDate

// MARK: - Live numbers on Home
//
// The day's numbers as a grid of tiles: heart rate now, then last night's HRV, resting HR, breathing,
// skin temperature and SpO2 (each with its distance from the wearer's normal and the normal range as a
// small bar), then today's steps and calories. The vitals come from the same resolution as the recovery
// dial (vitalsFor), so a tile and the recovery page always describe the same night.

/** Heart rate as the Home tile shows it: live when the strap is streaming, else the last banked sample. */
internal data class HeartNow(val bpm: Int?, val live: Boolean, val atEpochSec: Long?)

/** How long a banked sample may be old and still stand in for "now". */
private const val HR_FALLBACK_WINDOW_SEC = 3_600L

@Composable
internal fun rememberHeartNow(vm: AppViewModel): HeartNow {
    val liveBpm by vm.bpm.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var banked by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    LaunchedEffect(vm.activeStrapId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis() / 1000L
                banked = runCatching {
                    vm.repo.hrSamplesUnion(vm.activeStrapId, now - HR_FALLBACK_WINDOW_SEC, now, limit = 5_000)
                        .maxByOrNull { it.ts }?.let { it.bpm to it.ts }
                }.getOrNull()
                delay(60_000L)
            }
        }
    }
    val live = liveBpm
    return when {
        live != null && live > 0 -> HeartNow(live, live = true, atEpochSec = null)
        banked != null -> HeartNow(banked!!.first, live = false, atEpochSec = banked!!.second)
        else -> HeartNow(null, live = false, atEpochSec = null)
    }
}

/** A heart that beats at [bpm] while live; still when motion is quieted or the value is not live. */
@Composable
private fun BeatingHeart(bpm: Int?, live: Boolean, modifier: Modifier = Modifier) {
    val still = rememberPoseStill()
    val period = ((60_000 / (bpm ?: 60).coerceIn(30, 220))).coerceAtLeast(270)
    val beat: State<Float> = if (still || !live) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "heartbeat").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(period, easing = CleanMotion.standard), RepeatMode.Restart),
            label = "beat",
        )
    }
    Icon(
        Icons.Rounded.Favorite,
        contentDescription = null,
        tint = CleanColors.recoveryLow,
        modifier = modifier.size(18.dp).graphicsLayer {
            // A quick double beat at the start of each period, then rest.
            val t = beat.value
            val pulse = when {
                t < 0.12f -> t / 0.12f
                t < 0.24f -> 1f - (t - 0.12f) / 0.12f
                t < 0.34f -> (t - 0.24f) / 0.10f * 0.6f
                t < 0.46f -> 0.6f - (t - 0.34f) / 0.12f * 0.6f
                else -> 0f
            }
            scaleX = 1f + 0.22f * pulse
            scaleY = 1f + 0.22f * pulse
        },
    )
}

/** One number tile of the grid. */
@Composable
private fun NumberTile(
    label: String,
    value: String,
    unit: String?,
    caption: String,
    captionColor: Color,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    arrowUp: Boolean? = null,
    range: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    GlassCard(modifier, padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = CleanType.label, color = CleanColors.textTertiary, maxLines = 1, modifier = Modifier.weight(1f))
            icon?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = CleanType.bigNumber, color = CleanColors.textPrimary, maxLines = 1)
            if (unit != null) {
                Text(unit, style = CleanType.unit, color = CleanColors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (arrowUp != null) {
                Icon(
                    if (arrowUp) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = captionColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(caption, style = CleanType.caption, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (range != null) {
            Spacer(Modifier.height(8.dp))
            range()
        }
    }
}

/** The short value of a vital (without the unit) and its unit, for a tile. */
private fun vitalNumber(kind: VitalKind, value: Double, fmt: CleanFormat): Pair<String, String?> = when (kind) {
    VitalKind.HRV -> fmt.int(value) to fmt.string(R.string.clean_unit_ms)
    VitalKind.RESTING_HR -> fmt.int(value) to fmt.string(R.string.clean_unit_bpm)
    VitalKind.RESPIRATION -> fmt.decimal(value) to fmt.string(R.string.clean_unit_rpm)
    VitalKind.SLEEP_DURATION -> fmt.hoursMinutes(value) to null
    VitalKind.SKIN_TEMP -> fmt.signed(value, 1) to "°C"
    VitalKind.SPO2 -> fmt.int(value) to "%"
}

@Composable
private fun VitalTile(stat: VitalStat, fmt: CleanFormat, modifier: Modifier = Modifier) {
    val (value, unit) = vitalNumber(stat.kind, stat.value, fmt)
    val tone = toneColor(stat.tone)
    val delta = stat.delta
    val low = stat.bandLow
    val high = stat.bandHigh
    val inRange = low != null && high != null && stat.value in low..high
    val caption = when {
        delta == null || low == null || high == null -> stringResource(R.string.clean_tile_learning)
        inRange -> stringResource(R.string.clean_tile_normal)
        else -> stringResource(R.string.clean_tile_vs_normal, (if (delta > 0) "+" else MINUS) + vitalDelta(stat.kind, delta, fmt))
    }
    NumberTile(
        label = vitalName(stat.kind),
        value = value,
        unit = unit,
        caption = caption,
        captionColor = if (stat.tone == VitalTone.NORMAL) CleanColors.textSecondary else tone,
        modifier = modifier,
        arrowUp = if (delta != null && !inRange && low != null) delta > 0 else null,
        range = if (low != null && high != null) {
            { RangeBar(stat, tone, Modifier.fillMaxWidth()) }
        } else null,
    )
}

/**
 * The Home number grid: heart rate now, last night's vitals against the wearer's normal, and today's
 * activity, two tiles a row.
 */
@Composable
internal fun NumbersGrid(
    vm: AppViewModel,
    today: CleanToday,
    fmt: CleanFormat,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heart = rememberHeartNow(vm)
    val vitals = remember(today.vitalsRow, today) {
        today.vitalsRow?.let { vitalsFor(it, today) }.orEmpty().filter { it.kind != VitalKind.SLEEP_DURATION }
    }
    val activity: DailyMetric? = today.todayRow
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        add { m ->
            val age = heart.atEpochSec?.let { (System.currentTimeMillis() / 1000L - it).coerceAtLeast(0) }
            NumberTile(
                label = stringResource(R.string.clean_tile_heart_rate),
                value = heart.bpm?.toString() ?: "–",
                unit = if (heart.bpm != null) fmt.string(R.string.clean_unit_bpm) else null,
                caption = when {
                    heart.live -> stringResource(R.string.clean_tile_live)
                    age != null && age < 120 -> stringResource(R.string.clean_tile_just_now)
                    age != null -> stringResource(R.string.clean_tile_minutes_ago, (age / 60).toInt())
                    else -> stringResource(R.string.clean_tile_start_live)
                },
                captionColor = if (heart.live) CleanColors.recoveryLow else CleanColors.textSecondary,
                modifier = m,
                icon = { BeatingHeart(heart.bpm, heart.live) },
                onClick = onOpenLive,
            )
        }
        vitals.forEach { stat -> add { m -> VitalTile(stat, fmt, m) } }
        activity?.steps?.let { steps ->
            add { m ->
                NumberTile(
                    label = stringResource(R.string.clean_steps),
                    value = fmt.int(steps.toDouble()),
                    unit = null,
                    caption = stringResource(R.string.clean_tile_today_estimated),
                    captionColor = CleanColors.textSecondary,
                    modifier = m,
                    icon = { Icon(Icons.AutoMirrored.Rounded.DirectionsRun, null, tint = CleanColors.strainBright, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        activity?.activeKcalEst?.let { kcal ->
            add { m ->
                NumberTile(
                    label = stringResource(R.string.clean_kcal),
                    value = fmt.int(kcal),
                    unit = "kcal",
                    caption = stringResource(R.string.clean_tile_today_estimated),
                    captionColor = CleanColors.textSecondary,
                    modifier = m,
                    icon = { Icon(Icons.Rounded.LocalFireDepartment, null, tint = CleanColors.watch, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
    val night = today.vitalsRow?.day?.let { fmt.dayMonth(LocalDate.parse(it)) }
    Column(modifier) {
        SectionLabel(
            stringResource(R.string.clean_numbers_title),
            trailing = night?.let { stringResource(R.string.clean_vitals_of_night, it) },
        )
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(CleanDims.rowGap)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CleanDims.rowGap), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { tile -> tile(Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.clean_vitals_legend),
            style = CleanType.caption,
            color = CleanColors.textTertiary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

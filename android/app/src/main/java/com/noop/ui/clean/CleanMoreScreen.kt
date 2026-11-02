package com.noop.ui.clean

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.ui.AppViewModel
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.Destination
import com.noop.ui.drawerGroups

// MARK: - Clean More
//
// The door to everything else: the strap's state with a sync button, the design switch back, and every
// classic screen grouped exactly as the classic More page groups them. A classic screen opened from here
// keeps the clean tab bar, with More lit.

@Composable
internal fun CleanMoreScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val fmt = rememberCleanFormat()
    val link = rememberLinkStatus(vm)
    val context = LocalContext.current
    val classic = buildList {
        add(Destination.Today)
        add(Destination.Sleep)
        add(Destination.Trends)
        if (BottomBarStyleStore.coachEnabled) add(Destination.Coach)
    }

    CleanScreen(
        title = stringResource(R.string.clean_tab_more),
        subtitle = stringResource(R.string.clean_more_subtitle),
        accent = CleanColors.auroraNeutral,
    ) {
        StrapCard(link, fmt, onSync = { vm.syncNow() }, onOpenDevices = { onNavigate(Destination.Devices.route) }, Modifier.cleanReveal(1))
        GlassCard(Modifier.cleanReveal(2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Palette, CleanColors.trend)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.clean_design_title), style = CleanType.body, color = CleanColors.textPrimary)
                    Text(stringResource(R.string.clean_design_body), style = CleanType.caption, color = CleanColors.textTertiary)
                }
                Switch(
                    checked = true,
                    onCheckedChange = { on -> if (!on) CleanDesign.set(context, false) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CleanColors.textPrimary,
                        checkedTrackColor = CleanColors.trendDeep,
                        checkedBorderColor = CleanColors.trend,
                    ),
                )
            }
        }
        var index = 3
        CleanPagesGroup(onNavigate, Modifier.cleanReveal(index++))
        DestinationGroup(stringResource(R.string.clean_more_classic), classic, onNavigate, Modifier.cleanReveal(index++))
        drawerGroups.forEach { group ->
            DestinationGroup(stringResource(group.headerRes), group.items, onNavigate, Modifier.cleanReveal(index++))
        }
        HonestyNote(Modifier.cleanReveal(index))
    }
}

@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun StrapCard(
    link: LinkStatus,
    fmt: CleanFormat,
    onSync: () -> Unit,
    onOpenDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier, onClick = onOpenDevices) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(
                when {
                    link.syncing -> CleanColors.strainBright
                    link.connected -> CleanColors.good
                    else -> CleanColors.textTertiary
                },
                pulsing = link.connected || link.syncing,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when {
                            link.syncing -> R.string.clean_link_syncing
                            link.connected -> R.string.clean_link_connected
                            else -> R.string.clean_link_disconnected
                        },
                    ),
                    style = CleanType.headline,
                    color = CleanColors.textPrimary,
                )
                val details = buildList {
                    link.batteryPct?.let { add(stringResource(R.string.clean_battery, fmt.percent(it.toDouble()))) }
                    link.lastSyncAt?.let { add(stringResource(R.string.clean_last_sync, fmt.time(it))) }
                }
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = CleanType.caption, color = CleanColors.textTertiary)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary)
        }
        Spacer(Modifier.height(14.dp))
        val interaction = remember { MutableInteractionSource() }
        val scale = rememberPressScale(interaction)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .glass(RoundedCornerShape(CleanDims.pillRadius), tint = CleanColors.strain)
                .clickable(interaction, indication = null, role = Role.Button, onClick = onSync)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Sync, null, tint = CleanColors.strainBright, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.clean_sync_now), style = CleanType.body, color = CleanColors.textPrimary)
        }
    }
}

/** A clean page listed on More: its route, icon, title and accent. */
private data class CleanPage(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val titleRes: Int, val accent: androidx.compose.ui.graphics.Color)

private val cleanPages = listOf(
    CleanPage(CleanRoutes.STRESS, Icons.Rounded.Spa, R.string.clean_stress_title, CleanColors.trend),
    CleanPage(CleanRoutes.JOURNAL, Icons.Rounded.EditNote, R.string.clean_journal_title, CleanColors.sleep),
    CleanPage(CleanRoutes.WORKOUTS, Icons.Rounded.FitnessCenter, R.string.clean_workouts, CleanColors.strain),
)

@Composable
private fun CleanPagesGroup(onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionLabel(stringResource(R.string.clean_more_pages))
        Spacer(Modifier.height(6.dp))
        GlassCard(padding = 4.dp) {
            cleanPages.forEachIndexed { i, page ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { onNavigate(page.route) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(page.icon, page.accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(page.titleRes),
                        style = CleanType.body,
                        color = CleanColors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary)
                }
                if (i < cleanPages.lastIndex) {
                    HorizontalDivider(color = CleanColors.hairline, modifier = Modifier.padding(start = 62.dp, end = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun DestinationGroup(
    title: String,
    items: List<Destination>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier) {
        SectionLabel(title)
        Spacer(Modifier.height(6.dp))
        GlassCard(padding = 4.dp) {
            items.forEachIndexed { i, dest ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { onNavigate(dest.route) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(dest.icon, CleanColors.textSecondary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(dest.titleRes),
                        style = CleanType.body,
                        color = CleanColors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary)
                }
                if (i < items.lastIndex) {
                    HorizontalDivider(color = CleanColors.hairline, modifier = Modifier.padding(start = 62.dp, end = 14.dp))
                }
            }
        }
    }
}

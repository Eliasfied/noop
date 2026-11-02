package com.noop.ui.clean

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import com.noop.R
import com.noop.ui.AppViewModel
import com.noop.ui.Destination
import com.noop.ui.Metrics
import com.noop.ui.NoopPrefs
import com.noop.ui.NoopType
import com.noop.ui.Palette

// MARK: - Design switch, routes and tab bar
//
// The clean design is an optional second skin. It is OFF by default; the classic More page carries the
// switch to turn it on and the clean More page the switch back. It adds its own routes to the ONE NavHost
// the shell already owns, so every classic screen stays reachable from it unchanged.

/** Whether the clean design is active. Snapshot-backed so flipping it re-routes the shell at once. */
object CleanDesign {
    private const val KEY = "noop.cleanDesign.enabled"
    private var loaded = false

    var enabled by mutableStateOf(false)
        private set

    /** Reads the stored choice once per process; later calls are free. */
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        enabled = NoopPrefs.of(ctx.applicationContext).getBoolean(KEY, false)
    }

    fun set(ctx: Context, value: Boolean) {
        loaded = true
        enabled = value
        NoopPrefs.of(ctx.applicationContext).edit().putBoolean(KEY, value).apply()
    }
}

/** Routes of the clean screens. Distinct from every classic route. */
object CleanRoutes {
    const val HOME = "clean_home"
    const val RECOVERY = "clean_recovery"
    const val STRAIN = "clean_strain"
    const val SLEEP = "clean_sleep"
    const val TRENDS = "clean_trends"
    const val MORE = "clean_more"
    const val STRESS = "clean_stress"
    const val JOURNAL = "clean_journal"
    const val WORKOUTS = "clean_workouts"
    const val WORKOUT = "clean_workout"
    const val HEALTH = "clean_health"
    const val COACH = "clean_coach"
}

/**
 * Opens a clean tab on its ROOT page: pops back to it when the tab is already on the back stack (the user is
 * inside that tab, possibly on a classic page pushed from it), otherwise starts it fresh above Home.
 *
 * Deliberately without saveState/restoreState. The classic tabs save and restore their stacks, but a clean
 * tab's stack can hold a classic screen pushed from it (clean Sleep -> classic Sleep), and restoring that
 * reopened the classic screen on every later tap of the tab, with no way back short of the back button.
 */
fun NavHostController.navigateCleanTab(route: String) {
    if (currentDestination?.route == route) return
    if (popBackStack(route, inclusive = false)) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

/** The clean screens, registered into the shell's NavHost next to the classic ones. */
fun NavGraphBuilder.cleanGraph(nav: NavHostController, vm: AppViewModel) {
    val openCoach: () -> Unit = { nav.navigate(CleanRoutes.COACH) }
    val ask: (String) -> Unit = { askCoach(it, openCoach) }
    composable(CleanRoutes.HOME) {
        CleanHomeScreen(
            vm = vm,
            onOpenRecovery = { nav.navigate(CleanRoutes.RECOVERY) },
            onOpenStrain = { nav.navigate(CleanRoutes.STRAIN) },
            onOpenSleep = { nav.navigateCleanTab(CleanRoutes.SLEEP) },
            onOpenDevices = { nav.navigate(Destination.Devices.route) },
            onOpenStress = { nav.navigate(CleanRoutes.STRESS) },
            onOpenJournal = { nav.navigate(CleanRoutes.JOURNAL) },
            onOpenHealth = { nav.navigate(CleanRoutes.HEALTH) },
            onAsk = ask,
            onOpenCoach = openCoach,
            onOpenLive = { nav.navigate(Destination.Live.route) },
        )
    }
    composable(CleanRoutes.STRESS) {
        CleanStressScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onOpenBreathe = { nav.navigate(Destination.Breathe.route) },
            onAsk = ask,
        )
    }
    composable(CleanRoutes.COACH) {
        CleanCoachScreen(
            appVm = vm,
            onBack = { nav.popBackStack() },
            onOpenSetup = { nav.navigate(Destination.Coach.route) },
            onOpenSettings = {
                // Coach settings resolve the classic Coach entry's view model, so it goes first.
                nav.navigate(Destination.Coach.route)
                nav.navigate(Destination.CoachSettings.route)
            },
        )
    }
    composable(CleanRoutes.HEALTH) {
        CleanHealthScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onOpenClassic = { nav.navigate(Destination.Health.route) },
            onAsk = ask,
        )
    }
    composable(CleanRoutes.JOURNAL) {
        CleanJournalScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onOpenClassic = { nav.navigate(Destination.Insights.route) },
        )
    }
    composable(CleanRoutes.RECOVERY) { CleanRecoveryScreen(vm = vm, onBack = { nav.popBackStack() }, onAsk = ask) }
    composable(CleanRoutes.STRAIN) {
        CleanStrainScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onOpenWorkouts = { nav.navigate(CleanRoutes.WORKOUTS) },
            onOpenWorkout = { nav.openCleanWorkout(it) },
            onAsk = ask,
        )
    }
    composable(CleanRoutes.WORKOUTS) {
        CleanWorkoutsScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onOpenWorkout = { nav.openCleanWorkout(it) },
            onOpenClassic = { nav.navigate(Destination.Workouts.route) },
        )
    }
    composable(
        "${CleanRoutes.WORKOUT}/{device}/{start}",
        arguments = listOf(
            navArgument("device") { type = NavType.StringType },
            navArgument("start") { type = NavType.LongType },
        ),
    ) { entry ->
        CleanWorkoutScreen(
            vm = vm,
            deviceId = entry.arguments?.getString("device").orEmpty(),
            startTs = entry.arguments?.getLong("start") ?: 0L,
            onBack = { nav.popBackStack() },
            onOpenClassic = { nav.navigate(Destination.Workouts.route) },
        )
    }
    composable(CleanRoutes.SLEEP) {
        CleanSleepScreen(
            vm = vm,
            onOpenClassicSleep = { nav.navigate(Destination.Sleep.route) },
            onOpenAlarms = { nav.navigate(Destination.SmartAlarm.route) },
            onAsk = ask,
        )
    }
    composable(CleanRoutes.TRENDS) { CleanTrendsScreen(vm = vm) }
    composable(CleanRoutes.MORE) { CleanMoreScreen(vm = vm, onNavigate = { nav.navigate(it) }) }
}

private data class CleanTab(val route: String, val icon: ImageVector, val labelRes: Int, val accent: Color)

private val cleanTabs = listOf(
    CleanTab(CleanRoutes.HOME, Icons.Rounded.Home, R.string.clean_tab_home, CleanColors.recoveryHigh),
    CleanTab(CleanRoutes.SLEEP, Icons.Rounded.Bedtime, R.string.clean_tab_sleep, CleanColors.sleep),
    CleanTab(CleanRoutes.TRENDS, Icons.Rounded.Insights, R.string.clean_tab_trends, CleanColors.trend),
    CleanTab(CleanRoutes.MORE, Icons.Rounded.GridView, R.string.clean_tab_more, CleanColors.textSecondary),
)

/** Which tab a clean route belongs to: detail pages light their parent; anything else lights More. */
internal fun cleanTabIndex(route: String?): Int = when (route) {
    CleanRoutes.HOME, CleanRoutes.RECOVERY, CleanRoutes.STRAIN -> 0
    CleanRoutes.SLEEP -> 1
    CleanRoutes.TRENDS -> 2
    else -> 3
}

/** The four routes a tab opens on. */
private val tabRoots = setOf(CleanRoutes.HOME, CleanRoutes.SLEEP, CleanRoutes.TRENDS, CleanRoutes.MORE)

/**
 * The lit tab for a back stack (routes bottom to top): the tab whose root page sits nearest below the top.
 * A page opened from a tab keeps that tab lit, clean or classic alike (the classic Sleep screen opened from
 * clean Sleep lights Sleep, Stress opened from More lights More), instead of always reading as More.
 */
internal fun cleanActiveTab(stack: List<String?>): Int =
    cleanTabIndex(stack.lastOrNull { it in tabRoots })

/**
 * The clean design's floating tab bar: a glass capsule whose selection is a lens that glides between tabs
 * on a spring. Drawn by the shell in place of the classic bar, with the same insets and auto-hide.
 */
@Composable
fun CleanTabBar(nav: NavHostController, modifier: Modifier = Modifier) {
    val stack by nav.currentBackStack.collectAsState()
    val selected = cleanActiveTab(stack.map { it.destination.route })
    val onSelect: (String) -> Unit = { nav.navigateCleanTab(it) }
    val tick = rememberTick()
    val shape = RoundedCornerShape(CleanDims.pillRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = CleanDims.tabBarMaxWidth)
                .height(CleanDims.tabBarHeight)
                .shadow(22.dp, shape, ambientColor = CleanColors.shadow, spotColor = CleanColors.shadow)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(CleanColors.glassBar, CleanColors.glassBarDeep)))
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(CleanColors.glassRimBright, CleanColors.glassRimDim, CleanColors.glassRimBright.copy(alpha = 0.12f)),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                    shape,
                )
                .padding(5.dp),
        ) {
            val slot = maxWidth / cleanTabs.size
            val lensX by animateDpAsState(slot * selected, CleanMotion.lens(), label = "tabLens")
            val accent by animateColorAsState(cleanTabs[selected].accent, label = "tabLensTint")
            Box(
                Modifier
                    .offset(x = lensX)
                    .width(slot)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), CleanColors.glassLens)))
                    .border(1.dp, CleanColors.glassRimBright.copy(alpha = 0.45f), shape),
            )
            Row(Modifier.fillMaxSize()) {
                cleanTabs.forEachIndexed { i, tab ->
                    TabSlot(
                        tab = tab,
                        active = i == selected,
                        onClick = {
                            if (i != selected) tick()
                            onSelect(tab.route)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabSlot(tab: CleanTab, active: Boolean, onClick: () -> Unit) {
    val label = stringResource(tab.labelRes)
    val scale by animateFloatAsState(if (active) 1.08f else 1f, CleanMotion.lens(), label = "tabIcon")
    val tint by animateColorAsState(if (active) CleanColors.textPrimary else CleanColors.textSecondary, label = "tabTint")
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(CleanDims.pillRadius))
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = active
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Text(label, style = CleanType.micro, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The entry on the classic More page that switches the clean design on. Drawn in the classic tokens,
 * since it sits on a classic page.
 */
@Composable
fun CleanDesignSwitchRow(onEnabled: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Metrics.cardRadius))
            .background(Palette.surfaceRaised)
            .clickable {
                CleanDesign.set(context, true)
                onEnabled()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.clean_switch_title), style = NoopType.body, color = Palette.textPrimary)
            Text(stringResource(R.string.clean_switch_subtitle), style = NoopType.footnote, color = Palette.textTertiary)
        }
        Switch(
            checked = false,
            onCheckedChange = {
                CleanDesign.set(context, true)
                onEnabled()
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent),
        )
    }
}

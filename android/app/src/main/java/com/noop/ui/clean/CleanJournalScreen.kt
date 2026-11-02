package com.noop.ui.clean

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.RankedEffect
import com.noop.data.JournalEntry
import com.noop.ui.AppViewModel
import com.noop.ui.InsightsHubViewModel
import com.noop.ui.InsightsOutcome
import com.noop.ui.JOURNAL_DEVICE_ID
import com.noop.ui.JournalCatalogItem
import com.noop.ui.JournalGroup
import com.noop.ui.addCustomJournalItem
import com.noop.ui.journalDayKey
import com.noop.ui.loadJournalCatalogItems
import com.noop.ui.normJournalKey
import com.noop.ui.removeJournalItem
import com.noop.ui.resolveJournalItems
import com.noop.ui.saveJournalCatalogItems
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Clean Logbook
//
// Log yesterday's and today's habits in two taps, add habits from a grouped catalogue, and see what each
// one does to recovery. Writes go to the SAME journal table the classic Insights screen writes (under
// JOURNAL_DEVICE_ID), and the "what helps / what hurts" list is the classic Insights Hub ranking
// (InsightsHubViewModel + EffectRanker), so both designs read one journal and agree on its effects.

/** How many days back the logbook can be filled in, matching the classic journal's backfill range. */
private const val LOGBOOK_DAYS = 7

/** A catalogue section: its title, its habits (a string array) and the journal group they land in. */
internal data class HabitSection(val titleRes: Int, val arrayRes: Int, val group: JournalGroup)

internal val habitSections = listOf(
    HabitSection(R.string.clean_habits_nutrition_title, R.array.clean_habits_nutrition, JournalGroup.Nutrition),
    HabitSection(R.string.clean_habits_drinks_title, R.array.clean_habits_drinks, JournalGroup.Nutrition),
    HabitSection(R.string.clean_habits_supplements_title, R.array.clean_habits_supplements, JournalGroup.Supplements),
    HabitSection(R.string.clean_habits_evening_title, R.array.clean_habits_evening, JournalGroup.Lifestyle),
    HabitSection(R.string.clean_habits_training_title, R.array.clean_habits_training, JournalGroup.Behaviour),
    HabitSection(R.string.clean_habits_health_title, R.array.clean_habits_health, JournalGroup.Health),
    HabitSection(R.string.clean_habits_mind_title, R.array.clean_habits_mind, JournalGroup.Lifestyle),
    HabitSection(R.string.clean_habits_context_title, R.array.clean_habits_context, JournalGroup.Other),
)

/**
 * Localised labels for the ten starter questions. Display only: the stored question stays the verbatim
 * starter string the effects engine and imports join on.
 */
internal val starterLabels: Map<String, Int> = mapOf(
    "Did you drink any alcohol?" to R.string.clean_starter_alcohol,
    "Did you have caffeine late in the day?" to R.string.clean_starter_caffeine,
    "Did you view a screen in bed?" to R.string.clean_starter_screen,
    "Did you eat close to bedtime?" to R.string.clean_starter_late_meal,
    "Did you feel stressed?" to R.string.clean_starter_stressed,
    "Did you use a sauna?" to R.string.clean_starter_sauna,
    "Did you share your bed?" to R.string.clean_starter_shared_bed,
    "Did you feel sick or ill?" to R.string.clean_starter_sick,
    "Did you take magnesium?" to R.string.clean_starter_magnesium,
    "Did you read before bed?" to R.string.clean_starter_read,
)

/** The label a habit shows: the user's rename, else a localised starter label, else the stored text. */
@Composable
internal fun habitLabel(item: JournalCatalogItem): String =
    item.displayName ?: starterLabels[item.canonical]?.let { stringResource(it) } ?: item.canonical

@Composable
private fun habitLabel(canonical: String, items: List<JournalCatalogItem>): String {
    val item = items.firstOrNull { normJournalKey(it.canonical) == normJournalKey(canonical) }
    return if (item != null) habitLabel(item) else starterLabels[canonical]?.let { stringResource(it) } ?: canonical
}

@Composable
internal fun journalGroupName(group: JournalGroup): String = stringResource(
    when (group) {
        JournalGroup.Supplements -> R.string.clean_jgroup_supplements
        JournalGroup.Nutrition -> R.string.clean_jgroup_nutrition
        JournalGroup.Lifestyle -> R.string.clean_jgroup_lifestyle
        JournalGroup.Health -> R.string.clean_jgroup_health
        JournalGroup.Behaviour -> R.string.clean_jgroup_behaviour
        JournalGroup.Other -> R.string.clean_jgroup_other
    },
)

/** The outcomes the insights can be read against; sleep is left out because the hub picks efficiency. */
private val insightOutcomes = listOf(InsightsOutcome.Recovery, InsightsOutcome.Hrv, InsightsOutcome.Rhr)

@Composable
internal fun CleanJournalScreen(vm: AppViewModel, onBack: () -> Unit, onOpenClassic: () -> Unit) {
    val fmt = rememberCleanFormat()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    var catalog by remember { mutableStateOf(loadJournalCatalogItems(context)) }
    var imported by remember { mutableStateOf<List<String>>(emptyList()) }
    var answers by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var numeric by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var seq by remember { mutableIntStateOf(0) }
    var dayOffset by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(seq, dayOffset) {
        runCatching {
            val importedRows = vm.repo.journal("my-whoop", "0000-01-01", "9999-12-31")
            val native = vm.repo.journal(JOURNAL_DEVICE_ID, "0000-01-01", "9999-12-31")
            imported = importedRows.map { it.question }.distinct()
            val key = journalDayKey(dayOffset)
            answers = native.filter { it.day == key }.associate { it.question to it.answeredYes }
            numeric = native.filter { it.day == key && it.numericValue != null }.associate { it.question to it.numericValue!! }
        }
    }
    val items = remember(imported, catalog) { resolveJournalItems(imported, catalog, includeHidden = false) }

    fun applyCatalog(next: List<JournalCatalogItem>) {
        saveJournalCatalogItems(context, next)
        catalog = next
    }
    fun answer(question: String, yes: Boolean?) {
        val key = journalDayKey(dayOffset)
        scope.launch {
            runCatching {
                if (yes == null) vm.repo.deleteJournalEntry(JOURNAL_DEVICE_ID, key, question)
                else vm.repo.upsertJournal(listOf(JournalEntry(JOURNAL_DEVICE_ID, key, question, yes)))
            }
            seq++
        }
    }

    val hub = remember { InsightsHubViewModel() }
    LaunchedEffect(days, seq) { runCatching { hub.load(vm, days) } }
    val snapshot by hub.state.collectAsState()

    CleanScreen(
        title = stringResource(R.string.clean_journal_title),
        subtitle = stringResource(R.string.clean_journal_subtitle),
        accent = CleanColors.auroraViolet,
        onBack = onBack,
    ) {
        DayPicker(dayOffset, fmt, { dayOffset = it }, Modifier.cleanReveal(1))
        LogCard(items, answers, numeric, ::answer, Modifier.cleanReveal(2))
        CatalogCard(items, onAdd = { label, group ->
            applyCatalog(addCustomJournalItem(catalog, label, group = group))
        }, onRemove = { canonical ->
            applyCatalog(removeJournalItem(catalog, canonical))
        }, modifier = Modifier.cleanReveal(3))
        InsightsCard(snapshot, hub, items, fmt, Modifier.cleanReveal(4))
        GlassCard(Modifier.cleanReveal(5), onClick = onOpenClassic) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.clean_journal_classic_title), style = CleanType.body, color = CleanColors.textPrimary)
                    Text(stringResource(R.string.clean_journal_classic_body), style = CleanType.caption, color = CleanColors.textTertiary)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = CleanColors.textTertiary)
            }
        }
    }
}

@Composable
private fun DayPicker(offset: Long, fmt: CleanFormat, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (0 until LOGBOOK_DAYS).forEach { back ->
            val date = today.minusDays(back.toLong())
            val active = offset == back.toLong()
            val label = if (back <= 1) fmt.relativeDay(date) else "${fmt.weekday(date)} ${fmt.dayMonth(date)}"
            Box(
                Modifier
                    .clip(RoundedCornerShape(CleanDims.pillRadius))
                    .then(
                        if (active) Modifier.background(CleanColors.auroraViolet.copy(alpha = 0.55f))
                        else Modifier.glass(RoundedCornerShape(CleanDims.pillRadius))
                    )
                    .clickable(role = Role.Tab) { onPick(back.toLong()) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, style = CleanType.callout, color = if (active) CleanColors.textPrimary else CleanColors.textSecondary)
            }
        }
    }
}

@Composable
private fun LogCard(
    items: List<JournalCatalogItem>,
    answers: Map<String, Boolean>,
    numeric: Map<String, Double>,
    onAnswer: (String, Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_journal_log_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_journal_log_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        val logged = items.count { answers.containsKey(it.canonical) }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.clean_journal_logged_count, logged, items.size),
            style = CleanType.caption,
            color = CleanColors.textTertiary,
        )
        JournalGroup.displayOrder.forEach { group ->
            val groupItems = items.filter { it.group == group }.sortedWith(compareBy({ it.sortIndex }, { it.canonical }))
            if (groupItems.isEmpty()) return@forEach
            Spacer(Modifier.height(14.dp))
            Text(journalGroupName(group).uppercase(), style = CleanType.label, color = CleanColors.textTertiary)
            groupItems.forEach { item ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        habitLabel(item),
                        style = CleanType.body,
                        color = CleanColors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.kind.isNumeric) {
                        Text(
                            numeric[item.canonical]?.let { v -> "${v.roundToInt()} ${item.kind.unitLabel.orEmpty()}" }
                                ?: stringResource(R.string.clean_journal_numeric_classic),
                            style = CleanType.caption,
                            color = CleanColors.textTertiary,
                        )
                    } else {
                        YesNo(answers[item.canonical]) { onAnswer(item.canonical, it) }
                    }
                }
            }
        }
    }
}

/** A tri-state yes / no pair: tapping the chosen side again clears the answer. */
@Composable
private fun YesNo(value: Boolean?, onChange: (Boolean?) -> Unit) {
    val tick = rememberTick()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(true to R.string.clean_yes, false to R.string.clean_no).forEach { (option, label) ->
            val active = value == option
            val color = if (option) CleanColors.good else CleanColors.watch
            Box(
                Modifier
                    .clip(RoundedCornerShape(CleanDims.pillRadius))
                    .background(if (active) color.copy(alpha = 0.28f) else CleanColors.glassPressed)
                    .border(1.dp, if (active) color.copy(alpha = 0.8f) else CleanColors.glassRimDim, RoundedCornerShape(CleanDims.pillRadius))
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                        tick()
                        onChange(if (active) null else option)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(stringResource(label), style = CleanType.callout, color = if (active) CleanColors.textPrimary else CleanColors.textSecondary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogCard(
    items: List<JournalCatalogItem>,
    onAdd: (String, JournalGroup) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "catalogChevron")
    val present = remember(items) { items.associateBy { normJournalKey(it.canonical) } }
    GlassCard(modifier) {
        Row(
            Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.clean_journal_catalog_title), style = CleanType.headline, color = CleanColors.textPrimary)
                Text(stringResource(R.string.clean_journal_catalog_explain), style = CleanType.caption, color = CleanColors.textSecondary)
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = CleanColors.textSecondary, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                habitSections.forEach { section ->
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(section.titleRes).uppercase(), style = CleanType.label, color = CleanColors.textTertiary)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stringArrayResource(section.arrayRes).forEach { label ->
                            val existing = present[normJournalKey(label)]
                            HabitChip(label, added = existing != null) {
                                if (existing == null) onAdd(label, section.group) else onRemove(existing.canonical)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitChip(label: String, added: Boolean, onClick: () -> Unit) {
    val tick = rememberTick()
    Row(
        Modifier
            .clip(RoundedCornerShape(CleanDims.pillRadius))
            .background(if (added) CleanColors.good.copy(alpha = 0.22f) else CleanColors.glassPressed)
            .border(1.dp, if (added) CleanColors.good.copy(alpha = 0.7f) else CleanColors.glassRimDim, RoundedCornerShape(CleanDims.pillRadius))
            .clickable(role = Role.Checkbox) {
                tick()
                onClick()
            }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (added) Icons.Rounded.Check else Icons.Rounded.Add,
            null,
            tint = if (added) CleanColors.good else CleanColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = CleanType.callout, color = if (added) CleanColors.textPrimary else CleanColors.textSecondary)
    }
}

@Composable
private fun outcomeName(outcome: InsightsOutcome): String = stringResource(
    when (outcome) {
        InsightsOutcome.Recovery -> R.string.clean_recovery
        InsightsOutcome.Hrv -> R.string.clean_vital_hrv
        InsightsOutcome.Rhr -> R.string.clean_vital_rhr
        InsightsOutcome.Sleep -> R.string.clean_sleep_performance
    },
)

/** An effect's size in the outcome's own unit: recovery points, ms or bpm. */
private fun effectValue(outcome: InsightsOutcome, delta: Double, fmt: CleanFormat): String = when (outcome) {
    InsightsOutcome.Recovery, InsightsOutcome.Sleep -> fmt.string(R.string.clean_points, fmt.signed(delta))
    InsightsOutcome.Hrv -> fmt.string(R.string.clean_value_ms, fmt.signed(delta))
    InsightsOutcome.Rhr -> fmt.string(R.string.clean_value_bpm, fmt.signed(delta))
}

@Composable
private fun lagText(lag: Int): String = stringResource(
    when (lag) {
        0 -> R.string.clean_lag_same_day
        1 -> R.string.clean_lag_next_morning
        else -> R.string.clean_lag_two_days
    },
)

@Composable
private fun InsightsCard(
    snapshot: InsightsHubViewModel.Snapshot,
    hub: InsightsHubViewModel,
    items: List<JournalCatalogItem>,
    fmt: CleanFormat,
    modifier: Modifier = Modifier,
) {
    var outcomeIndex by rememberSaveable { mutableIntStateOf(0) }
    val outcome = insightOutcomes[outcomeIndex]
    val ranked = remember(snapshot, outcome) { hub.rankFor(snapshot, outcome) }
    GlassCard(modifier) {
        Text(stringResource(R.string.clean_journal_insights_title), style = CleanType.headline, color = CleanColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.clean_journal_insights_explain), style = CleanType.caption, color = CleanColors.textSecondary)
        Spacer(Modifier.height(12.dp))
        GlassSegmented(
            options = insightOutcomes.map { outcomeName(it) },
            selected = outcomeIndex,
            onSelect = { outcomeIndex = it },
            accent = CleanColors.auroraViolet,
        )
        Spacer(Modifier.height(14.dp))
        when {
            !snapshot.loaded -> Text(stringResource(R.string.clean_loading), style = CleanType.callout, color = CleanColors.textTertiary)
            ranked.isEmpty() -> Text(stringResource(R.string.clean_journal_insights_empty), style = CleanType.callout, color = CleanColors.textTertiary)
            else -> {
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.clean_journal_hurts), style = CleanType.micro, color = CleanColors.watch, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.clean_journal_helps), style = CleanType.micro, color = CleanColors.good)
                }
                Spacer(Modifier.height(6.dp))
                val shown = ranked.take(10)
                val goodness = shown.map { if (outcome.higherIsBetter) it.effect.delta else -it.effect.delta }
                val maxAbs = goodness.maxOf { abs(it) }.coerceAtLeast(1.0)
                shown.forEachIndexed { i, effect ->
                    EffectRow(effect, goodness[i], maxAbs, outcome, items, fmt)
                    if (i < shown.lastIndex) HorizontalDivider(color = CleanColors.hairline, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun EffectRow(
    effect: RankedEffect,
    goodness: Double,
    maxAbs: Double,
    outcome: InsightsOutcome,
    items: List<JournalCatalogItem>,
    fmt: CleanFormat,
) {
    val e = effect.effect
    val tone: Color = when {
        !e.significant -> CleanColors.textSecondary
        goodness > 0 -> CleanColors.good
        else -> CleanColors.watch
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                habitLabel(effect.behavior, items),
                style = CleanType.body,
                color = CleanColors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(effectValue(outcome, e.delta, fmt), style = CleanType.smallNumber, color = tone)
        }
        Spacer(Modifier.height(6.dp))
        // Scaled so the strongest row spans half the track; sign = helps (right) or hurts (left).
        DriverBar((goodness / maxAbs * 100).roundToInt(), 100)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.clean_journal_effect_detail, e.nWith, e.nWithout, lagText(effect.lag)) +
                " · " + stringResource(if (e.significant) R.string.clean_journal_clear else R.string.clean_journal_unsure),
            style = CleanType.caption,
            color = CleanColors.textTertiary,
        )
    }
}

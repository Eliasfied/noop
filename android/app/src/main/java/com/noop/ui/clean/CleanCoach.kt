package com.noop.ui.clean

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.noop.ai.AiKeyStore
import com.noop.ai.AiProvider
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.CoachBriefScheduler
import com.noop.ui.CoachBriefSettings
import com.noop.ui.CoachHandoff
import kotlinx.coroutines.launch
import java.time.LocalDate

// MARK: - AI coach in the clean design
//
// Two doors into the existing Coach (ai/AiCoach, CoachScreen), nothing new behind them: the morning brief
// the CoachBriefScheduler already generates and stores, shown as a card, and "ask the coach" questions
// handed over through CoachHandoff, the same channel the classic Today launcher uses. Each question
// carries the numbers the page shows, so the answer talks about what the wearer is looking at. Nothing
// is sent until a provider is configured and the wearer opens the chat; consent stays the Coach's own.

/** Whether the Coach can send: a saved key, or a connected custom server (the CoachViewModel rule). */
internal fun coachConfigured(ctx: Context): Boolean =
    if (AiKeyStore.readProvider(ctx) == AiProvider.CUSTOM) AiKeyStore.readCustomConnected(ctx) else AiKeyStore.hasKey(ctx)

/** Hands [question] to the Coach and opens it; the Coach sends it once, when it can. */
internal fun askCoach(question: String, openCoach: () -> Unit) {
    CoachHandoff.pendingPrompt = question
    openCoach()
}

/** A glass pill that asks the Coach [question]. Hidden when the Coach is switched off. */
@Composable
internal fun AskCoachButton(question: String, onAsk: (String) -> Unit, modifier: Modifier = Modifier) {
    if (!BottomBarStyleStore.coachEnabled) return
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(RoundedCornerShape(CleanDims.pillRadius), tint = CleanColors.auroraViolet)
            .clickable(interaction, indication = null, role = Role.Button) { onAsk(question) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = CleanColors.sleepBright, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.clean_coach_ask), style = CleanType.body, color = CleanColors.textPrimary)
            Text(question, style = CleanType.caption, color = CleanColors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Plain text of a Markdown brief: headings, emphasis and bullet markers removed, blank runs folded. */
internal fun briefPlainText(markdown: String): String =
    markdown.lines()
        .map { line ->
            line.trim()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^[-*•]\\s+"), "• ")
                .replace("**", "")
                .replace("__", "")
        }
        .fold(mutableListOf<String>()) { acc, l -> if (!(l.isEmpty() && acc.lastOrNull().isNullOrEmpty())) acc.add(l); acc }
        .joinToString("\n")
        .trim()

/**
 * The morning brief on Home: the stored brief with its date and a "generate now" action, a setup prompt
 * when no provider is configured, and three ready questions built from today's numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BriefingCard(today: CleanToday, fmt: CleanFormat, onAsk: (String) -> Unit, onOpenCoach: () -> Unit, modifier: Modifier = Modifier) {
    if (!BottomBarStyleStore.coachEnabled) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val settings = remember(version) { CoachBriefSettings.from(context) }
    val configured = remember(version) { coachConfigured(context) }
    val consent = remember(version) { AiKeyStore.readConsent(context) }
    var generating by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val brief = settings.storedBrief?.let(::briefPlainText)?.takeIf { it.isNotBlank() }
    val briefDay = settings.lastRunDayKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    GlassCard(modifier, tint = CleanColors.auroraViolet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = CleanColors.sleepBright, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.clean_coach_brief_title), style = CleanType.headline, color = CleanColors.textPrimary)
                Text(
                    when {
                        !configured -> stringResource(R.string.clean_coach_not_set_up)
                        briefDay != null -> stringResource(R.string.clean_as_of, fmt.relativeDay(briefDay))
                        else -> stringResource(R.string.clean_coach_no_brief)
                    },
                    style = CleanType.caption,
                    color = CleanColors.textTertiary,
                )
            }
            if (configured) {
                if (generating) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = CleanColors.sleepBright, strokeWidth = 2.dp)
                } else {
                    GlassIconButton(Icons.Rounded.Refresh, stringResource(R.string.clean_coach_generate), onClick = {
                        generating = true
                        failed = false
                        scope.launch {
                            val text = runCatching { CoachBriefScheduler.generateNow(context) }.getOrNull()
                            failed = text == null
                            generating = false
                            version++
                        }
                    }, modifier = Modifier.size(36.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            !configured -> {
                Text(stringResource(R.string.clean_coach_setup_body), style = CleanType.callout, color = CleanColors.textSecondary)
                Spacer(Modifier.height(10.dp))
                AskCoachLink(stringResource(R.string.clean_coach_setup_action), onOpenCoach)
            }
            brief != null -> {
                Text(
                    brief,
                    style = CleanType.callout,
                    color = CleanColors.textSecondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
                )
            }
            else -> Text(stringResource(R.string.clean_coach_no_brief_body), style = CleanType.callout, color = CleanColors.textSecondary)
        }
        if (failed) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.clean_coach_failed), style = CleanType.caption, color = CleanColors.watch)
        }
        if (configured && !consent) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.clean_coach_no_consent), style = CleanType.caption, color = CleanColors.textTertiary)
        }
        if (configured) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestedQuestions(today, fmt).forEach { q -> QuestionChip(q, onAsk) }
            }
        }
    }
}

@Composable
private fun AskCoachLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = CleanType.body,
        color = CleanColors.sleepBright,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
    )
}

@Composable
private fun QuestionChip(question: String, onAsk: (String) -> Unit) {
    Text(
        question,
        style = CleanType.callout,
        color = CleanColors.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(CleanDims.pillRadius))
            .glass(RoundedCornerShape(CleanDims.pillRadius))
            .clickable(role = Role.Button) { onAsk(question) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Three ready questions with today's numbers in them. */
@Composable
private fun suggestedQuestions(today: CleanToday, fmt: CleanFormat): List<String> = buildList {
    val rec = today.recoveryValue
    if (rec != null) add(recoveryQuestion(today, fmt)) else add(stringResource(R.string.clean_coach_q_start))
    today.strain21?.let { add(strainQuestion(it, today.recoveryValue, fmt)) }
    add(stringResource(R.string.clean_coach_q_sleep_generic))
}

/** The recovery question, with the score and the two biggest inputs. */
@Composable
internal fun recoveryQuestion(today: CleanToday, fmt: CleanFormat): String {
    val rec = today.recoveryValue ?: return stringResource(R.string.clean_coach_q_start)
    val row = today.vitalsRow
    val hrv = row?.avgHrv?.let { fmt.string(R.string.clean_value_ms, fmt.int(it)) } ?: "–"
    val rhr = row?.restingHr?.let { fmt.string(R.string.clean_value_bpm, it.toString()) } ?: "–"
    return stringResource(R.string.clean_coach_q_recovery, fmt.percent(shownPercent(rec).toDouble()), hrv, rhr)
}

@Composable
internal fun strainQuestion(strain21: Double, recovery: Double?, fmt: CleanFormat): String {
    val target = strainTarget(recovery)
    return if (target != null) {
        stringResource(R.string.clean_coach_q_strain_target, fmt.decimal(strain21), target.low, target.high)
    } else {
        stringResource(R.string.clean_coach_q_strain, fmt.decimal(strain21))
    }
}

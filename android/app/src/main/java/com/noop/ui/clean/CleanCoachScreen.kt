package com.noop.ui.clean

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noop.R
import com.noop.ai.ChatMsg
import com.noop.ui.AppViewModel
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.CoachHandoff
import com.noop.ui.CoachMarkdown
import com.noop.ui.CoachViewModel
import com.noop.ui.rememberPoseStill

// MARK: - Clean Coach
//
// The coach conversation in the clean design. All of the behaviour is the classic CoachViewModel's (the
// same send path that re-reads consent and the system prompt on every send, the same persisted
// transcript, the same scheduled-brief hand-over); this file only draws it: glass bubbles, ready
// questions in the wearer's language, and a composer that sits above the keyboard and the tab bar.
// Provider setup and the coach settings stay on the classic screens; coming back from them rebuilds the
// view model so a changed provider or model is never sent with a stale copy.

/** The composer draft, shared with the classic Coach so a half-typed question survives either way. */
private fun draftPrefs(ctx: Context) = ctx.getSharedPreferences("noop_coach_draft", Context.MODE_PRIVATE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CleanCoachScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val fmt = rememberCleanFormat()
    val today = rememberCleanToday(appVm)
    // Rebuilt after a trip to the classic setup or settings, so provider and model are read fresh.
    var generation by rememberSaveable { mutableIntStateOf(0) }
    var awayForSettings by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awayForSettings) {
                awayForSettings = false
                generation++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val vm: CoachViewModel = viewModel(key = "cleanCoach$generation")
    val configured = remember(generation) { coachConfigured(context) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val consent by vm.consent.collectAsStateWithLifecycle()
    val keyRejected by vm.keyRejected.collectAsStateWithLifecycle()
    val provider by vm.provider.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf(draftPrefs(context).getString("draft", "") ?: "") }
    var confirmClear by remember { mutableStateOf(false) }
    val tick = rememberTick()

    fun openSettings() { awayForSettings = true; onOpenSettings() }
    fun openSetup() { awayForSettings = true; onOpenSetup() }
    fun send(text: String) {
        if (text.isBlank() || sending) return
        tick()
        vm.send(context, text)
        input = ""
        draftPrefs(context).edit().remove("draft").apply()
    }

    // Restore the persisted transcript, retire yesterday's, surface a scheduled brief: the classic order.
    LaunchedEffect(vm) {
        vm.loadPersistedMessagesIfNeeded()
        vm.retireStaleConversationIfNeeded()
        vm.consumeScheduledBriefIfAny(context)
    }
    // A question handed over by "ask the coach": sent once, and only when the coach can send.
    LaunchedEffect(vm, configured) {
        val handed = CoachHandoff.consume()
        if (handed != null && configured) vm.send(context, handed)
    }
    var wasSending by remember { mutableStateOf(false) }
    LaunchedEffect(sending) {
        if (wasSending && !sending) tick()
        wasSending = sending
    }

    val listState = rememberLazyListState()
    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastLength, sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size + 1)
    }

    Box(Modifier.fillMaxSize()) {
        CleanBackdrop(CleanColors.auroraViolet)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            CoachTopBar(
                subtitle = if (configured) "${provider.displayName} · $model" else stringResource(R.string.clean_coach_not_set_up),
                onBack = onBack,
                onSettings = if (configured) ::openSettings else null,
                onClear = if (configured && messages.isNotEmpty()) ({ confirmClear = true }) else null,
            )
            if (!configured) {
                Column(Modifier.padding(horizontal = CleanDims.gutter).weight(1f)) {
                    SetupCard(::openSetup)
                }
                return@Column
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = CleanDims.gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    if (!consent) ConsentBanner(::openSettings)
                    if (messages.isEmpty()) EmptyState(today, fmt, ::send)
                }
                items(messages, key = { it.id }) { msg ->
                    if (msg.role == "user") UserBubble(msg) else AssistantBubble(msg, streaming = sending && msg == messages.last(), vm = vm)
                }
                item {
                    val last = messages.lastOrNull()
                    if (!sending && last?.role == "assistant") FollowUps(::send)
                    val e = error
                    if (e != null) {
                        Text(e, style = CleanType.callout, color = CleanColors.bad, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (keyRejected) {
                        Text(
                            stringResource(R.string.clean_coach_key_rejected),
                            style = CleanType.callout,
                            color = CleanColors.sleepBright,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClick = ::openSetup),
                        )
                    }
                }
            }
            val imeOpen = WindowInsets.isImeVisible
            Composer(
                input = input,
                sending = sending,
                onInput = {
                    input = it
                    draftPrefs(context).edit().putString("draft", it).apply()
                    if (error != null) vm.clearError()
                },
                onSend = { send(input) },
                modifier = Modifier
                    .imePadding()
                    .padding(horizontal = CleanDims.gutter)
                    .padding(top = 6.dp, bottom = if (imeOpen) 8.dp else BottomBarStyleStore.barHeightForContent() + 8.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clean_coach_clear_title)) },
            text = { Text(stringResource(R.string.clean_coach_clear_body)) },
            confirmButton = {
                TextButton(onClick = { vm.clearConversation(); confirmClear = false }) {
                    Text(stringResource(R.string.clean_coach_clear_confirm), color = CleanColors.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.clean_cancel)) }
            },
        )
    }
}

@Composable
private fun CoachTopBar(subtitle: String, onBack: () -> Unit, onSettings: (() -> Unit)?, onClear: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = CleanDims.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.clean_back), onBack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.clean_coach_title), style = CleanType.title, color = CleanColors.textPrimary)
            Text(subtitle, style = CleanType.caption, color = CleanColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onClear != null) {
            GlassIconButton(Icons.Rounded.DeleteSweep, stringResource(R.string.clean_coach_clear_title), onClear)
            Spacer(Modifier.width(8.dp))
        }
        if (onSettings != null) {
            GlassIconButton(Icons.Rounded.Tune, stringResource(R.string.clean_coach_settings), onSettings)
        }
    }
}

@Composable
private fun SetupCard(onSetup: () -> Unit) {
    GlassCard(tint = CleanColors.auroraViolet) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = CleanColors.sleepBright, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.clean_coach_setup_title), style = CleanType.title, color = CleanColors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.clean_coach_setup_body), style = CleanType.callout, color = CleanColors.textSecondary)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.clean_coach_setup_privacy), style = CleanType.caption, color = CleanColors.textTertiary)
        Spacer(Modifier.height(14.dp))
        PrimaryPill(stringResource(R.string.clean_coach_setup_action), onSetup)
    }
}

@Composable
private fun PrimaryPill(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(CleanDims.pillRadius))
            .background(Brush.horizontalGradient(listOf(CleanColors.sleepDeep, CleanColors.auroraViolet)))
            .border(1.dp, CleanColors.glassRimBright.copy(alpha = 0.4f), RoundedCornerShape(CleanDims.pillRadius))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CleanType.headline, color = CleanColors.textPrimary)
    }
}

@Composable
private fun ConsentBanner(onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .glass(RoundedCornerShape(18.dp), tint = CleanColors.watch)
            .clickable(role = Role.Button, onClick = onOpenSettings)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = CleanColors.watch, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.clean_coach_consent_off), style = CleanType.callout, color = CleanColors.textSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(today: CleanToday, fmt: CleanFormat, onAsk: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(stringResource(R.string.clean_coach_empty_title), style = CleanType.largeTitle, color = CleanColors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.clean_coach_empty_body), style = CleanType.callout, color = CleanColors.textSecondary)
        Spacer(Modifier.height(16.dp))
        val questions = buildList {
            add(recoveryQuestion(today, fmt))
            today.strain21?.let { add(strainQuestion(it, today.recoveryValue, fmt)) }
            add(stringResource(R.string.clean_coach_q_sleep_generic))
            add(stringResource(R.string.clean_coach_q_hrv))
            add(stringResource(R.string.clean_coach_q_week))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            questions.forEach { q -> QuestionRow(q, onAsk) }
        }
    }
}

@Composable
private fun QuestionRow(question: String, onAsk: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { onAsk(question) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = CleanColors.sleepBright, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(question, style = CleanType.callout, color = CleanColors.textPrimary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowUps(onAsk: (String) -> Unit) {
    val followUps = listOf(
        stringResource(R.string.clean_coach_follow_today),
        stringResource(R.string.clean_coach_follow_simpler),
        stringResource(R.string.clean_coach_follow_tomorrow),
    )
    FlowRow(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        followUps.forEach { q ->
            Text(
                q,
                style = CleanType.callout,
                color = CleanColors.textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(CleanDims.pillRadius))
                    .glass(RoundedCornerShape(CleanDims.pillRadius))
                    .clickable(role = Role.Button) { onAsk(q) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun UserBubble(msg: ChatMsg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            msg.text,
            style = CleanType.body,
            color = CleanColors.textPrimary,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                .background(Brush.linearGradient(listOf(CleanColors.sleepDeep.copy(alpha = 0.85f), CleanColors.auroraViolet.copy(alpha = 0.85f))))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AssistantBubble(msg: ChatMsg, streaming: Boolean, vm: CoachViewModel) {
    val clipboard = LocalClipboardManager.current
    var saved by remember(msg.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .glass(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (msg.text.isEmpty()) ThinkingDots() else CoachMarkdown(msg.text, color = CleanColors.textPrimary)
        }
        if (!streaming && msg.text.isNotEmpty()) {
            Row(Modifier.padding(start = 6.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SmallAction(Icons.Rounded.ContentCopy, stringResource(R.string.clean_coach_copy)) {
                    clipboard.setText(AnnotatedString(msg.text))
                }
                SmallAction(
                    Icons.Rounded.EditNote,
                    stringResource(if (saved) R.string.clean_coach_saved else R.string.clean_coach_save_journal),
                ) {
                    if (!saved) {
                        vm.saveAdviceToJournal(msg.text)
                        saved = true
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = CleanColors.textTertiary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = CleanType.caption, color = CleanColors.textTertiary)
    }
}

/** Three dots rising in turn while the reply is on its way; still when motion is quieted. */
@Composable
private fun ThinkingDots() {
    val still = rememberPoseStill()
    val phase: State<Float> = if (still) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "thinking").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
            label = "thinkingPhase",
        )
    }
    Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        val t = ((phase.value - i * 0.18f) % 1f + 1f) % 1f
                        val lift = if (t < 0.3f) kotlin.math.sin(t / 0.3f * Math.PI).toFloat() else 0f
                        translationY = -lift * 6.dp.toPx()
                        alpha = 0.45f + 0.55f * lift
                    }
                    .clip(CircleShape)
                    .background(CleanColors.sleepBright),
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    sending: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = input.isNotBlank() && !sending
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (input.isEmpty()) {
                Text(stringResource(R.string.clean_coach_placeholder), style = CleanType.body, color = CleanColors.textTertiary)
            }
            BasicTextField(
                value = input,
                onValueChange = onInput,
                textStyle = CleanType.body.copy(color = CleanColors.textPrimary),
                cursorBrush = SolidColor(CleanColors.sleepBright),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        val interaction = remember { MutableInteractionSource() }
        val scale by rememberPressScale(interaction)
        Box(
            Modifier
                .size(42.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(
                    if (canSend) Brush.linearGradient(listOf(CleanColors.sleepDeep, CleanColors.sleepBright))
                    else SolidColor(CleanColors.glassPressed),
                )
                .clickable(interaction, indication = null, role = Role.Button, enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.clean_coach_send),
                tint = if (canSend) CleanColors.textPrimary else CleanColors.textTertiary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

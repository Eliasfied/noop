package com.noop.ui.clean

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.noop.R
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.Palette
import com.noop.ui.rememberPoseStill
import com.noop.ui.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// MARK: - Glass primitives
//
// Compose 1.6 has no backdrop blur, so the glass is built the way frosted glass reads to the eye: a slow,
// soft aurora behind everything, translucent panels whose fill is lighter at the top, and a rim that is
// bright on the light-facing edge and fades round the sides. Over a blurry light source a translucent
// panel looks frosted without blurring anything, and it costs one canvas instead of a render pass.

/**
 * The aurora canvas every clean screen sits on: a deep gradient plus three soft light sources, one of them
 * in the screen's [accent]. The lights drift on a 30-second loop; battery saver, "Remove animations" and
 * the in-app quiet-motion setting pose it still ([rememberPoseStill]).
 */
@Composable
internal fun CleanBackdrop(accent: Color, modifier: Modifier = Modifier) {
    val still = rememberPoseStill()
    val phase: State<Float> = if (still) {
        remember { mutableFloatStateOf(0.18f) }
    } else {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart),
            label = "auroraPhase",
        )
    }
    val tint by animateColorAsState(accent, tween(900, easing = CleanMotion.standard), label = "auroraTint")
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val p = phase.value * 2f * PI.toFloat()
        drawRect(Brush.verticalGradient(listOf(CleanColors.canvasTop, CleanColors.canvasBottom)))
        fun light(center: Offset, radius: Float, color: Color) = drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center,
        )
        light(Offset(w * (0.20f + 0.10f * sin(p)), h * (0.08f + 0.04f * cos(p))), w * 1.05f, tint.copy(alpha = 0.26f))
        light(Offset(w * (0.95f + 0.07f * cos(p)), h * (0.36f + 0.06f * sin(p))), w * 0.85f, CleanColors.auroraViolet.copy(alpha = 0.55f))
        light(Offset(w * (0.25f + 0.12f * sin(p + 1.7f)), h * (0.80f + 0.05f * cos(p))), w * 0.95f, CleanColors.auroraTeal.copy(alpha = 0.30f))
        light(Offset(w * (0.70f + 0.08f * cos(p + 0.9f)), h * (0.62f + 0.05f * sin(p * 2f))), w * 0.55f, tint.copy(alpha = 0.10f))
    }
}

/** The glass fill + rim for any [shape]. [tint] washes the panel faintly in a domain colour. */
internal fun Modifier.glass(
    shape: Shape = RoundedCornerShape(CleanDims.cardRadius),
    tint: Color? = null,
): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(CleanColors.glassFillTop, CleanColors.glassFillBottom)))
    .then(
        if (tint != null) {
            Modifier.background(
                Brush.linearGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.03f), Color.Transparent)),
            )
        } else {
            Modifier
        },
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(
                CleanColors.glassRimBright,
                CleanColors.glassRimDim,
                CleanColors.glassRimDim,
                CleanColors.glassRimBright.copy(alpha = 0.14f),
            ),
            start = Offset.Zero,
            end = Offset.Infinite,
        ),
        shape = shape,
    )

/** Scale-down press feedback for anything tappable on glass. */
@Composable
internal fun rememberPressScale(interaction: MutableInteractionSource): State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) 0.97f else 1f, CleanMotion.pressSpring, label = "press")
}

/** A light haptic tick, the one used when a chart scrub crosses a day or a tab changes. */
@Composable
internal fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
}

/** A glass card. With [onClick] it presses in and announces itself as a button. */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    padding: Dp = CleanDims.cardPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(tint = tint)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(padding),
        content = content,
    )
}

/**
 * Fade-and-rise entrance for the [index]-th block of a screen, staggered so a page assembles top to
 * bottom. A one-shot that settles, so it follows "Remove animations" only, like the classic reveals.
 */
internal fun Modifier.cleanReveal(index: Int): Modifier = composed {
    val reduce = rememberReduceMotion()
    var shown by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduce) {
            tween(0)
        } else {
            tween(CleanMotion.revealMillis, delayMillis = index * CleanMotion.staggerMillis, easing = CleanMotion.emphasized)
        },
        label = "reveal",
    )
    val rise = with(LocalDensity.current) { 26.dp.toPx() }
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * rise
    }
}

/** An all-caps section label with an optional trailing hint. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = CleanType.label,
            color = CleanColors.textTertiary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) Text(trailing, style = CleanType.caption, color = CleanColors.textTertiary)
    }
}

/** A round glass button, used for back navigation. */
@Composable
internal fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)
    Box(
        modifier = modifier
            .size(CleanDims.backButton)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = CleanColors.textPrimary, modifier = Modifier.size(CleanDims.icon))
    }
}

/** A small glass capsule with a coloured dot, for states like "Calibrating" or a data source. */
@Composable
internal fun GlassChip(text: String, dot: Color?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(CleanDims.pillRadius))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dot != null) Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(text, style = CleanType.caption, color = CleanColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A segmented control whose selection is a sliding glass lens. Used for the 7 / 30 / 90 day ranges.
 */
@Composable
internal fun GlassSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val tick = rememberTick()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .glass(RoundedCornerShape(CleanDims.pillRadius))
            .padding(4.dp),
    ) {
        val slot = maxWidth / options.size
        val lensX by animateDpAsState(slot * selected, CleanMotion.lens(), label = "segmentLens")
        Box(
            Modifier
                .offset(x = lensX)
                .width(slot)
                .fillMaxHeight()
                .clip(RoundedCornerShape(CleanDims.pillRadius))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.16f))))
                .border(1.dp, CleanColors.glassRimBright.copy(alpha = 0.35f), RoundedCornerShape(CleanDims.pillRadius)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                val color by animateColorAsState(
                    if (active) CleanColors.textPrimary else CleanColors.textSecondary,
                    label = "segmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(CleanDims.pillRadius))
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            if (i != selected) {
                                tick()
                                onSelect(i)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = CleanType.callout, color = color, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The page frame of every clean screen: aurora backdrop, a large title that scrolls away, and a compact
 * glass header that fades in once it has. Content clears the floating tab bar using the height the shell
 * measured ([BottomBarStyleStore.barHeightForContent]), so it never ends up under the glass.
 */
@Composable
internal fun CleanScreen(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val collapseAt = with(density) { 64.dp.toPx() }
    val collapsed by remember { derivedStateOf { scroll.value > collapseAt } }
    val headerAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(220), label = "compactHeader")
    LightSystemBarIcons()
    Box(modifier.fillMaxSize()) {
        CleanBackdrop(accent)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(horizontal = CleanDims.gutter)
                .padding(bottom = BottomBarStyleStore.barHeightForContent() + 28.dp),
            verticalArrangement = Arrangement.spacedBy(CleanDims.sectionGap),
        ) {
            Spacer(Modifier.height(6.dp))
            if (onBack != null) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.clean_back), onBack)
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().cleanReveal(0)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = CleanType.largeTitle,
                        color = CleanColors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Text(subtitle, style = CleanType.callout, color = CleanColors.textSecondary)
                    }
                }
                trailing()
            }
            content()
        }
        if (headerAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = headerAlpha }
                    .background(Brush.verticalGradient(listOf(CleanColors.glassBarDeep, CleanColors.glassBar)))
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = CleanDims.gutter),
                contentAlignment = Alignment.Center,
            ) {
                if (onBack != null) {
                    GlassIconButton(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        stringResource(R.string.clean_back),
                        onBack,
                        modifier = Modifier.align(Alignment.CenterStart).size(34.dp),
                    )
                }
                Text(title, style = CleanType.headline, color = CleanColors.textPrimary, maxLines = 1)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = headerAlpha }
                    .statusBarsPadding()
                    .padding(top = 52.dp)
                    .height(1.dp)
                    .background(CleanColors.hairline),
            )
        }
    }
}

/** How many clean screens are composed right now; two overlap while one crossfades into the next. */
private object CleanSystemBars {
    var screens = 0
}

/**
 * Light status- and navigation-bar icons while a clean screen is up, since the clean canvas is always dark.
 * The classic theme sets them from the appearance setting; the last clean screen to leave hands that back.
 */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        CleanSystemBars.screens++
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            CleanSystemBars.screens--
            if (CleanSystemBars.screens == 0) {
                controller?.isAppearanceLightStatusBars = Palette.isLight
                controller?.isAppearanceLightNavigationBars = Palette.isLight
            }
        }
    }
}

/** A dot that breathes while something is live (connected, syncing); still when motion is quieted. */
@Composable
internal fun PulseDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val still = rememberPoseStill()
    val halo: State<Float> = if (still || !pulsing) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = CleanMotion.standard), RepeatMode.Restart),
            label = "pulseHalo",
        )
    }
    Canvas(modifier.size(14.dp)) {
        val r = size.minDimension / 2f
        val t = halo.value
        if (t > 0f) drawCircle(color.copy(alpha = 0.45f * (1f - t)), radius = r * (0.45f + 0.55f * t))
        drawCircle(color, radius = r * 0.36f)
    }
}

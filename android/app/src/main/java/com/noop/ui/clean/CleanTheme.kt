package com.noop.ui.clean

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MARK: - Clean design tokens
//
// The optional "clean" design (default off, switched in More) is a calm, dark, glass-on-aurora skin over
// the SAME data the classic screens read. Every colour, type style, size and motion curve the package uses
// is defined once here, so no screen in com.noop.ui.clean spells a literal of its own. The classic
// [com.noop.ui.Palette] is deliberately not reused for the domain colours: the clean design is always dark,
// while the classic palette follows the light/dark appearance setting.

/** Colour tokens of the clean design. Always dark, independent of the classic appearance setting. */
internal object CleanColors {
    // Canvas: a deep blue-black, slightly lighter at the top so the aurora has something to sit on.
    val canvasTop = Color(0xFF0C1119)
    val canvasBottom = Color(0xFF040609)

    // Glass: translucent white over the aurora, with a brighter rim on the light-facing edge.
    val glassFillTop = Color(0x17FFFFFF)
    val glassFillBottom = Color(0x08FFFFFF)
    val glassRimBright = Color(0x4DFFFFFF)
    val glassRimDim = Color(0x0FFFFFFF)
    val glassBar = Color(0xC7141A23)
    val glassBarDeep = Color(0xE00B0F15)
    val glassLens = Color(0x24FFFFFF)
    val glassPressed = Color(0x14FFFFFF)
    val scrim = Color(0xB3040609)
    val hairline = Color(0x14FFFFFF)
    val track = Color(0x1AFFFFFF)
    val gridLine = Color(0x12FFFFFF)
    val shadow = Color(0x99000000)

    // Text.
    val textPrimary = Color(0xFFF5F7FA)
    val textSecondary = Color(0xFFA9B2C0)
    val textTertiary = Color(0xFF6B7584)

    // Recovery zones (low / mid / high), each with a deeper tone for gradients.
    val recoveryHigh = Color(0xFF2EE58A)
    val recoveryHighDeep = Color(0xFF0FA968)
    val recoveryMid = Color(0xFFFFD54A)
    val recoveryMidDeep = Color(0xFFE0A419)
    val recoveryLow = Color(0xFFFF5A5F)
    val recoveryLowDeep = Color(0xFFC92F3E)

    // Strain (load) domain.
    val strain = Color(0xFF3D9BFF)
    val strainDeep = Color(0xFF1F63D6)
    val strainBright = Color(0xFF8CC8FF)

    // Sleep domain.
    val sleep = Color(0xFF8C9EFF)
    val sleepDeep = Color(0xFF5B5BD6)
    val sleepBright = Color(0xFFC3CCFF)

    // Trends / neutral accent.
    val trend = Color(0xFF4FD8D0)
    val trendDeep = Color(0xFF1E9C9E)

    // Sleep stages, light to deep.
    val stageAwake = Color(0xFFE6EAF2)
    val stageRem = Color(0xFFA78BFF)
    val stageLight = Color(0xFF59A8FF)
    val stageDeep = Color(0xFF3949D6)

    // Verdict tones shared by vitals and drivers.
    val good = Color(0xFF2EE58A)
    val watch = Color(0xFFFFB547)
    val bad = Color(0xFFFF6B6B)
    val neutral = Color(0xFFA9B2C0)

    // Aurora light sources behind the glass. Low alpha on purpose: they tint, they do not compete.
    val auroraNeutral = Color(0xFF2B4C7E)
    val auroraTeal = Color(0xFF1E6F78)
    val auroraViolet = Color(0xFF3B2F7A)
}

/** Type scale of the clean design: condensed numerals for scores, the system sans for words. */
internal object CleanType {
    private val numerals = FontFamily(
        Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
        Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Medium),
        Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
    )
    private val sans = FontFamily.SansSerif

    val heroNumber = TextStyle(
        fontFamily = numerals, fontWeight = FontWeight.Bold, fontSize = 64.sp,
        letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum",
    )
    val dialNumber = TextStyle(
        fontFamily = numerals, fontWeight = FontWeight.Bold, fontSize = 30.sp,
        letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum",
    )
    val bigNumber = TextStyle(
        fontFamily = numerals, fontWeight = FontWeight.Bold, fontSize = 34.sp,
        letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum",
    )
    val number = TextStyle(
        fontFamily = numerals, fontWeight = FontWeight.Bold, fontSize = 22.sp, fontFeatureSettings = "tnum",
    )
    val smallNumber = TextStyle(
        fontFamily = numerals, fontWeight = FontWeight.Medium, fontSize = 16.sp, fontFeatureSettings = "tnum",
    )
    val unit = TextStyle(fontFamily = numerals, fontWeight = FontWeight.Medium, fontSize = 13.sp)

    val largeTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
    val headline = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val callout = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp)
    val caption = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val micro = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 10.sp)
    val label = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
}

/** Spacing, radii and component sizes of the clean design. */
internal object CleanDims {
    val gutter = 18.dp
    val cardRadius = 28.dp
    val cardPadding = 20.dp
    val sectionGap = 14.dp
    val rowGap = 12.dp
    val pillRadius = 50.dp
    val tabBarHeight = 64.dp
    val tabBarMaxWidth = 440.dp
    val backButton = 40.dp
    val homeDial = 104.dp
    val homeDialStroke = 9.dp
    val heroDial = 220.dp
    val heroDialStroke = 16.dp
    val chartHeight = 170.dp
    val hypnogramHeight = 176.dp
    val rangeBarHeight = 6.dp
    val stageBarHeight = 14.dp
    val icon = 20.dp
    val iconSmall = 16.dp
}

/** Motion curves and specs of the clean design. */
internal object CleanMotion {
    /** The emphasised decelerate curve used for every one-shot reveal (dials, bars, cards). */
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** A softer curve for colour and opacity changes. */
    val standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    const val dialMillis = 1300
    const val revealMillis = 520
    const val staggerMillis = 70
    const val chartMillis = 900

    /** The liquid lens under the selected tab or segment: a little overshoot, settles fast. */
    fun <T> lens(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 420f)

    /** Press feedback on glass surfaces. */
    val pressSpring = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
}

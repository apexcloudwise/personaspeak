package biz.pixelperfectstudios.personaspeak.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// DESIGN.md ships two brand families — Outfit for headlines, Inter for UI/body
// — plus System Monospace for technical data. The font FILES are not vendored
// yet (follow-up, not blocking this foundation), so Outfit/Inter fall back to
// the platform default sans-serif. Once .ttf/.otf resources land under
// app/src/main/res/font/, swap each `FontFamily.Default` for
// FontFamily(Font(R.font.outfit)) / Font(R.font.inter).

private val Outfit = FontFamily.Default
private val Inter = FontFamily.Default
private val Mono = FontFamily.Monospace

val PersonaSpeakTypography = Typography(
    // headline-lg
    headlineLarge = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    // headline-md
    headlineMedium = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    // body-md — primary body text across onboarding and settings
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    // label-md — key captions, list rows
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // label-sm — secondary descriptions (persona subtitles etc.)
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

// Material3 Typography has no monospace slot, so the `technical` token
// (API keys, logs, developer-facing data) lives as a standalone TextStyle.
val TechnicalTextStyle = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)

package com.charles.crowdtransit.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// DEVIATION FROM SPEC: the spec calls for Plus Jakarta Sans bundled via the Google Fonts
// downloadable-fonts API. That API requires a signed Google Fonts provider certificate lookup at
// runtime (via the Google Play Services cert fingerprint) which is fragile to configure in this
// environment without a real signing/release setup, and there's no reliable way to vendor the
// variable font files as raw assets here. We fall back to the platform sans-serif family
// (Roboto/Noto on most devices) at the same sizes/weights the spec specifies for Plus Jakarta
// Sans, which is a close geometric-sans match and keeps the build reliable. Swapping in the real
// font later only requires changing `SunnyTransitFontFamily` below.
val SunnyTransitFontFamily = FontFamily.SansSerif

val CrowdTransitTypography = Typography(
    // Display — page titles: 32sp / 800
    displayLarge = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    // Headline — section / card titles: 22sp / 700
    headlineLarge = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    // Title — stop names: 17sp / 700
    titleLarge = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // Body: 15sp / 400
    bodyLarge = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // Label / caption: 13sp / 600, ink-secondary
    labelLarge = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SunnyTransitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

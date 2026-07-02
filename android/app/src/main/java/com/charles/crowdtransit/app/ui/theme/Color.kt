package com.charles.crowdtransit.app.ui.theme

import androidx.compose.ui.graphics.Color

// "Sunny Transit" design system — light theme (see docs/superpowers/specs/2026-07-02-visual-redesign-design.md)

// Core surfaces
val AppBackground = Color(0xFFFAFAF7) // warm off-white page background
val Surface = Color(0xFFFFFFFF) // cards, sheets, nav bars
val SurfaceElevated = Color(0xFFF1F1EC) // surface-sunken: inputs, wells, secondary chips
val SurfaceCard = Color(0xFFFFFFFF)
val SurfaceDark = AppBackground // kept for source-compat with existing screens using containerColor = SurfaceDark
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF6F6F1)
val SurfaceContainerHighest = Color(0xFFE9E9E2)

// Ink (text) tokens
val OnSurface = Color(0xFF1A1D1B) // ink
val OnSurfaceSecondary = Color(0xFF5A605C) // ink-secondary
val OnSurfaceFaint = Color(0xFF9AA19C) // ink-faint

val Outline = Color(0xFFE4E4DE)
val OutlineVariant = Color(0xFFE4E4DE)

// Brand / primary
val Primary = Color(0xFF00A862) // hero green
val PrimaryDark = Color(0xFF007A47)
val PrimaryLight = Color(0xFFE0F5EB) // primary-tint
val OnPrimary = Color(0xFFFFFFFF)

// Secondary kept as an alias of primary-tint so existing screens (SurfaceElevated-adjacent
// buttons using "Secondary"/"OnSecondary") render as soft green pills rather than the old blue.
val Secondary = PrimaryLight
val SecondaryLight = PrimaryLight
val SecondaryDark = PrimaryDark
val OnSecondary = PrimaryDark

val Accent = Color(0xFFFFB000) // amber — ratings, highlights
val Success = Primary
val Warning = Accent
val Error = Color(0xFFDE3730)
val ErrorContainer = Color(0xFFFBE4E2)

val RatingGold = Accent
val RatingEmpty = Outline

// Transit-mode palette (vivid on light background)
val TransitBus = Color(0xFF00A862)
val TransitTrain = Color(0xFF2563EB)
val TransitSubway = Color(0xFF8B2FC9)
val TransitFerry = Color(0xFF0891B2)
val TransitTram = Color(0xFFEA7317)

// Gamification level colors (ring around avatar)
val LevelPedestrian = Color(0xFF9AA19C)
val LevelCommuter = Color(0xFF2563EB)
val LevelRegular = Color(0xFF00A862)
val LevelConductor = Color(0xFFEA7317)
val LevelTransitLegend = Color(0xFFFFB000)

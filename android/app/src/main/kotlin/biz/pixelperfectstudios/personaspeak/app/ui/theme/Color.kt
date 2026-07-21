package biz.pixelperfectstudios.personaspeak.app.ui.theme

import androidx.compose.ui.graphics.Color

// Source of truth: stitch_personaspeak_ui_mockups/personaspeak_design_system/DESIGN.md.
// Dark-first palette; the same tokens feed the color scheme in Theme.kt.

// Surfaces
val Background = Color(0xFF10141A)
val OnBackground = Color(0xFFDFE2EB)
val Surface = Color(0xFF10141A)
val OnSurface = Color(0xFFDFE2EB)
val SurfaceDim = Color(0xFF10141A)
val SurfaceBright = Color(0xFF353940)
val SurfaceContainerLowest = Color(0xFF0A0E14)
val SurfaceContainerLow = Color(0xFF181C22)
val SurfaceContainer = Color(0xFF1C2026)
val SurfaceContainerHigh = Color(0xFF262A31)
val SurfaceContainerHighest = Color(0xFF31353C)
val SurfaceVariant = Color(0xFF31353C)
val OnSurfaceVariant = Color(0xFFBBCAC6)
val InverseSurface = Color(0xFFDFE2EB)
val InverseOnSurface = Color(0xFF2D3137)

// Lines
val Outline = Color(0xFF859490)
val OutlineVariant = Color(0xFF3C4947)

// Primary (teal)
val SurfaceTint = Color(0xFF4FDBC8)
val Primary = Color(0xFF4FDBC8)
val OnPrimary = Color(0xFF003731)
val PrimaryContainer = Color(0xFF14B8A6)
val OnPrimaryContainer = Color(0xFF00423B)
val InversePrimary = Color(0xFF006B5F)

// Secondary (cyan)
val Secondary = Color(0xFF4CD7F6)
val OnSecondary = Color(0xFF003640)
val SecondaryContainer = Color(0xFF03B5D3)
val OnSecondaryContainer = Color(0xFF00424E)

// Tertiary (amber)
val Tertiary = Color(0xFFFFB95F)
val OnTertiary = Color(0xFF472A00)
val TertiaryContainer = Color(0xFFE49200)
val OnTertiaryContainer = Color(0xFF543300)

// Error
val Error = Color(0xFFFFB4AB)
val OnError = Color(0xFF690005)
val ErrorContainer = Color(0xFF93000A)
val OnErrorContainer = Color(0xFFFFDAD6)

// Fixed tones (tonal surface variants). Exposed as tokens for direct use by
// screens; not wired into darkColorScheme() because the Compose BOM pinned here
// (2025.05.01 / material3 1.3.x) predates the fixed ColorScheme params.
val PrimaryFixed = Color(0xFF71F8E4)
val PrimaryFixedDim = Color(0xFF4FDBC8)
val OnPrimaryFixed = Color(0xFF00201C)
val OnPrimaryFixedVariant = Color(0xFF005048)
val SecondaryFixed = Color(0xFFACEDFF)
val SecondaryFixedDim = Color(0xFF4CD7F6)
val OnSecondaryFixed = Color(0xFF001F26)
val OnSecondaryFixedVariant = Color(0xFF004E5C)
val TertiaryFixed = Color(0xFFFFDDB8)
val TertiaryFixedDim = Color(0xFFFFB95F)
val OnTertiaryFixed = Color(0xFF2A1700)
val OnTertiaryFixedVariant = Color(0xFF653E00)

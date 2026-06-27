package com.example.calmsource.core.model

/**
 * Semantic color tokens shared across mobile and TV UI layers.
 *
 * These named constants replace magic hex values scattered throughout the codebase,
 * making color choices self-documenting and easy to update globally.
 *
 * Values are raw ARGB [Long] constants (e.g. `0xFF10B981`). Convert to
 * `androidx.compose.ui.graphics.Color` at the call site:
 * ```kotlin
 * import androidx.compose.ui.graphics.Color
 * Text(color = Color(SemanticColors.Success))  // instead of Color(0xFF10B981)
 * ```
 *
 * This file lives in `core:model` which has no Compose dependency, so we
 * use plain [Long] values to keep the module framework-agnostic.
 */
object SemanticColors {
    // --- Status colors ---
    /** Green — healthy, connected, enabled, success states */
    const val Success = 0xFF10B981L
    /** Red — failed, disconnected, error states */
    const val Error = 0xFFEF4444L
    /** Amber — slow, expiring, caution states */
    const val Warning = 0xFFF59E0BL

    // --- Content colors ---
    /** Gold — star rating display */
    const val StarRating = 0xFFFBBF24L
    /** Green — seed count in torrent-style sources */
    const val SeedsGreen = 0xFF34D399L
    /** Blue — informational badges and links */
    const val InfoBlue = 0xFF3B82F6L
    /** Gray — disabled, inactive, neutral text */
    const val NeutralGray = 0xFF6B7280L

    // --- Overlay / transparency colors ---
    /** 60% black — player overlay backdrop */
    const val OverlayDark = 0x99000000L
    /** 70% black — TV player overlay (slightly darker for TV viewing distance) */
    const val OverlayDarker = 0xB3000000L
    /** 40% black — small button backgrounds over video */
    const val OverlayButton = 0x66000000L
    /** 20% black — subtle button hover over video */
    const val OverlaySubtle = 0x33000000L

    // --- Surface / card colors ---
    /** 10% white — faint glass card backgrounds */
    const val FaintWhite = 0x1AFFFFFFL
    /** 20% white — borders, dividers, inactive tracks */
    const val SemiWhite = 0x33FFFFFFL
    /** 10% black — subtle shadows on light backgrounds */
    const val SubtleBlack = 0x1A000000L

    // --- Text colors ---
    /** Light gray — metadata text, secondary information */
    const val LightGray = 0xFFCCCCCCL
    /** Dark code background — monospace/JSON debug sections */
    const val CodeBackground = 0xFF1C1B2AL
    /** Light purple tint — focused/highlighted items */
    const val FocusTint = 0x1F8B5CF6L
}

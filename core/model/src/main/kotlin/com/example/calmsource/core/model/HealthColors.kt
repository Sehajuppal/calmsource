package com.example.calmsource.core.model

/**
 * Extension functions that map health status enums to semantic color [Long] values.
 *
 * This centralizes the health→color mapping that was previously duplicated
 * 8+ times across SettingsScreens.kt and TvSettingsScreens.kt.
 *
 * Returns raw ARGB [Long] values from [SemanticColors]. Convert to Compose Color
 * at the call site:
 * ```kotlin
 * import androidx.compose.ui.graphics.Color
 * Box(modifier = Modifier.background(Color(extension.health.toColorLong())))
 * ```
 */

/** Maps [ExtensionHealth] to a semantic color [Long] value. */
fun ExtensionHealth.toColorLong(): Long = when (this) {
    ExtensionHealth.ACTIVE -> SemanticColors.Success
    ExtensionHealth.SLOW -> SemanticColors.Warning
    ExtensionHealth.FAILED -> SemanticColors.Error
    ExtensionHealth.DISABLED -> SemanticColors.NeutralGray
    ExtensionHealth.INVALID_MANIFEST -> SemanticColors.Error
    ExtensionHealth.NEEDS_CONFIGURATION -> SemanticColors.Warning
    ExtensionHealth.UNKNOWN -> SemanticColors.NeutralGray
}

/** Maps [DebridAccountHealth] to a semantic color [Long] value. */
fun DebridAccountHealth.toColorLong(): Long = when (this) {
    DebridAccountHealth.HEALTHY -> SemanticColors.Success
    DebridAccountHealth.SLOW -> SemanticColors.Warning
    DebridAccountHealth.FAILED -> SemanticColors.Error
}

/** Maps [ProviderHealth] to a semantic color [Long] value. */
fun ProviderHealth.toColorLong(): Long = when (this) {
    ProviderHealth.HEALTHY -> SemanticColors.Success
    ProviderHealth.SLOW -> SemanticColors.Warning
    ProviderHealth.FAILED -> SemanticColors.Error
}

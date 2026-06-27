package com.example.calmsource.core.model

/**
 * Security utility for masking sensitive tokens in UI display.
 *
 * Tokens should never be displayed in full. This extension provides a consistent
 * masking format: first 4 chars + "..." + last 4 chars.
 *
 * Example: "sk_live_abc123xyz789" → "sk_l...z789"
 *
 * @see DebridTokenSet.toString for model-level masking
 */

/** Minimum length required before partial masking is applied. Shorter tokens show as "••••••••". */
private const val MIN_MASKABLE_LENGTH = 16

/**
 * Masks a sensitive token string for safe UI display.
 *
 * @return Masked string showing only first and last 4 characters,
 *         or "••••••••" if the token is too short to mask safely.
 *         Tokens must be at least 16 characters so that at least 8 characters are hidden.
 */
fun String.maskToken(): String {
    return if (length >= MIN_MASKABLE_LENGTH) {
        take(4) + "..." + takeLast(4)
    } else {
        "••••••••"
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.parser

/**
 * Converts raw Nigerian currency strings to kobo (Long).
 * 1 NGN = 100 kobo. All storage is in kobo to avoid floating-point issues.
 *
 * Handles:
 *   "NGN5,000.00"  → 500_000
 *   "NGN 5,000.00" → 500_000
 *   "₦15,000"      → 1_500_000
 *   "N500.50"       → 50_050
 *   "2,000.00"      → 200_000
 *   "500"           → 50_000   (whole naira)
 *   "500.5"         → 50_050   (single-decimal — pad to 2)
 */
object AmountParser {

    private val CURRENCY_PREFIX = Regex("^(?:NGN|₦|N)\\s*", RegexOption.IGNORE_CASE)

    /**
     * Returns the amount in kobo, or null if the string cannot be parsed.
     * Returns null (not throws) on garbage input so callers can skip safely.
     */
    fun parseToKobo(raw: String): Long? {
        if (raw.isBlank()) return null
        val stripped = raw.trim()
            .let { CURRENCY_PREFIX.replace(it, "") }
            .replace(",", "")
            .trim()

        if (stripped.isBlank()) return null

        return if (stripped.contains('.')) {
            val dotIdx = stripped.indexOf('.')
            val nairaStr = stripped.substring(0, dotIdx)
            val koboStr = stripped.substring(dotIdx + 1)

            val naira = nairaStr.toLongOrNull() ?: return null
            val kobo = when (koboStr.length) {
                0 -> 0L
                1 -> (koboStr.toLongOrNull() ?: return null) * 10L
                2 -> koboStr.toLongOrNull() ?: return null
                else -> return null  // more than 2 decimal places = not a currency value
            }
            if (naira < 0 || kobo < 0) return null
            naira * 100L + kobo
        } else {
            val naira = stripped.toLongOrNull() ?: return null
            if (naira < 0) return null
            naira * 100L
        }
    }
}

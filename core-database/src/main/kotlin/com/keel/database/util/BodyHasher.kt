// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.util

import java.security.MessageDigest

/**
 * Computes a stable hash of an SMS/notification body for Level 1 deduplication.
 *
 * Normalisation before hashing:
 *  - lowercase
 *  - collapse all whitespace to single space
 *  - mask digit runs that look like amounts/balances (to tolerate minor formatting differences)
 *
 * Returns first 32 hex chars (128-bit prefix) — unique enough for a 10-minute window.
 */
object BodyHasher {

    fun hash(body: String): String {
        val normalised = normalize(body)
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalised.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Normalise a bank message body for dedup hashing. Lowercases, collapses whitespace,
     * strips any character that isn't a digit, letter, or a financial punctuation mark
     * (., : / ₦ # * -). This makes the hash tolerant of zero-width spaces, tab variants,
     * and cosmetic punctuation that some banks insert between otherwise identical sends.
     */
    internal fun normalize(body: String): String = body
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("[^a-z0-9.,:/₦#*-]"), "")
        .trim()
}

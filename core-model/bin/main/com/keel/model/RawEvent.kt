// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.model

import kotlinx.serialization.Serializable

/**
 * Raw bank message as received — before any parsing. Stored immediately on receipt so
 * nothing is ever dropped if the parser crashes or the model is unavailable.
 */
@Serializable
data class RawEvent(
    val id: Long = 0,
    /** SMS sender address (e.g. "GTBank") or notification package (e.g. "com.gtbank.gtworldv1") */
    val senderAddress: String,
    /** Notification package name — null for SMS events */
    val senderPackage: String? = null,
    val body: String,
    /**
     * SHA-256 prefix of normalised body (lowercase, whitespace collapsed, amounts masked).
     * Used for Level 1 dedup: same hash in a 10-minute window from the same source = duplicate.
     */
    val bodyHash: String,
    val source: Source,
    val receivedAt: Long,
    val processed: Boolean = false,
    /**
     * Set true by ParserRegistry when regex returns NeedsLLM.
     * LlmParserWorker picks up rows with this flag set.
     */
    val needsLlmParsing: Boolean = false,
)

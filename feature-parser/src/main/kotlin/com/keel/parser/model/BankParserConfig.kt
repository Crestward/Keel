// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.parser.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root config for a single bank. Loaded from `assets/parsers/<bank>.json`.
 * Adding a new bank = drop a JSON file. Zero code changes required.
 */
@Serializable
data class BankParserConfig(
    @SerialName("bank_id") val bankId: String,
    @SerialName("sender_ids") val senderIds: List<String>,
    @SerialName("package_names") val packageNames: List<String>,
    val patterns: List<PatternConfig>,
)

/**
 * One regex pattern for a specific message format.
 * [type] is "CREDIT" or "DEBIT" — determined by the pattern context, not extracted from the text.
 * [groups] maps capture-group indices to semantic field names.
 * Patterns are tried in order; first match wins.
 */
@Serializable
data class PatternConfig(
    val regex: String,
    val type: String,
    val groups: List<GroupMapping>,
)

/**
 * Maps a regex capture-group index (1-based) to a field name.
 * Supported field names: "amount", "balance", "merchant".
 */
@Serializable
data class GroupMapping(
    val field: String,
    val group: Int,
)

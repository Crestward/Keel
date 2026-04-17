// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.model

import kotlinx.serialization.Serializable

/**
 * A bank account identified by the (bank, maskedNumber) unique pair.
 *
 * Created automatically by AccountNormalizer during transaction parsing.
 * The balance here is updated from parsed balance fields and may lag the live
 * bank balance — it's used for guardrail checks, not as a source of truth.
 *
 * @param bank Bank identifier (e.g. "GTBank", "Kuda").
 * @param maskedNumber Last 4–6 digits or masked format from SMS (e.g. "****5315").
 * @param balanceKobo Latest known balance in kobo. Updated on every parsed transaction.
 * @param nickname Optional user-assigned label (e.g. "Salary account").
 */
@Serializable
data class Account(
    val id: Long = 0,
    val bank: String,
    val maskedNumber: String,
    val balanceKobo: Long = 0L,
    val nickname: String? = null,
)

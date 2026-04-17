// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.model

import kotlinx.serialization.Serializable

/**
 * A parsed bank transaction. Amounts are always in kobo (1 NGN = 100 kobo) to avoid
 * floating-point precision issues with financial data.
 *
 * All columns defined here — Room schema won't change after Phase 1.
 */
@Serializable
data class Transaction(
    val id: Long = 0,
    /** Amount in kobo — never store as Naira float */
    val amount: Long,
    val type: TransactionType,
    /** Merchant name, normalised to lowercase */
    val merchant: String,
    val category: String = "other",
    /** Account balance after this transaction, in kobo. Null if not in message. */
    val balance: Long? = null,
    /** Raw SMS/notification body — kept for debugging, never shown in release UI */
    val rawText: String = "",
    /** Whether this row came from SMS, notification push, or merged from both */
    val source: Source,
    /** True once the transaction has been fully parsed and indexed */
    val parsed: Boolean = true,
    /** FK to accounts table — resolved by AccountNormalizer during parsing */
    val accountId: Long? = null,
    /** Links to the specific AgentRun that processed this transaction */
    val agentRunId: Long? = null,
    val timestamp: Long,
)

/**
 * Merge two records that represent the same underlying transaction arriving via
 * different sources (SMS + notification). Prefers existing non-null fields and
 * backfills nulls from the incoming record. Raw text is unioned so neither
 * source's original body is lost. Timestamp is kept at the earlier value
 * (first-observed), and agentRunId prefers the newer run if set.
 */
fun Transaction.mergeWith(other: Transaction): Transaction = copy(
    // Financial facts must already match (they're the dedup key); prefer existing.
    amount = amount,
    type = type,
    // Backfill nullable / weakly-typed fields from whichever source has them.
    merchant = if (merchant.isNotBlank()) merchant else other.merchant,
    category = if (category != "other") category else other.category,
    balance = balance ?: other.balance,
    accountId = accountId ?: other.accountId,
    agentRunId = other.agentRunId ?: agentRunId,
    rawText = when {
        rawText.isBlank() -> other.rawText
        other.rawText.isBlank() || rawText == other.rawText -> rawText
        else -> "$rawText\n---\n${other.rawText}"
    },
    source = Source.BOTH,
    parsed = parsed || other.parsed,
    // Keep the earlier timestamp — that's when the transaction actually happened.
    timestamp = minOf(timestamp, other.timestamp),
)

@Serializable
enum class TransactionType { CREDIT, DEBIT }

/**
 * Where the raw event originated.
 * BOTH means a SMS and notification were merged (same transaction, two sources).
 */
@Serializable
enum class Source { SMS, NOTIFICATION, BOTH }

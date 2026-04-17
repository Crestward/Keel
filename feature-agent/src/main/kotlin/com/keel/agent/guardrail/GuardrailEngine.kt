// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.guardrail

import com.keel.database.dao.AccountDao
import com.keel.database.dao.TransactionDao
import com.keel.model.Insight
import com.keel.model.Severity
import com.keel.model.Transaction
import com.keel.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates deterministic safety rules against a newly-inserted transaction.
 *
 * All rules are zero-side-effect: they read from the DB and return [Insight] objects.
 * The caller ([ParserWorker]) is responsible for inserting the insights and posting
 * notifications via [KeelNotificationManager].
 *
 * Only 3 rules are included — all are time-sensitive enough to bypass the 6-hour
 * agent cycle:
 *  - [checkLowBalance] — fires on CRITICAL account balance threshold
 *  - [checkDuplicateCharge] — fires when the same debit repeats within 24 h
 *  - [checkLargeUnexpectedDebit] — fires when a debit is > 3× the merchant's average
 */
@Singleton
class GuardrailEngine @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
) {

    /**
     * Evaluate all guardrail rules against [transaction].
     *
     * @param transaction The just-inserted transaction (id field must be non-zero).
     * @return List of insights to immediately surface — may be empty.
     */
    suspend fun evaluate(transaction: Transaction): List<Insight> {
        val insights = mutableListOf<Insight>()

        checkLowBalance(transaction)?.let { insights += it }

        if (transaction.type == TransactionType.DEBIT) {
            checkDuplicateCharge(transaction)?.let { insights += it }
            checkLargeUnexpectedDebit(transaction)?.let { insights += it }
        }

        return insights
    }

    // ─── Rule 1: Low Balance ─────────────────────────────────────────────────

    private suspend fun checkLowBalance(tx: Transaction): Insight? {
        // Prefer the post-transaction balance reported in the SMS/notification.
        // Fall back to the stored account record if the SMS didn't include it.
        val balanceKobo = tx.balance
            ?: tx.accountId?.let { accountDao.getById(it)?.balanceKobo }
            ?: return null

        if (balanceKobo >= LOW_BALANCE_THRESHOLD_KOBO) return null

        val naira = balanceKobo / 100
        return Insight(
            title = "Low Balance Alert",
            body = "Your account balance is ₦$naira, which is below ₦5,000. " +
                    "Please top up to avoid failed transactions.",
            severity = Severity.CRITICAL,
            agentGenerated = false,
            agentRunId = null,
            createdAt = System.currentTimeMillis(),
        )
    }

    // ─── Rule 2: Duplicate Charge ────────────────────────────────────────────

    private suspend fun checkDuplicateCharge(tx: Transaction): Insight? {
        val since = tx.timestamp - TWENTY_FOUR_HOURS_MS
        val prior = transactionDao.findDuplicateCharge(
            merchant = tx.merchant,
            amount = tx.amount,
            since = since,
            excludeId = tx.id,
        ) ?: return null

        val naira = tx.amount / 100
        val merchant = tx.merchant.replaceFirstChar { it.uppercaseChar() }
        return Insight(
            title = "Possible Duplicate Charge",
            body = "You were charged ₦$naira by $merchant twice within 24 hours. " +
                    "If this is unexpected, contact your bank.",
            severity = Severity.WARNING,
            agentGenerated = false,
            agentRunId = null,
            createdAt = System.currentTimeMillis(),
        )
    }

    // ─── Rule 3: Large Unexpected Debit ─────────────────────────────────────

    private suspend fun checkLargeUnexpectedDebit(tx: Transaction): Insight? {
        // Need at least MIN_HISTORY_COUNT prior debits to establish a meaningful average
        val count = transactionDao.countDebitsByMerchant(
            merchant = tx.merchant,
            excludeId = tx.id,
        )
        if (count < MIN_HISTORY_COUNT) return null

        val average = transactionDao.averageDebitByMerchant(
            merchant = tx.merchant,
            excludeId = tx.id,
        )
        if (average == 0L) return null
        if (tx.amount <= average * LARGE_DEBIT_MULTIPLIER) return null

        val naira = tx.amount / 100
        val avgNaira = average / 100
        val merchant = tx.merchant.replaceFirstChar { it.uppercaseChar() }
        return Insight(
            title = "Unusually Large Charge",
            body = "You were charged ₦$naira by $merchant, which is more than " +
                    "3× your usual ₦$avgNaira. Please verify this transaction.",
            severity = Severity.WARNING,
            agentGenerated = false,
            agentRunId = null,
            createdAt = System.currentTimeMillis(),
        )
    }

    companion object {
        /** ₦5,000 = 500,000 kobo */
        const val LOW_BALANCE_THRESHOLD_KOBO = 500_000L

        private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L

        /** Minimum prior transactions before LargeUnexpected fires */
        private const val MIN_HISTORY_COUNT = 3

        /** Multiplier threshold: fire if amount > average × this value */
        private const val LARGE_DEBIT_MULTIPLIER = 3
    }
}

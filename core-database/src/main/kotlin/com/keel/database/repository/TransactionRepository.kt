// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.repository

import com.keel.database.dao.TransactionDao
import com.keel.database.entity.toEntity
import com.keel.database.entity.toModel
import com.keel.model.InsertResult
import com.keel.model.Transaction
import com.keel.model.TransactionType
import com.keel.model.mergeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) {
    /**
     * Serialises check-then-insert across concurrent callers (SMS receiver +
     * NotificationListener can race for the same txn). Single singleton instance
     * so one mutex suffices for Phase 1's throughput.
     */
    private val dedupMutex = Mutex()

    /**
     * Three-level dedup strategy before insert:
     *
     * Level 2 (balance fingerprint, 24h → fallback 7d): if a transaction with the
     * same amount + type + balance exists in the last 24h, it's the same txn
     * arriving via both sources. Merge via [mergeWith] so neither source's fields
     * are lost. The 7-day fallback catches the F12 edge case where an SMS is
     * delayed far beyond the notification's arrival time.
     *
     * Level 3 (amount+type+merchant, 2h): fallback when balance is unavailable
     * on either side. Also merges rather than dropping the incoming record —
     * the new one may carry a balance or richer raw text.
     *
     * If neither matches, insert as new.
     */
    suspend fun insertOrMerge(tx: Transaction): InsertResult = dedupMutex.withLock {
        // Window anchored to the transaction's own timestamp so that back-dated
        // records (e.g. delayed SMS with an embedded timestamp) still dedup correctly.
        val anchor = tx.timestamp

        // Level 2 — balance fingerprint (strongest signal)
        val txBalance = tx.balance
        if (txBalance != null) {
            val match = dao.findByAmountAndBalance(
                amount = tx.amount,
                type = tx.type,
                balance = txBalance,
                since = anchor - TWENTY_FOUR_HOURS_MS,
            ) ?: dao.findByAmountAndBalance(
                // F12 fallback: 7-day window for heavily delayed SMS
                amount = tx.amount,
                type = tx.type,
                balance = txBalance,
                since = anchor - SEVEN_DAYS_MS,
            )
            if (match != null) {
                val merged = match.toModel().mergeWith(tx)
                dao.update(merged.toEntity().copy(id = match.id))
                return@withLock InsertResult.Merged(match.id)
            }
        }

        // Level 3 — amount + type + merchant (2h window)
        val level3Match = dao.findByAmountAndMerchant(
            amount = tx.amount,
            type = tx.type,
            merchant = tx.merchant,
            since = anchor - TWO_HOURS_MS,
        )
        if (level3Match != null) {
            val merged = level3Match.toModel().mergeWith(tx)
            dao.update(merged.toEntity().copy(id = level3Match.id))
            return@withLock InsertResult.Merged(level3Match.id)
        }

        val id = dao.insert(tx.toEntity())
        InsertResult.Inserted(id)
    }

    fun getAllFlow(): Flow<List<Transaction>> =
        dao.getAllFlow().map { it.map { e -> e.toModel() } }

    suspend fun getRecent(limit: Int): List<Transaction> =
        dao.getRecent(limit).map { it.toModel() }

    suspend fun getById(id: Long): Transaction? = dao.getById(id)?.toModel()

    suspend fun updateCategory(id: Long, category: String) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(category = category))
    }

    suspend fun updateMerchant(id: Long, merchant: String) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(merchant = merchant))
    }

    suspend fun sumByTypeAndRange(type: TransactionType, from: Long, to: Long): Long =
        dao.sumByTypeAndRange(type, from, to)

    suspend fun query(
        from: Long,
        to: Long,
        category: String? = null,
        merchant: String? = null,
        limit: Int = 20,
    ): List<Transaction> = dao.query(from, to, category, merchant, minOf(limit, 20)).map { it.toModel() }

    companion object {
        private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L
        private const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.keel.database.entity.TransactionEntity
import com.keel.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Sum of all transactions by type within a date range. Returns 0 if none.
     * Used by get_spending_summary tool.
     */
    @Query(
        """SELECT COALESCE(SUM(amount), 0) FROM transactions
           WHERE type = :type AND timestamp BETWEEN :from AND :to"""
    )
    suspend fun sumByTypeAndRange(type: TransactionType, from: Long, to: Long): Long

    /**
     * Level 2 dedup: same amount + type + balance fingerprint within a time window.
     * Strongest dedup signal — same post-transaction balance across sources is
     * effectively a collision only if it's the same transaction. Does NOT exclude
     * by source: a reworded same-source SMS that missed the body-hash window can
     * still legitimately match here. Newest-first so merges target the most recent
     * record.
     */
    @Query(
        """SELECT * FROM transactions
           WHERE amount = :amount
             AND type = :type
             AND balance = :balance
             AND timestamp >= :since
           ORDER BY timestamp DESC
           LIMIT 1"""
    )
    suspend fun findByAmountAndBalance(
        amount: Long,
        type: TransactionType,
        balance: Long,
        since: Long,
    ): TransactionEntity?

    /**
     * Level 3 dedup: same merchant + amount + type within a 2-hour window.
     * Fallback when balance is unavailable.
     */
    @Query(
        """SELECT * FROM transactions
           WHERE amount = :amount
             AND type = :type
             AND merchant = :merchant
             AND timestamp >= :since
           ORDER BY timestamp DESC
           LIMIT 1"""
    )
    suspend fun findByAmountAndMerchant(
        amount: Long,
        type: TransactionType,
        merchant: String,
        since: Long,
    ): TransactionEntity?

    /** For the agent's query_transactions tool — hard-capped at 20 rows */
    @Query(
        """SELECT * FROM transactions
           WHERE timestamp BETWEEN :from AND :to
             AND (:category IS NULL OR category = :category)
             AND (:merchant IS NULL OR merchant LIKE '%' || :merchant || '%')
           ORDER BY timestamp DESC
           LIMIT :limit"""
    )
    suspend fun query(
        from: Long,
        to: Long,
        category: String?,
        merchant: String?,
        limit: Int = 20,
    ): List<TransactionEntity>

    /** Top N merchants by total spend in a period — for get_spending_summary */
    @Query(
        """SELECT merchant, SUM(amount) as total FROM transactions
           WHERE type = 'DEBIT' AND timestamp BETWEEN :from AND :to
           GROUP BY merchant
           ORDER BY total DESC
           LIMIT :n"""
    )
    suspend fun topMerchantsBySpend(from: Long, to: Long, n: Int = 5): List<MerchantSpend>

    @Query("SELECT * FROM transactions WHERE agent_run_id = :runId")
    suspend fun getByRunId(runId: Long): List<TransactionEntity>

    /**
     * DuplicateChargeGuardrail: find a prior DEBIT with same merchant+amount within [since].
     * [excludeId] is the ID of the just-inserted transaction — exclude it from the search.
     */
    @Query(
        """SELECT * FROM transactions
           WHERE merchant = :merchant
             AND amount = :amount
             AND type = 'DEBIT'
             AND timestamp >= :since
             AND id != :excludeId
           ORDER BY timestamp DESC
           LIMIT 1"""
    )
    suspend fun findDuplicateCharge(
        merchant: String,
        amount: Long,
        since: Long,
        excludeId: Long,
    ): TransactionEntity?

    /**
     * LargeUnexpectedDebitGuardrail: average of all past DEBIT amounts for this merchant.
     * Returns null (COALESCE to 0) if no prior transactions exist.
     * [excludeId] excludes the just-inserted transaction from the average.
     * [minCount] is checked via [countDebitsByMerchant] before trusting the result.
     */
    @Query(
        """SELECT COALESCE(AVG(amount), 0) FROM transactions
           WHERE merchant = :merchant
             AND type = 'DEBIT'
             AND id != :excludeId"""
    )
    suspend fun averageDebitByMerchant(merchant: String, excludeId: Long): Long

    /** How many past DEBITs exist for this merchant (for statistical significance check). */
    @Query(
        """SELECT COUNT(*) FROM transactions
           WHERE merchant = :merchant AND type = 'DEBIT' AND id != :excludeId"""
    )
    suspend fun countDebitsByMerchant(merchant: String, excludeId: Long): Int
}

data class MerchantSpend(val merchant: String, val total: Long)

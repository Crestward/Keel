// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.keel.database.entity.AccountEntity

@Dao
interface AccountDao {

    /** Insert-or-ignore: only inserts if (bank, maskedNumber) pair is new. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE bank = :bank AND masked_number = :maskedNumber LIMIT 1")
    suspend fun findByBankAndMasked(bank: String, maskedNumber: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY bank ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("UPDATE accounts SET balance_kobo = :balanceKobo WHERE id = :id")
    suspend fun updateBalance(id: Long, balanceKobo: Long)
}

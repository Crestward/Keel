// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.keel.database.entity.MerchantEntity

@Dao
interface MerchantDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(merchant: MerchantEntity): Long

    @Update
    suspend fun update(merchant: MerchantEntity)

    @Query("SELECT * FROM merchants WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): MerchantEntity?

    @Query("SELECT * FROM merchants ORDER BY name ASC")
    suspend fun getAll(): List<MerchantEntity>

    @Query("SELECT * FROM merchants WHERE is_subscription = 1 ORDER BY name ASC")
    suspend fun getSubscriptions(): List<MerchantEntity>

}

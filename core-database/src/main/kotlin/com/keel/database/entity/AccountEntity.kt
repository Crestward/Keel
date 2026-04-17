// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["bank", "masked_number"], unique = true),  // dedup key
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bank: String,
    @ColumnInfo(name = "masked_number") val maskedNumber: String,
    @ColumnInfo(name = "balance_kobo") val balanceKobo: Long = 0L,
    val nickname: String? = null,
)

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "merchants",
    indices = [
        Index(value = ["name"], unique = true),   // canonical name lookup
        Index("category"),                         // category-grouped queries
        Index("is_subscription"),                  // subscription detection
    ],
)
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Canonical lowercase merchant name. */
    val name: String,
    /** JSON array of alternative name strings seen in messages. */
    val variants: String = "[]",
    val category: String = "other",
    @ColumnInfo(name = "is_subscription") val isSubscription: Boolean = false,
    @ColumnInfo(name = "subscription_interval_days") val subscriptionIntervalDays: Int? = null,
)

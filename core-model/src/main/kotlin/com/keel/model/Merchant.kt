// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.model

import kotlinx.serialization.Serializable

/**
 * A canonical merchant record.
 *
 * @param variants Alternative spellings seen in SMS/notifications (e.g. "SHOPRITE", "Shoprite NG").
 *                 Stored as JSON in the DB column.
 * @param category Slug matching a row in the categories table (e.g. "food_dining").
 * @param isSubscription True if this merchant charges on a recurring schedule.
 * @param subscriptionIntervalDays Typical billing cycle in days (30 for monthly, 7 for weekly).
 */
@Serializable
data class Merchant(
    val id: Long = 0,
    val name: String,
    val variants: List<String> = emptyList(),
    val category: String = "other",
    val isSubscription: Boolean = false,
    val subscriptionIntervalDays: Int? = null,
)

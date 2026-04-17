// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.model

import kotlinx.serialization.Serializable

/**
 * A spending category in the hierarchical taxonomy.
 *
 * Top-level categories have [parentId] = null.
 * Sub-categories reference their parent by ID.
 *
 * Seeded with 11 Nigerian-specific categories by [CategorySeeder] at first launch.
 */
@Serializable
data class Category(
    val id: Long = 0,
    /** Human-readable name shown in the UI, e.g. "Food & Dining". */
    val name: String,
    /** URL-safe slug used in DataStore and agent tool calls, e.g. "food_dining". */
    val slug: String,
    /** FK to parent category. Null for top-level categories. */
    val parentId: Long? = null,
    /** Emoji icon rendered next to the category name in the UI. */
    val icon: String = "",
)

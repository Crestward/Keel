// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.seeder

import com.keel.database.dao.CategoryDao
import com.keel.database.entity.CategoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the 11 canonical Nigerian spending categories into the categories table.
 *
 * Idempotent: uses INSERT OR IGNORE so it is safe to call on every app launch.
 * Actual inserts only happen when [CategoryDao.count] == 0 to avoid 11 no-op
 * DB writes on every cold start.
 */
@Singleton
class CategorySeeder @Inject constructor(
    private val categoryDao: CategoryDao,
) {

    suspend fun seedIfEmpty() {
        if (categoryDao.count() > 0) return
        SEED_CATEGORIES.forEach { categoryDao.insert(it) }
    }

    companion object {
        val SEED_CATEGORIES = listOf(
            CategoryEntity(name = "Food & Dining",      slug = "food_dining",      icon = "🍔"),
            CategoryEntity(name = "Transport",          slug = "transport",        icon = "🚗"),
            CategoryEntity(name = "Shopping",           slug = "shopping",         icon = "🛍️"),
            CategoryEntity(name = "Bills & Utilities",  slug = "bills_utilities",  icon = "💡"),
            CategoryEntity(name = "Entertainment",      slug = "entertainment",    icon = "🎬"),
            CategoryEntity(name = "Health",             slug = "health",           icon = "💊"),
            CategoryEntity(name = "Education",          slug = "education",        icon = "📚"),
            CategoryEntity(name = "Airtime & Data",     slug = "airtime_data",     icon = "📱"),
            CategoryEntity(name = "Transfer",           slug = "transfer",         icon = "💸"),
            CategoryEntity(name = "Savings",            slug = "savings",          icon = "🏦"),
            CategoryEntity(name = "Other",              slug = "other",            icon = "📦"),
        )
    }
}

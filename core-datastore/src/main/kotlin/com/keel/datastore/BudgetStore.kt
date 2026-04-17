// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.budgetDataStore: DataStore<Preferences> by preferencesDataStore("keel_budgets")

/**
 * DataStore-backed budget configuration.
 *
 * Keys are category slugs (e.g. "food_dining"). Values are monthly budget amounts
 * in kobo. Amounts of 0 mean no budget set for that category.
 *
 * Used by [GuardrailEngine] (Phase 4) and [ContextBuilder] (Phase 5) to provide
 * budget context to the agent.
 */
@Singleton
class BudgetStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Sets or replaces the monthly budget for [categorySlug].
     * Pass 0 to clear the budget for a category.
     */
    suspend fun setBudget(categorySlug: String, amountKobo: Long) {
        context.budgetDataStore.edit { prefs ->
            prefs[longPreferencesKey(categorySlug)] = amountKobo
        }
    }

    /** Returns the monthly budget in kobo for [categorySlug], or 0 if unset. */
    suspend fun getBudget(categorySlug: String): Long =
        context.budgetDataStore.data.first()[longPreferencesKey(categorySlug)] ?: 0L

    /**
     * Returns all categories that have a budget set (value > 0).
     * Map key = category slug, value = monthly budget in kobo.
     */
    suspend fun getAllBudgets(): Map<String, Long> =
        context.budgetDataStore.data.first()
            .asMap()
            .mapNotNull { (key, value) ->
                val amount = value as? Long ?: return@mapNotNull null
                if (amount > 0L) key.name to amount else null
            }
            .toMap()

    /** Removes the budget entry for [categorySlug]. */
    suspend fun clearBudget(categorySlug: String) {
        context.budgetDataStore.edit { prefs ->
            prefs.remove(longPreferencesKey(categorySlug))
        }
    }
}

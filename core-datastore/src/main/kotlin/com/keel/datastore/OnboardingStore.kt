// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "keel_onboarding")

private val KEY_IS_COMPLETE = booleanPreferencesKey("is_complete")

/**
 * DataStore-backed onboarding completion flag.
 *
 * Written once when the user finishes the onboarding flow (model downloaded and
 * validated, or explicitly skipped). Read by [KeelNavHost] to decide the start
 * destination — "dashboard" if complete, "onboarding" otherwise.
 */
@Singleton
class OnboardingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Emits true once the user has completed or skipped the onboarding flow. */
    val isCompleteFlow: Flow<Boolean> =
        context.onboardingDataStore.data.map { prefs ->
            prefs[KEY_IS_COMPLETE] ?: false
        }

    /** Marks onboarding as complete. Idempotent — safe to call multiple times. */
    suspend fun markComplete() {
        context.onboardingDataStore.edit { prefs ->
            prefs[KEY_IS_COMPLETE] = true
        }
    }
}

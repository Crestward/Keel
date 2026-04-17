// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backfillDataStore: DataStore<Preferences> by preferencesDataStore("keel_backfill")

/**
 * Stores the flag that prevents the SMS inbox backfill from running more than once.
 *
 * Run logic: on app start, check isSmsBackfillCompleted(). If false, run SmsInboxBackfill,
 * then call markSmsBackfillCompleted(). Never runs again unless the user triggers
 * "Rescan SMS" from Settings, which calls resetSmsBackfill().
 */
@Singleton
class BackfillStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val smsBackfillCompleted = booleanPreferencesKey("sms_backfill_completed")

    suspend fun isSmsBackfillCompleted(): Boolean =
        context.backfillDataStore.data.first()[smsBackfillCompleted] ?: false

    suspend fun markSmsBackfillCompleted() {
        context.backfillDataStore.edit { it[smsBackfillCompleted] = true }
    }

    /** Called from Settings → "Rescan SMS" */
    suspend fun resetSmsBackfill() {
        context.backfillDataStore.edit { it[smsBackfillCompleted] = false }
    }
}

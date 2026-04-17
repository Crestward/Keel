// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.freqCapDataStore: DataStore<Preferences> by preferencesDataStore("keel_freq_cap")

/**
 * DataStore-backed frequency cap for WARNING-level notifications.
 *
 * Two independent gates, both must pass for [canSend] to return true:
 *   1. Per-key cooldown: once sent, the same rule+merchant pair is suppressed for [COOLDOWN_MS]
 *      (72 hours). Prevents alert fatigue for repeating conditions.
 *   2. Global daily cap: at most [MAX_WARNINGS_PER_DAY] WARNING notifications per calendar day.
 *      Resets automatically at midnight.
 *
 * CRITICAL notifications skip both gates — they are always delivered.
 */
@Singleton
class FrequencyCapStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Returns true if a WARNING notification with this [ruleKey] is allowed to send.
     *
     * [ruleKey] format: "{ruleId}:{merchantOrAccount}" — e.g. "duplicate_charge:MTN"
     */
    suspend fun canSend(ruleKey: String): Boolean {
        val prefs = context.freqCapDataStore.data.first()

        // Gate 1: per-key cooldown
        val lastSent = prefs[longPreferencesKey(LAST_SENT_PREFIX + ruleKey)] ?: 0L
        if (System.currentTimeMillis() - lastSent < COOLDOWN_MS) return false

        // Gate 2: global daily cap
        val today = todayString()
        val storedDate = prefs[stringPreferencesKey(DAILY_DATE_KEY)] ?: ""
        val dailyCount = if (storedDate == today) prefs[intPreferencesKey(DAILY_COUNT_KEY)] ?: 0
                         else 0
        return dailyCount < MAX_WARNINGS_PER_DAY
    }

    /**
     * Records that a WARNING notification with this [ruleKey] was sent.
     * Must be called immediately after the notification is posted.
     */
    suspend fun recordSent(ruleKey: String) {
        val now = System.currentTimeMillis()
        val today = todayString()

        context.freqCapDataStore.edit { prefs ->
            // Update per-key timestamp
            prefs[longPreferencesKey(LAST_SENT_PREFIX + ruleKey)] = now

            // Update daily count (reset if new day)
            val storedDate = prefs[stringPreferencesKey(DAILY_DATE_KEY)] ?: ""
            val currentCount = if (storedDate == today) prefs[intPreferencesKey(DAILY_COUNT_KEY)] ?: 0
                               else 0
            prefs[stringPreferencesKey(DAILY_DATE_KEY)] = today
            prefs[intPreferencesKey(DAILY_COUNT_KEY)] = currentCount + 1
        }
    }

    /** Resets per-key cooldown — useful in tests or after user dismisses a type of alert. */
    suspend fun resetKey(ruleKey: String) {
        context.freqCapDataStore.edit { prefs ->
            prefs.remove(longPreferencesKey(LAST_SENT_PREFIX + ruleKey))
        }
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private const val LAST_SENT_PREFIX = "last_sent_"
        private const val DAILY_DATE_KEY = "daily_date"
        private const val DAILY_COUNT_KEY = "daily_count"

        /** 72 hours cooldown per rule+merchant pair */
        const val COOLDOWN_MS = 3 * 24 * 60 * 60 * 1000L

        /** At most 3 WARNING notifications per calendar day */
        const val MAX_WARNINGS_PER_DAY = 3
    }
}

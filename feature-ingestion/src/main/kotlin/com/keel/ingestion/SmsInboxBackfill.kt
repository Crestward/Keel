// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ingestion

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.keel.database.repository.RawEventRepository
import com.keel.model.RawEvent
import com.keel.model.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time backfill of the SMS inbox for the last 90 days.
 *
 * Called by SmsBackfillWorker (in app module) on first launch.
 * After completion, BackfillStore.markSmsBackfillCompleted() is called by the Worker.
 * Never runs again automatically — only via Settings → "Rescan SMS".
 *
 * Applies the same sender ID + body keyword filter as SmsBroadcastReceiver.
 * Each message goes through Level 1 dedup in RawEventRepository.insertIfNotDuplicate().
 */
@Singleton
class SmsInboxBackfill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rawEventRepository: RawEventRepository,
) {
    /**
     * Returns the count of raw events successfully inserted.
     */
    suspend fun run(): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - NINETY_DAYS_MS
        var inserted = 0

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoff.toString())

        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val addrIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addrIndex) ?: continue
                    val body = cursor.getString(bodyIndex) ?: continue
                    val date = cursor.getLong(dateIndex)

                    if (!BankPackages.isBankSms(address, body)) continue

                    val event = RawEvent(
                        senderAddress = address,
                        senderPackage = null,
                        body = body,
                        bodyHash = RawEventRepository.computeBodyHash(body),
                        source = Source.SMS,
                        receivedAt = date,
                    )

                    // Level 1 dedup — uses a 10-minute window; backfill uses received_at timestamps
                    // so duplicates within that window (e.g. multi-part SMS) are caught
                    rawEventRepository.insertIfNotDuplicate(event)
                    inserted++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS backfill failed", e)
        }

        Log.d(TAG, "Backfill complete: $inserted messages processed")
        inserted
    }

    companion object {
        private const val TAG = "SmsInboxBackfill"
        private const val NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000
    }
}

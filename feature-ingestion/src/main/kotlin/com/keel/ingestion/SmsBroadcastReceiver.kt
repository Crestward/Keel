// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.keel.database.repository.RawEventRepository
import com.keel.model.RawEvent
import com.keel.model.Source
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SMS BroadcastReceiver — captures bank SMS alerts.
 *
 * Multi-part SMS: the system delivers them as a single intent with multiple PDUs.
 * We join all parts before inserting, so the full message body is always stored.
 *
 * Filters: sender ID must be in BankPackages.SMS_SENDER_IDS (case-insensitive) AND
 * the body must contain at least one bank keyword. Never filter by sender alone.
 *
 * Uses goAsync() to keep the BroadcastReceiver alive long enough for the coroutine
 * insert to complete.
 */
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rawEventRepository: RawEventRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) return@launch

                // Group multi-part SMS by originating address — join bodies in order
                val grouped = messages.groupBy { it.originatingAddress ?: "" }

                for ((address, parts) in grouped) {
                    if (address.isBlank()) continue
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    if (body.isBlank()) continue

                    // Must pass both sender ID and body keyword checks
                    if (!BankPackages.isBankSms(address, body)) continue

                    val now = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    val event = RawEvent(
                        senderAddress = address,
                        senderPackage = null,
                        body = body,
                        bodyHash = RawEventRepository.computeBodyHash(body),
                        source = Source.SMS,
                        receivedAt = now,
                    )

                    rawEventRepository.insertIfNotDuplicate(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMS receive failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "KeelSmsReceiver"
    }
}

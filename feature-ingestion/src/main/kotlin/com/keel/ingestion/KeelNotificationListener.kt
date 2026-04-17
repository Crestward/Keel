// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ingestion

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.keel.database.repository.RawEventRepository
import com.keel.model.RawEvent
import com.keel.model.Source
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Notification Listener Service — captures transaction push notifications from Nigerian bank apps.
 *
 * Filters by verified package names in BankPackages.NOTIFICATION_PACKAGES.
 * Prefers EXTRA_BIG_TEXT (expanded notification text) over EXTRA_TEXT — banks often
 * put the full transaction details only in the big-text variant.
 *
 * Level 1 dedup via RawEventRepository.insertIfNotDuplicate() before any insert.
 *
 * OEM kill recovery: onListenerDisconnected() calls requestRebind() — essential for
 * Tecno/Infinix/Xiaomi devices that aggressively kill background services.
 */
@AndroidEntryPoint
class KeelNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var rawEventRepository: RawEventRepository

    // SupervisorJob: if one coroutine fails, it doesn't cancel the scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in BankPackages.NOTIFICATION_PACKAGES) return

        val extras = sbn.notification?.extras ?: return

        // Prefer expanded big text — contains full transaction details in most bank apps
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        if (body.isBlank()) return

        val now = System.currentTimeMillis()
        val event = RawEvent(
            senderAddress = pkg,
            senderPackage = pkg,
            body = body,
            bodyHash = RawEventRepository.computeBodyHash(body),
            source = Source.NOTIFICATION,
            receivedAt = now,
        )

        scope.launch {
            try {
                rawEventRepository.insertIfNotDuplicate(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store notification from $pkg", e)
            }
        }
    }

    override fun onListenerDisconnected() {
        // Request system to rebind — critical for OEM devices that kill background services
        requestRebind(ComponentName(this, KeelNotificationListener::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "KeelNLS"
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.notification

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.keel.datastore.FrequencyCapStore
import com.keel.model.Insight
import com.keel.model.Severity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts [NotificationCompat] alerts for insights surfaced by [GuardrailEngine] or the
 * ReAct agent.
 *
 * Priority rules:
 *  - [Severity.CRITICAL] — always posted, bypasses frequency cap.
 *  - [Severity.WARNING]  — rate-limited by [FrequencyCapStore]: max 3/day globally,
 *                          3-day cooldown per rule+merchant key.
 *  - [Severity.INFO]     — never posted as a notification (surfaced in dashboard only).
 *
 * The notification channel ("keel_insights") is created in [KeelApplication.onCreate]
 * before any notification is posted.
 */
@Singleton
class KeelNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val freqCapStore: FrequencyCapStore,
) {
    private val notifManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Posts a notification for [insight] if policy allows it.
     *
     * @param insight Insight to notify about. [Insight.id] should be non-zero (set after DB insert).
     * @param ruleKey Frequency cap key, format "{ruleId}:{merchantOrAccount}".
     *                Ignored for CRITICAL severity.
     */
    suspend fun notifyIfAllowed(insight: Insight, ruleKey: String) {
        if (!hasNotificationPermission()) return
        if (insight.severity == Severity.INFO) return

        if (insight.severity == Severity.WARNING) {
            if (!freqCapStore.canSend(ruleKey)) return
        }

        post(insight)

        if (insight.severity == Severity.WARNING) {
            freqCapStore.recordSent(ruleKey)
        }
    }

    /**
     * Posts a batch of insights, using the insight title as the rule key.
     * Convenience overload for [GuardrailEngine] results where per-rule keys aren't
     * known at the call site — uses "[severity]:[title]" as the cap key.
     */
    suspend fun notifyBatch(insights: List<Insight>) {
        for (insight in insights) {
            val key = "${insight.severity.name.lowercase()}:${insight.title}"
            notifyIfAllowed(insight, key)
        }
    }

    private fun post(insight: Insight) {
        val priority = when (insight.severity) {
            Severity.CRITICAL -> NotificationCompat.PRIORITY_HIGH
            Severity.WARNING  -> NotificationCompat.PRIORITY_DEFAULT
            Severity.INFO     -> NotificationCompat.PRIORITY_LOW
        }

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val pendingIntent = if (launchIntent != null) {
            android.app.PendingIntent.getActivity(
                context,
                insight.id.toInt(),
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        } else null

        val notification = NotificationCompat.Builder(context, CHANNEL_INSIGHTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(insight.title)
            .setContentText(insight.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(insight.body))
            .setPriority(priority)
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        // Use insight.id as notification ID so each insight replaces its own slot
        notifManager.notify(insight.id.toInt(), notification)
    }

    private fun hasNotificationPermission(): Boolean {
        // POST_NOTIFICATIONS requires runtime grant only on API 33+
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        const val CHANNEL_INSIGHTS = "keel_insights"
    }
}

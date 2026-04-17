// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ingestion

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks all permissions Keel needs and reports their status as a single snapshot.
 * UI observes AllPermissions to drive the onboarding flow and permission banners.
 */
@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun checkAll(): AllPermissions {
        return AllPermissions(
            nlsEnabled = isNlsEnabled(),
            smsGranted = isSmsGranted(),
            batteryOptimisationIgnored = isBatteryOptimisationIgnored(),
            notificationsGranted = isNotificationsGranted(),
        )
    }

    /**
     * Checks if Notification Listener Service access is granted.
     *
     * On Android 13+ (API 33), NLS access is in "Restricted settings" — the user must
     * explicitly allow it via Settings → Special app access → Notification access.
     * The Settings.Secure string used has changed across API levels; we check both.
     */
    fun isNlsEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val cn = ComponentName(context, KeelNotificationListener::class.java)
        return flat.split(":").any { component ->
            runCatching { ComponentName.unflattenFromString(component) == cn }.getOrDefault(false)
        }
    }

    fun isSmsGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun isBatteryOptimisationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isNotificationsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // POST_NOTIFICATIONS only required on API 33+
        }
    }
}

data class AllPermissions(
    val nlsEnabled: Boolean,
    val smsGranted: Boolean,
    val batteryOptimisationIgnored: Boolean,
    val notificationsGranted: Boolean,
) {
    val allGranted: Boolean
        get() = nlsEnabled && smsGranted && batteryOptimisationIgnored && notificationsGranted
}

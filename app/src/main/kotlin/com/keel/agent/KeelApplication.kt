// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.keel.agent.worker.AgentWorker
import com.keel.agent.worker.MemoryConsolidationWorker
import com.keel.agent.worker.SmsBackfillWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class KeelApplication : Application(), androidx.work.Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        enqueueSmsBackfill()
        enqueuePeriodicWorkers()
    }

    /**
     * Creates the 'keel_insights' notification channel for financial nudges.
     * Creating a channel that already exists is a no-op — safe to call every launch.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_INSIGHTS,
                "Financial Insights",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Keel financial nudges and alerts"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /**
     * Enqueue the one-time SMS inbox backfill.
     * KEEP_EXISTING policy: if it's already queued or running, do nothing.
     * The worker itself checks BackfillStore.isSmsBackfillCompleted() before doing any work.
     */
    private fun enqueueSmsBackfill() {
        val request = OneTimeWorkRequestBuilder<SmsBackfillWorker>()
            .addTag(SmsBackfillWorker.TAG)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            SmsBackfillWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedules:
     *  - [AgentWorker] — every 6 hours, BATTERY_NOT_LOW. Tagged "agent_worker" so
     *    [KeelViewModel] can observe its [WorkInfo.State] for the AgentStatus indicator.
     *  - [MemoryConsolidationWorker] — every 24 hours. Prunes and compresses old RECALL
     *    entries into CORE memory.
     *
     * Both use KEEP policy: if already scheduled from a previous launch, do nothing.
     * WorkManager persists periodic requests across reboots.
     */
    private fun enqueuePeriodicWorkers() {
        val wm = WorkManager.getInstance(this)

        // Agent periodic review — every 6 hours, battery must not be low
        val agentConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val agentRequest = PeriodicWorkRequestBuilder<AgentWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(agentConstraints)
            .addTag(AgentWorker.TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(
            AgentWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            agentRequest,
        )

        // Memory consolidation — once per day, no special constraints
        val consolidationRequest = PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .addTag(MemoryConsolidationWorker.TAG)
            .build()

        wm.enqueueUniquePeriodicWork(
            MemoryConsolidationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            consolidationRequest,
        )
    }

    companion object {
        const val CHANNEL_INSIGHTS = "keel_insights"
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.keel.datastore.BackfillStore
import com.keel.ingestion.SmsInboxBackfill
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that runs the SMS inbox backfill exactly once.
 *
 * Enqueued from KeelApplication.onCreate() via AppEntryPoint.
 * The BackfillStore flag prevents it from running more than once.
 * Requires READ_SMS permission — if not granted, returns success (will retry when
 * the user grants permission and the app restarts).
 */
@HiltWorker
class SmsBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backfillStore: BackfillStore,
    private val smsInboxBackfill: SmsInboxBackfill,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (backfillStore.isSmsBackfillCompleted()) {
            Log.d(TAG, "Backfill already done — skipping")
            return Result.success()
        }

        Log.d(TAG, "Starting SMS inbox backfill")
        val count = smsInboxBackfill.run()
        backfillStore.markSmsBackfillCompleted()
        Log.d(TAG, "Backfill complete: $count messages")
        return Result.success()
    }

    companion object {
        const val TAG = "SmsBackfillWorker"
        const val WORK_NAME = "keel_sms_backfill"
    }
}

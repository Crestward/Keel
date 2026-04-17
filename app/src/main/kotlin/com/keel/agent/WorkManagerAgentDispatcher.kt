// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.keel.agent.worker.AgentWorker
import com.keel.model.AgentDispatcher
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-backed implementation of [AgentDispatcher].
 *
 * Generates a [runId] before enqueueing so both this side (caller) and the
 * [AgentWorker] side (executor) share the same ID. This lets [KeelViewModel]
 * call [InsightRepository.getByRunId] immediately after the run finishes
 * without needing a Room auto-ID round-trip.
 *
 * The request is expedited so it starts within seconds on most devices.
 * [OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST] is the fallback for
 * devices where expedited quota is exhausted.
 */
@Singleton
class WorkManagerAgentDispatcher @Inject constructor(
    private val workManager: WorkManager,
) : AgentDispatcher {

    override suspend fun dispatchUserQuery(text: String): Long {
        val runId = System.currentTimeMillis()

        val request = OneTimeWorkRequestBuilder<AgentWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    AgentWorker.KEY_TRIGGER to AgentWorker.TRIGGER_USER_QUERY,
                    AgentWorker.KEY_QUERY to text,
                    AgentWorker.KEY_RUN_ID to runId,
                )
            )
            .addTag(AgentWorker.TAG)
            .build()

        workManager.enqueue(request)

        // Suspend until the work finishes (succeeded, failed, or cancelled).
        // AgentWorker echoes the runId back in output data on success.
        val workInfo = workManager.getWorkInfoByIdFlow(request.id)
            .filterNotNull()
            .filter { it.state.isFinished }
            .first()

        // Return the runId from output data if available, or fall back to the
        // pre-generated one (they are the same unless AgentWorker overrode it).
        return workInfo.outputData.getLong(AgentWorker.KEY_RUN_ID, runId)
    }
}

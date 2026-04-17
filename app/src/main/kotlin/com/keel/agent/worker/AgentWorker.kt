// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.keel.agent.ReActLoop
import com.keel.database.repository.AgentRunRepository
import com.keel.llm.InsufficientMemoryException
import com.keel.llm.LLMBackend
import com.keel.llm.ThermalThrottleException
import com.keel.model.AgentTrigger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic and event-driven ReAct agent runner.
 *
 * **Triggers:**
 *  - Scheduled 6-hour periodic run → [AgentTrigger.PeriodicReview]
 *  - New transaction batch from [ParserWorker] → [AgentTrigger.NewTransactions]
 *  - User chat query from [KeelViewModel] → [AgentTrigger.UserQuery]
 *
 * The trigger type and optional data are passed via [WorkManager] input data:
 *  - `"trigger"` key: "periodic" | "new_transactions" | "user_query"
 *  - `"count"` key: Int (for NewTransactions trigger)
 *  - `"query"` key: String (for UserQuery trigger)
 *
 * **Engine lifecycle:** [OnDeviceBackend] is @Singleton — the engine stays loaded between
 * WorkManager runs. First initialization takes ~10s; subsequent runs reuse the loaded engine.
 *
 * **Error handling:**
 *  - [ThermalThrottleException] → [Result.retry] (WorkManager retries with 30min backoff)
 *  - [InsufficientMemoryException] → [Result.retry]
 *  - Any [AgentRun] (including failed ones) is persisted to [AgentRunRepository]
 *  - If model is not available (not downloaded), returns [Result.success] silently
 *
 * Tagged with [TAG] so [KeelViewModel] can observe its [WorkInfo.State] for the
 * [AgentStatus.THINKING] indicator in the dashboard.
 */
@HiltWorker
class AgentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backend: LLMBackend,
    private val reactLoop: ReActLoop,
    private val agentRunRepository: AgentRunRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Skip silently if model is not downloaded yet
        if (!backend.isAvailable) return Result.success()

        val trigger = parseTrigger()

        // Use preset runId for user-query correlation (KeelViewModel pre-generates it so it can
        // call InsightRepository.getByRunId() after the run completes). For periodic/event runs
        // no preset is provided and we fall back to current time.
        val runId = inputData.getLong(KEY_RUN_ID, System.currentTimeMillis())

        return try {
            val agentRun = reactLoop.run(trigger, runId)
            agentRunRepository.insert(agentRun)
            // Return runId in output data so WorkManagerAgentDispatcher can read it back.
            Result.success(workDataOf(KEY_RUN_ID to agentRun.id))
        } catch (e: ThermalThrottleException) {
            // Device is too hot — let WorkManager retry after its backoff interval
            Result.retry()
        } catch (e: InsufficientMemoryException) {
            // Not enough RAM — retry; system may have freed memory by next run
            Result.retry()
        } catch (e: Exception) {
            // Unexpected failure — store a minimal failed run record so the debug screen
            // shows something, then return failure so WorkManager doesn't keep retrying.
            runCatching {
                agentRunRepository.insert(
                    com.keel.model.AgentRun(
                        id = System.currentTimeMillis(),
                        triggeredBy = trigger,
                        iterationCount = 0,
                        toolCallsJson = "[]",
                        insightsCreated = emptyList(),
                        durationMs = 0,
                        terminationReason = "ERROR: ${e.message?.take(100)}",
                        timestamp = System.currentTimeMillis(),
                    )
                )
            }
            Result.failure()
        }
    }

    private fun parseTrigger(): AgentTrigger {
        return when (inputData.getString(KEY_TRIGGER)) {
            TRIGGER_NEW_TRANSACTIONS -> AgentTrigger.NewTransactions(
                count = inputData.getInt(KEY_COUNT, 1)
            )
            TRIGGER_USER_QUERY -> AgentTrigger.UserQuery(
                text = inputData.getString(KEY_QUERY) ?: ""
            )
            else -> AgentTrigger.PeriodicReview
        }
    }

    companion object {
        const val TAG = "agent_worker"
        const val WORK_NAME = "com.keel.agent.worker.AgentWorker"

        const val KEY_TRIGGER = "trigger"
        const val KEY_COUNT = "count"
        const val KEY_QUERY = "query"
        /** Pre-assigned run ID for user-query correlation. Also returned in output data. */
        const val KEY_RUN_ID = "run_id"

        const val TRIGGER_PERIODIC = "periodic"
        const val TRIGGER_NEW_TRANSACTIONS = "new_transactions"
        const val TRIGGER_USER_QUERY = "user_query"
    }
}

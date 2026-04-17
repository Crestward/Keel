// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui

import com.keel.llm.BackendStatus
import com.keel.model.Insight
import com.keel.model.Transaction

/**
 * Immutable snapshot of everything the dashboard / chat screen needs to render.
 *
 * Produced exclusively by [KeelViewModel] and observed as a [StateFlow].
 * The UI must never hold its own mutable state for content that belongs here.
 */
data class KeelState(
    /** Recent transactions, newest-first. Empty until first load completes. */
    val transactions: List<Transaction> = emptyList(),

    /** Active (non-dismissed, non-snoozed) insights from periodic agent reviews. */
    val insights: List<Insight> = emptyList(),

    /**
     * Insights created by the agent in direct response to a user chat query.
     * Cleared when a new query is submitted. Shown separately from [insights]
     * so the user can see exactly what the agent found for their question.
     */
    val chatInsights: List<Insight> = emptyList(),

    /** Whether the ReAct agent is currently running. Drives the progress indicator. */
    val agentStatus: AgentStatus = AgentStatus.IDLE,

    /**
     * Current LLM model availability. Used to decide whether to show the
     * model-download prompt or the full agent UI.
     */
    val backendStatus: BackendStatus = BackendStatus.ModelNotDownloaded,

    /** True while any async data load is in progress. */
    val isLoading: Boolean = false,

    /** Non-null when a recoverable error occurred that should be shown to the user. */
    val error: String? = null,
)

/**
 * Reflects the current execution state of [AgentWorker].
 *
 * Derived from WorkManager [WorkInfo.State] — updated in real-time via
 * `workManager.getWorkInfosByTagFlow(AgentWorker.TAG)`.
 */
enum class AgentStatus {
    /** No agent run in flight. Normal dashboard state. */
    IDLE,

    /** Agent is actively running a ReAct loop. Show progress indicator. */
    THINKING,

    /** Last agent run failed. Show error badge on the agent indicator. */
    ERROR,
}

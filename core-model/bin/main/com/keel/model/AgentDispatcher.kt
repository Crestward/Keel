// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.model

/**
 * Abstraction over WorkManager for dispatching agent runs from the UI layer.
 *
 * Defined in core-model so [feature-ui] can inject it without taking a direct
 * dependency on the [app] module or [feature-agent] — which would violate the
 * no-inter-feature-dep rule enforced by the module graph.
 *
 * The [app] module provides the WorkManager-backed implementation via
 * WorkManagerAgentDispatcher bound in AppModule.
 */
interface AgentDispatcher {

    /**
     * Enqueues an expedited AgentWorker for a user chat query and suspends
     * until the run completes (succeeded or failed).
     *
     * @return the AgentRun.id generated for this run, or -1L if the run failed.
     *         Pass this to InsightRepository.getByRunId() to fetch insights
     *         created in response to the user's question.
     */
    suspend fun dispatchUserQuery(text: String): Long
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.model

import kotlinx.serialization.Serializable

/**
 * A financial insight surfaced either by the ReAct agent or a safety guardrail.
 */
@Serializable
data class Insight(
    val id: Long = 0,
    val title: String,
    val body: String,
    val severity: Severity,
    /** True when created by the agent's create_insight tool; false for guardrail-generated insights */
    val agentGenerated: Boolean,
    /**
     * Links to the AgentRun that created this insight.
     * Chat screen uses this to correlate responses to a specific user query.
     * Null for guardrail-generated insights.
     */
    val agentRunId: Long? = null,
    val dismissed: Boolean = false,
    /** Epoch millis when the user snoozed this, null if not snoozed */
    val snoozedUntil: Long? = null,
    val createdAt: Long,
)

@Serializable
enum class Severity { INFO, WARNING, CRITICAL }

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.model

import kotlinx.serialization.Serializable

// ─── Agent Run ────────────────────────────────────────────────────────────────

/**
 * Audit record for one complete ReAct loop execution.
 * Stored in DB for the debug screen and for correlating chat responses.
 */
@Serializable
data class AgentRun(
    val id: Long = 0,
    val triggeredBy: AgentTrigger,
    val iterationCount: Int,
    /** JSON-encoded List<ToolCallRecord> — full tool call history for this run */
    val toolCallsJson: String,
    /** IDs of Insight rows created by this run's create_insight tool calls */
    val insightsCreated: List<Long>,
    val durationMs: Long,
    /** "DONE" | "MAX_ITERATIONS" | "PARSE_ERROR" | "THERMAL_THROTTLE" | "OOM" */
    val terminationReason: String,
    val timestamp: Long,
)

// ─── Agent Trigger ────────────────────────────────────────────────────────────

/** What started this agent run */
@Serializable
sealed class AgentTrigger {
    /** Scheduled 6-hour periodic review */
    @Serializable
    data object PeriodicReview : AgentTrigger()

    /** New transaction batch arrived from ParserWorker */
    @Serializable
    data class NewTransactions(val count: Int) : AgentTrigger()

    /** User typed a message in the chat screen */
    @Serializable
    data class UserQuery(val text: String) : AgentTrigger()
}

// ─── Tool Call ────────────────────────────────────────────────────────────────

@Serializable
data class ToolCall(
    val name: String,
    val argsJson: String,
)

@Serializable
sealed class ToolResult {
    @Serializable
    data class Success(val toolName: String, val resultJson: String) : ToolResult()

    @Serializable
    data class Error(val toolName: String, val message: String) : ToolResult()
}

@Serializable
data class ToolCallRecord(
    val call: ToolCall,
    val result: ToolResult,
)

// ─── Parse Result ─────────────────────────────────────────────────────────────

/** Result of attempting to parse a RawEvent with regex */
sealed class ParseResult {
    data class Success(val transaction: Transaction) : ParseResult()
    /** Regex couldn't parse this — route to LlmParserWorker */
    data class NeedsLLM(val rawText: String) : ParseResult()
    data class Failure(val reason: String) : ParseResult()
}

// ─── Insert Result ────────────────────────────────────────────────────────────

/**
 * Result of TransactionRepository.insertOrMerge() or RawEventRepository.insertIfNotDuplicate().
 * Drives the three-level dedup strategy (F11/F12).
 */
sealed class InsertResult {
    /** New row written to DB */
    data class Inserted(val id: Long) : InsertResult()
    /** Existing row updated to Source.BOTH — SMS and notification were the same transaction */
    data class Merged(val existingId: Long) : InsertResult()
    /** Exact duplicate — not inserted or merged */
    data class Duplicate(val existingId: Long) : InsertResult()
}

// ─── Download Progress ────────────────────────────────────────────────────────

/** Gemma model download progress for the onboarding screen */
@Serializable
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    val fraction: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    val isComplete: Boolean get() = bytesDownloaded >= totalBytes && totalBytes > 0
}

// ─── Permission Status ────────────────────────────────────────────────────────

sealed class PermissionStatus {
    data object Granted : PermissionStatus()
    data object Denied : PermissionStatus()
    data object NeverAsked : PermissionStatus()
}

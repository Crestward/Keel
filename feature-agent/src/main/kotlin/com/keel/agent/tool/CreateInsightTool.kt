// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.tool

import com.keel.agent.notification.KeelNotificationManager
import com.keel.database.repository.InsightRepository
import com.keel.model.Insight
import com.keel.model.Severity
import com.keel.model.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool: create_insight
 * Args: title (string), body (string), severity ("INFO" | "WARNING" | "CRITICAL")
 * Returns: {"insight_id": Long}
 * Side effects:
 *  - Inserts an Insight row tagged with [agentRunId]
 *  - Posts a notification via [KeelNotificationManager] (CRITICAL always, WARNING rate-limited)
 *
 * [agentRunId] must be set before [execute] is called — use [setRunId].
 * This is the primary way the agent surfaces findings to the user.
 */
@Singleton
class CreateInsightTool @Inject constructor(
    private val insightRepository: InsightRepository,
    private val notificationManager: KeelNotificationManager,
) : AgentTool {

    override val name = "create_insight"
    override val descriptionForPrompt =
        "create_insight(title,body,severity) — severity: INFO/WARNING/CRITICAL, creates a user-visible insight card"

    @Volatile
    private var agentRunId: Long = 0L

    /** Called by [ReActLoop] at the start of each run before any tool calls. */
    fun setRunId(runId: Long) {
        agentRunId = runId
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun execute(argsJson: String): ToolResult {
        return runCatching {
            val obj = jsonParser.parseToJsonElement(argsJson) as JsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error(name, "Missing required arg: title")
            val body = obj["body"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error(name, "Missing required arg: body")
            val severityStr = obj["severity"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error(name, "Missing required arg: severity")
            val severity = runCatching { Severity.valueOf(severityStr) }.getOrElse {
                return ToolResult.Error(name, "Invalid severity: $severityStr. Use INFO/WARNING/CRITICAL")
            }

            val insight = Insight(
                title = title,
                body = body,
                severity = severity,
                agentGenerated = true,
                agentRunId = agentRunId,
                createdAt = System.currentTimeMillis(),
            )
            val id = insightRepository.insert(insight)
            val insertedInsight = insight.copy(id = id)

            notificationManager.notifyIfAllowed(
                insight = insertedInsight,
                ruleKey = "agent:$id",
            )

            ToolResult.Success(name, """{"insight_id":$id}""")
        }.getOrElse { e ->
            ToolResult.Error(name, "create_insight failed: ${e.message}")
        }
    }
}

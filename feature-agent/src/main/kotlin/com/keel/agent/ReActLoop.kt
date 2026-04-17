// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent

import com.keel.agent.tool.ToolRegistry
import com.keel.database.repository.AgentMemoryRepository
import com.keel.llm.LLMBackend
import com.keel.llm.extractJson
import com.keel.model.AgentRun
import com.keel.model.AgentTrigger
import com.keel.model.ToolCall
import com.keel.model.ToolCallRecord
import com.keel.model.ToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The core ReAct (Reason + Act) agent loop.
 *
 * Each call to [run] is one complete agent turn:
 *  1. Build system instruction (once, via [ContextBuilder])
 *  2. Create a fresh [Conversation] from the [Engine] singleton
 *  3. Send trigger message as first user turn
 *  4. Loop (max [MAX_ITERATIONS]):
 *     a. Collect full streaming response into a string
 *     b. Extract JSON via [extractJson] (model may wrap JSON in prose)
 *     c. Parse [AgentResponse] (Done / ToolCall / ParseError)
 *     d. On Done → break; On ToolCall → execute → send result as next user message
 *     e. On ParseError (iteration 0 only) → retry with JSON reminder prepended
 *  5. Write a RECALL memory entry summarising this run
 *  6. Return [AgentRun] for DB persistence by [AgentWorker]
 *
 * **agentRunId:** generated at the top of [run] as `System.currentTimeMillis()`.
 * This ID is passed to [CreateInsightTool] before the loop so insights are tagged
 * with the correct run ID before the run completes (no chicken-and-egg with Room auto-IDs).
 */
@Singleton
class ReActLoop @Inject constructor(
    private val backend: LLMBackend,
    private val contextBuilder: ContextBuilder,
    private val toolRegistry: ToolRegistry,
    private val memoryRepository: AgentMemoryRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * @param runId Pre-assigned run ID. Defaults to current time. Pass an explicit value
     *   when the caller (e.g. AgentWorker dispatched by KeelViewModel) needs to correlate
     *   insights with a specific run before the [AgentRun] is persisted to the DB.
     */
    suspend fun run(trigger: AgentTrigger, runId: Long = System.currentTimeMillis()): AgentRun {
        val startMs = System.currentTimeMillis()
        val history = mutableListOf<ToolCallRecord>()
        val insightsCreated = mutableListOf<Long>()
        var terminationReason: String? = null

        // Give CreateInsightTool the runId so created insights are tagged correctly
        toolRegistry.createInsight.setRunId(runId)

        val systemInstruction = contextBuilder.buildSystemInstruction(trigger)
        var iteration = 0
        var nextUserMessage = contextBuilder.buildTriggerMessage(trigger)

        backend.createSession(systemInstruction).use { session ->
            while (iteration < MAX_ITERATIONS) {
                val rawResponse = session.sendMessage(nextUserMessage)

                val extracted = extractJson(rawResponse)
                val agentResponse = extracted
                    ?.let { parseAgentResponse(it) }
                    ?: AgentResponse.ParseError(rawResponse)

                when (agentResponse) {
                    is AgentResponse.Done -> {
                        // Count the Done turn as an iteration so iterationCount reflects
                        // the total number of model calls (including the final one).
                        iteration++
                        terminationReason = "DONE"
                        break
                    }
                    is AgentResponse.ToolCall -> {
                        val result = toolRegistry.execute(agentResponse.tool, agentResponse.argsJson)
                        history.add(ToolCallRecord(ToolCall(agentResponse.tool, agentResponse.argsJson), result))

                        // Track insight IDs for the AgentRun record
                        if (agentResponse.tool == "create_insight") {
                            extractInsightId(result)?.let { insightsCreated.add(it) }
                        }

                        val resultText = when (result) {
                            is ToolResult.Success -> result.resultJson
                            is ToolResult.Error -> """{"error":"${result.message}"}"""
                        }
                        nextUserMessage = contextBuilder.buildToolResultMessage(agentResponse.tool, resultText)
                    }
                    is AgentResponse.ParseError -> {
                        if (iteration == 0) {
                            // Retry once with explicit JSON reminder
                            nextUserMessage = "REMINDER: respond with ONLY a JSON object. ${nextUserMessage}"
                        } else {
                            terminationReason = "PARSE_ERROR"
                            break
                        }
                    }
                }
                iteration++
            }
        }

        if (terminationReason == null) terminationReason = "MAX_ITERATIONS"

        // Write this run as a RECALL memory entry for future context
        memoryRepository.addRecallMemory(
            key = "run_$runId",
            content = history.summarise(trigger),
            expiresAt = System.currentTimeMillis() + RECALL_TTL_MS,
        )

        return AgentRun(
            id = runId,
            triggeredBy = trigger,
            iterationCount = iteration,
            toolCallsJson = json.encodeToString(history),
            insightsCreated = insightsCreated,
            durationMs = System.currentTimeMillis() - startMs,
            terminationReason = terminationReason,
            timestamp = System.currentTimeMillis(),
        )
    }

    // ─── Response parsing ─────────────────────────────────────────────────────

    private fun parseAgentResponse(jsonStr: String): AgentResponse {
        return runCatching {
            val obj = Json.parseToJsonElement(jsonStr).jsonObject
            when {
                obj["done"]?.jsonPrimitive?.booleanOrNull == true -> AgentResponse.Done
                obj["tool"]?.jsonPrimitive?.contentOrNull != null -> {
                    val tool = obj["tool"]!!.jsonPrimitive.content
                    val args = obj["args"]?.let {
                        // Encode args back to compact JSON for the tool
                        Json.encodeToString(it)
                    } ?: "{}"
                    AgentResponse.ToolCall(tool, args)
                }
                else -> AgentResponse.ParseError(jsonStr)
            }
        }.getOrElse { AgentResponse.ParseError(jsonStr) }
    }

    private fun extractInsightId(result: ToolResult): Long? {
        if (result !is ToolResult.Success) return null
        return runCatching {
            Json.parseToJsonElement(result.resultJson)
                .jsonObject["insight_id"]?.jsonPrimitive?.contentOrNull?.toLong()
        }.getOrNull()
    }

    private fun List<ToolCallRecord>.summarise(trigger: AgentTrigger): String {
        val triggerDesc = when (trigger) {
            is AgentTrigger.PeriodicReview -> "periodic review"
            is AgentTrigger.NewTransactions -> "${trigger.count} new transactions"
            is AgentTrigger.UserQuery -> "user query: ${trigger.text.take(50)}"
        }
        return if (isEmpty()) {
            "Agent run ($triggerDesc): no tool calls made."
        } else {
            val calls = joinToString("; ") { record ->
                val result = when (record.result) {
                    is ToolResult.Success -> "ok"
                    is ToolResult.Error -> "error"
                }
                "${record.call.name}→$result"
            }
            "Agent run ($triggerDesc): $calls"
        }
    }

    companion object {
        const val MAX_ITERATIONS = 5
        private const val RECALL_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

// ─── Agent response types ─────────────────────────────────────────────────────

sealed class AgentResponse {
    /** Model decided it has finished — {"thought":"...","done":true} */
    data object Done : AgentResponse()

    /** Model wants to call a tool — {"thought":"...","tool":"name","args":{...}} */
    data class ToolCall(val tool: String, val argsJson: String) : AgentResponse()

    /** JSON extraction or parsing failed */
    data class ParseError(val raw: String) : AgentResponse()
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.tool

import com.keel.agent.embedding.EmbeddingEngine
import com.keel.database.repository.AgentMemoryRepository
import com.keel.model.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool: search_memory
 * Args: query (string)
 * Returns: top 5 archival memory matches [{content, similarity}]
 * Side effect: increments accessCount on matched entries
 */
@Singleton
class SearchMemoryTool @Inject constructor(
    private val memoryRepository: AgentMemoryRepository,
    private val embeddingEngine: EmbeddingEngine,
) : AgentTool {

    override val name = "search_memory"
    override val descriptionForPrompt =
        "search_memory(query) — returns top 5 archival memory matches relevant to query"

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun execute(argsJson: String): ToolResult {
        return runCatching {
            val obj = jsonParser.parseToJsonElement(argsJson) as JsonObject
            val query = obj["query"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error(name, "Missing required arg: query")

            val queryEmbedding = embeddingEngine.embed(query)

            // If EmbeddingEngine has no model loaded, return empty gracefully
            if (queryEmbedding.isEmpty()) {
                return ToolResult.Success(name, "[]")
            }

            val results = memoryRepository.archivalSearch(
                queryEmbedding = queryEmbedding,
                threshold = 0.3f,
                limit = 5,
            )

            val json = buildString {
                append("[")
                results.forEachIndexed { index, memory ->
                    if (index > 0) append(",")
                    val escapedContent = memory.content
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                    append("""{"content":"$escapedContent"}""")
                }
                append("]")
            }
            ToolResult.Success(name, json)
        }.getOrElse { e ->
            ToolResult.Error(name, "search_memory failed: ${e.message}")
        }
    }
}

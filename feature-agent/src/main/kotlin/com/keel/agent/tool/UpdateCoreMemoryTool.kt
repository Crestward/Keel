// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.tool

import com.keel.database.repository.AgentMemoryRepository
import com.keel.model.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool: update_core_memory
 * Args: key (string), value (string)
 * Returns: {"ok":true}
 * Side effect: upserts a CORE memory row — persists permanently across runs
 */
@Singleton
class UpdateCoreMemoryTool @Inject constructor(
    private val memoryRepository: AgentMemoryRepository,
) : AgentTool {

    override val name = "update_core_memory"
    override val descriptionForPrompt =
        "update_core_memory(key,value) — persists a permanent fact about the user (e.g. payday, income)"

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun execute(argsJson: String): ToolResult {
        return runCatching {
            val obj = jsonParser.parseToJsonElement(argsJson) as JsonObject
            val key = obj["key"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error(name, "Missing required arg: key")
            val value = obj["value"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error(name, "Missing required arg: value")

            memoryRepository.updateCoreMemory(key, value)
            ToolResult.Success(name, """{"ok":true}""")
        }.getOrElse { e ->
            ToolResult.Error(name, "update_core_memory failed: ${e.message}")
        }
    }
}

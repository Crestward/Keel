// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.tool

import com.keel.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of all [AgentTool] implementations available to the ReAct loop.
 *
 * Injected into [ReActLoop] and [ContextBuilder]. Tools are registered by name —
 * the LLM uses the [AgentTool.name] string in its JSON response to call them.
 *
 * Adding a new tool: implement [AgentTool], annotate with @Singleton + @Inject,
 * add it to the constructor here, and it will automatically appear in the prompt.
 */
@Singleton
class ToolRegistry @Inject constructor(
    queryTransactions: QueryTransactionsTool,
    getSpendingSummary: GetSpendingSummaryTool,
    searchMemory: SearchMemoryTool,
    updateCoreMemory: UpdateCoreMemoryTool,
    val createInsight: CreateInsightTool,
    getAccountSummary: GetAccountSummaryTool,
) {
    private val tools: Map<String, AgentTool> = listOf(
        queryTransactions,
        getSpendingSummary,
        searchMemory,
        updateCoreMemory,
        createInsight,
        getAccountSummary,
    ).associateBy { it.name }

    /** All tools, for inclusion in the system instruction prompt. */
    val all: List<AgentTool> get() = tools.values.toList()

    /**
     * Executes the named tool with the given JSON arguments.
     * Returns [ToolResult.Error] if the tool name is unknown — never throws.
     */
    suspend fun execute(toolName: String, argsJson: String): ToolResult {
        val tool = tools[toolName]
            ?: return ToolResult.Error(toolName, "Unknown tool: $toolName")
        return tool.execute(argsJson)
    }
}

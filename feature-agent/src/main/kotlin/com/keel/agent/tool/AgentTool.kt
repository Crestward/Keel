// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.tool

import com.keel.model.ToolResult

/**
 * Base contract for all ReAct agent tools.
 *
 * Each tool is a @Singleton Hilt-injectable object with:
 *  - [name]                 — must exactly match the string the LLM uses in its JSON call
 *  - [descriptionForPrompt] — one compact line included in the system instruction tool list
 *  - [execute]              — parses [argsJson], runs the side-effecting or read-only
 *                             operation, and returns a [ToolResult]
 *
 * All tools enforce a hard result-size cap of 20 rows to prevent context overflow.
 */
interface AgentTool {
    val name: String
    val descriptionForPrompt: String
    suspend fun execute(argsJson: String): ToolResult
}

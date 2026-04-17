// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent

import com.keel.agent.tool.ToolRegistry
import com.keel.database.repository.AgentMemoryRepository
import com.keel.database.repository.TransactionRepository
import com.keel.model.AgentTrigger
import com.keel.model.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the system instruction (set once per run) and per-iteration user messages
 * sent to the LLM via [ReActLoop].
 *
 * **Token budget:** target ≤ 800 tokens for the system instruction.
 * Estimated using [text.length / 4] (standard English approximation for LiteRT-LM,
 * which has no public token counting API).
 *
 * Progressive truncation when over budget:
 *  1. Drop rawText from transactions (keep amount+merchant+category only)
 *  2. Reduce transaction count from 10 → 5
 *  3. Truncate CORE memory to top 5 most-accessed keys
 */
@Singleton
class ContextBuilder @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val memoryRepository: AgentMemoryRepository,
    private val toolRegistry: ToolRegistry,
) {
    private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Builds the system instruction placed in [ConversationConfig.systemInstruction].
     * Called once per agent run — this is the "world state" the LLM reasons from.
     */
    suspend fun buildSystemInstruction(trigger: AgentTrigger): String {
        val today = fullDateFormat.format(Date())
        val coreMemory = memoryRepository.getCoreMemory()
        val recentTransactions = transactionRepository.getRecent(10)

        val toolList = toolRegistry.all.joinToString("\n") { "  ${it.descriptionForPrompt}" }

        // Build with full detail first, then truncate if over budget
        var instruction = buildFullInstruction(today, coreMemory, recentTransactions, toolList)

        // Progressive truncation if over ~800 tokens (~3200 chars)
        if (instruction.length > TOKEN_BUDGET_CHARS) {
            // Step 1: strip rawText from transactions (already using compact format, skip)
            // Step 2: reduce to 5 transactions
            val fiveTransactions = recentTransactions.take(5)
            instruction = buildFullInstruction(today, coreMemory, fiveTransactions, toolList)
        }

        if (instruction.length > TOKEN_BUDGET_CHARS) {
            // Step 3: truncate CORE memory to top 5 entries
            val truncatedCore = coreMemory.entries.take(5).associate { it.key to it.value }
            val fiveTransactions = recentTransactions.take(5)
            instruction = buildFullInstruction(today, truncatedCore, fiveTransactions, toolList)
        }

        return instruction
    }

    /**
     * Builds the first user message for the run (iteration 0).
     * Describes the trigger that caused the agent to wake up.
     */
    fun buildTriggerMessage(trigger: AgentTrigger): String = when (trigger) {
        is AgentTrigger.PeriodicReview ->
            "TASK: Perform your periodic financial review. Analyse spending patterns, " +
                    "check for anomalies, and surface any useful insights for the user."
        is AgentTrigger.NewTransactions ->
            "TASK: ${trigger.count} new transaction${if (trigger.count == 1) "" else "s"} just arrived. " +
                    "Review them and surface any insights worth notifying the user about."
        is AgentTrigger.UserQuery ->
            "TASK: The user asked: \"${trigger.text}\". Answer by using tools to look up " +
                    "their financial data, then create an insight with your finding."
    }

    /**
     * Builds the tool-result message for iterations 1+.
     * The conversation accumulates internally in LiteRT-LM, so we only need to
     * send the latest observation.
     */
    fun buildToolResultMessage(toolName: String, resultJson: String): String =
        "[TOOL_RESULT: $toolName]\n$resultJson"

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun buildFullInstruction(
        today: String,
        coreMemory: Map<String, String>,
        transactions: List<Transaction>,
        toolList: String,
    ): String = buildString {
        appendLine("You are Keel, a private financial agent on the user's Android phone.")
        appendLine("Today: $today. All amounts in Nigerian Naira (₦, 1 Naira = 100 kobo).")
        appendLine()

        if (coreMemory.isNotEmpty()) {
            appendLine("USER PROFILE:")
            coreMemory.forEach { (key, value) -> appendLine("  $key: $value") }
            appendLine()
        }

        if (transactions.isNotEmpty()) {
            appendLine("RECENT TRANSACTIONS (last ${transactions.size}):")
            transactions.forEach { tx ->
                val date = dateFormat.format(Date(tx.timestamp))
                val naira = tx.amount / 100
                val cat = if (tx.category.isNotBlank()) " [${tx.category}]" else ""
                appendLine("  $date: ${tx.type} ₦$naira ${tx.merchant}$cat")
            }
            appendLine()
        }

        appendLine("TOOLS — respond with ONLY a JSON object, nothing else:")
        appendLine(toolList)
        appendLine()
        appendLine("RESPONSE FORMAT:")
        appendLine("""To call a tool: {"thought":"...","tool":"tool_name","args":{...}}""")
        appendLine("""When finished:  {"thought":"...","done":true}""")
        append("Never output anything outside the JSON object.")
    }

    companion object {
        /** ~800 tokens × 4 chars/token — progressive truncation trigger */
        private const val TOKEN_BUDGET_CHARS = 3_200
    }
}

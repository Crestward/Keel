// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.tool

import com.keel.database.repository.TransactionRepository
import com.keel.model.ToolResult
import com.keel.model.Transaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool: query_transactions
 * Args: start_date (epoch ms), end_date (epoch ms), category? (string), merchant? (string), limit? (int, max 20)
 * Returns: JSON array of up to 20 matching transactions
 */
@Singleton
class QueryTransactionsTool @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : AgentTool {

    override val name = "query_transactions"
    override val descriptionForPrompt =
        "query_transactions(start_date,end_date,category?,merchant?,limit?) — returns up to 20 transactions"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun execute(argsJson: String): ToolResult {
        return runCatching {
            val obj = jsonParser.parseToJsonElement(argsJson) as JsonObject
            val startDate = obj["start_date"]?.jsonPrimitive?.longOrNull
                ?: return ToolResult.Error(name, "Missing required arg: start_date")
            val endDate = obj["end_date"]?.jsonPrimitive?.longOrNull
                ?: return ToolResult.Error(name, "Missing required arg: end_date")
            val category = obj["category"]?.jsonPrimitive?.contentOrNull
            val merchant = obj["merchant"]?.jsonPrimitive?.contentOrNull
            val limit = minOf(obj["limit"]?.jsonPrimitive?.intOrNull ?: 20, 20)

            val transactions = transactionRepository.query(
                from = startDate,
                to = endDate,
                category = category,
                merchant = merchant,
                limit = limit,
            )

            val resultJson = buildString {
                append("[")
                transactions.forEachIndexed { index, tx ->
                    if (index > 0) append(",")
                    append(tx.toCompactJson())
                }
                append("]")
            }
            ToolResult.Success(name, resultJson)
        }.getOrElse { e ->
            ToolResult.Error(name, "query_transactions failed: ${e.message}")
        }
    }

    private fun Transaction.toCompactJson(): String {
        val dateStr = dateFormat.format(Date(timestamp))
        val naira = amount / 100
        return """{"date":"$dateStr","amount_naira":$naira,"type":"$type","merchant":"$merchant","category":"$category"}"""
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.tool

import com.keel.database.repository.TransactionRepository
import com.keel.model.ToolResult
import com.keel.model.TransactionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool: get_spending_summary
 * Args: period ("today" | "week" | "month" | "last_30_days")
 * Returns: {income_naira, expenses_naira, net_naira, top_merchants:[{merchant,total_naira}], avg_daily_naira}
 */
@Singleton
class GetSpendingSummaryTool @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : AgentTool {

    override val name = "get_spending_summary"
    override val descriptionForPrompt =
        "get_spending_summary(period) — period: today/week/month/last_30_days, returns income/expenses/net/top_merchants"

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun execute(argsJson: String): ToolResult {
        return runCatching {
            val obj = jsonParser.parseToJsonElement(argsJson) as JsonObject
            val period = obj["period"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error(name, "Missing required arg: period")

            val (from, to) = periodToRange(period)
                ?: return ToolResult.Error(name, "Unknown period: $period. Use today/week/month/last_30_days")

            val incomeKobo = transactionRepository.sumByTypeAndRange(TransactionType.CREDIT, from, to)
            val expensesKobo = transactionRepository.sumByTypeAndRange(TransactionType.DEBIT, from, to)
            val netKobo = incomeKobo - expensesKobo

            val topMerchants = transactionRepository.query(from, to, null, null, 20)
                .filter { it.type == TransactionType.DEBIT }
                .groupBy { it.merchant }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                .entries
                .sortedByDescending { it.value }
                .take(5)

            val daysInPeriod = maxOf(1L, (to - from) / (24 * 60 * 60 * 1000L))
            val avgDailyKobo = expensesKobo / daysInPeriod

            val topMerchantsJson = topMerchants.joinToString(",") { (merchant, totalKobo) ->
                """{"merchant":"$merchant","total_naira":${totalKobo / 100}}"""
            }

            ToolResult.Success(
                name,
                """{"income_naira":${incomeKobo / 100},"expenses_naira":${expensesKobo / 100},"net_naira":${netKobo / 100},"top_merchants":[$topMerchantsJson],"avg_daily_naira":${avgDailyKobo / 100}}"""
            )
        }.getOrElse { e ->
            ToolResult.Error(name, "get_spending_summary failed: ${e.message}")
        }
    }

    private fun periodToRange(period: String): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        return when (period) {
            "today" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            "week" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            "month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            "last_30_days" -> Pair(now - 30 * 24 * 60 * 60 * 1000L, now)
            else -> null
        }
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.tool

import com.keel.database.dao.AccountDao
import com.keel.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool: get_account_summary
 * Args: none
 * Returns: {accounts:[{bank,masked_number,balance_naira}], total_balance_naira}
 */
@Singleton
class GetAccountSummaryTool @Inject constructor(
    private val accountDao: AccountDao,
) : AgentTool {

    override val name = "get_account_summary"
    override val descriptionForPrompt =
        "get_account_summary() — returns all linked bank accounts and their current balances"

    override suspend fun execute(argsJson: String): ToolResult {
        return runCatching {
            val accounts = accountDao.getAll()
            val totalKobo = accounts.sumOf { it.balanceKobo }

            val accountsJson = accounts.joinToString(",") { account ->
                val maskedSafe = account.maskedNumber
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                """{"bank":"${account.bank}","masked_number":"$maskedSafe","balance_naira":${account.balanceKobo / 100}}"""
            }
            ToolResult.Success(
                name,
                """{"accounts":[$accountsJson],"total_balance_naira":${totalKobo / 100}}"""
            )
        }.getOrElse { e ->
            ToolResult.Error(name, "get_account_summary failed: ${e.message}")
        }
    }
}

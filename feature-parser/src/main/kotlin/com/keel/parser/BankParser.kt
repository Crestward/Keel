// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.parser

import com.keel.model.ParseResult
import com.keel.model.Source
import com.keel.model.Transaction
import com.keel.model.TransactionType
import com.keel.parser.model.BankParserConfig

/**
 * Pure Kotlin parser — no Android dependencies. Takes pre-loaded configs and
 * runs regex matching. Separated from [ParserRegistry] so it can be tested
 * on the JVM without Android context.
 */
class BankParser(private val configs: List<BankParserConfig>) {

    /**
     * Attempt to parse a raw bank message into a structured [Transaction].
     *
     * Matching strategy:
     * 1. Find the config whose senderIds or packageNames match the source.
     * 2. Try each pattern in order — first regex match wins.
     * 3. Extract amount (mandatory) + balance (optional) + merchant (optional, fallback = bankId).
     * 4. Return [ParseResult.NeedsLLM] if no config matched or no pattern matched —
     *    the agent will handle it in Phase 5 via LlmParserWorker.
     */
    fun parse(
        senderAddress: String,
        senderPackage: String?,
        rawText: String,
        source: Source,
        timestamp: Long,
    ): ParseResult {
        val config = configs.firstOrNull { cfg ->
            cfg.senderIds.any { it.equals(senderAddress, ignoreCase = true) } ||
                (senderPackage != null && cfg.packageNames.contains(senderPackage))
        } ?: return ParseResult.NeedsLLM(rawText)

        for (pattern in config.patterns) {
            val regex = Regex(
                pattern.regex,
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            val match = regex.find(rawText) ?: continue

            val fieldMap = pattern.groups.associate { mapping ->
                mapping.field to match.groupValues.getOrElse(mapping.group) { "" }
            }

            val amountRaw = fieldMap["amount"]?.ifBlank { null } ?: continue
            val amount = AmountParser.parseToKobo(amountRaw) ?: continue
            if (amount <= 0L) continue

            val type = when (pattern.type.uppercase()) {
                "CREDIT" -> TransactionType.CREDIT
                "DEBIT" -> TransactionType.DEBIT
                else -> continue
            }

            val balance = fieldMap["balance"]?.ifBlank { null }?.let { AmountParser.parseToKobo(it) }
            val merchant = fieldMap["merchant"]
                ?.trim()
                ?.lowercase()
                ?.ifBlank { null }
                ?: config.bankId

            return ParseResult.Success(
                Transaction(
                    amount = amount,
                    type = type,
                    merchant = merchant,
                    balance = balance,
                    rawText = rawText,
                    source = source,
                    timestamp = timestamp,
                )
            )
        }

        return ParseResult.NeedsLLM(rawText)
    }
}

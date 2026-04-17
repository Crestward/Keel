// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.parser

import android.content.Context
import com.keel.model.ParseResult
import com.keel.model.Source
import com.keel.parser.model.BankParserConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Hilt singleton that loads all bank parser configs from assets/parsers/ at construction
// time and delegates matching to BankParser. Adding a new bank = drop a JSON file, zero code changes.
@Singleton
class ParserRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val parser: BankParser = BankParser(loadConfigs())

    fun parse(
        senderAddress: String,
        senderPackage: String? = null,
        rawText: String,
        source: Source,
        timestamp: Long,
    ): ParseResult = parser.parse(senderAddress, senderPackage, rawText, source, timestamp)

    private fun loadConfigs(): List<BankParserConfig> {
        val json = Json { ignoreUnknownKeys = true }
        return context.assets
            .list("parsers")
            ?.filter { it.endsWith(".json") }
            ?.mapNotNull { name ->
                runCatching {
                    context.assets.open("parsers/$name").use { stream ->
                        json.decodeFromString<BankParserConfig>(stream.bufferedReader().readText())
                    }
                }.getOrNull()
            }
            ?: emptyList()
    }
}

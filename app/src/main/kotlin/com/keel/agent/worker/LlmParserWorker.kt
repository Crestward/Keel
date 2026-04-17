// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.keel.database.repository.RawEventRepository
import com.keel.database.repository.TransactionRepository
import com.keel.llm.OnDeviceBackend
import com.keel.llm.extractJson
import com.keel.model.InsertResult
import com.keel.model.Source
import com.keel.model.Transaction
import com.keel.model.TransactionType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Processes [RawEvent] rows where `needsLlmParsing = true`.
 *
 * These events failed regex parsing in [ParserWorker] (returned [ParseResult.NeedsLLM]).
 * This worker sends each one to [OnDeviceBackend.chatOnce] and attempts to extract a
 * structured transaction from the model's response.
 *
 * **Prompt:**
 * ```
 * System: You extract Nigerian bank transaction data. Always respond with only a JSON object.
 * User: Extract from this message: {rawText}
 *       Return: {"amount_kobo": integer, "type": "CREDIT" or "DEBIT", "merchant": string, "balance_kobo": integer or null}
 * ```
 *
 * On success → insert Transaction (if model unavailable or parse fails → Transaction with parsed=false).
 * After each event, clears `needs_llm_parsing` so this worker doesn't revisit it.
 *
 * Enqueued periodically or after [ParserWorker] completes — one-time, expedited request.
 */
@HiltWorker
class LlmParserWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rawEventRepository: RawEventRepository,
    private val transactionRepository: TransactionRepository,
    private val onDeviceBackend: OnDeviceBackend,
) : CoroutineWorker(context, params) {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        // No model? Leave events marked for when model is available.
        if (!onDeviceBackend.isAvailable) return Result.success()

        val events = rawEventRepository.getNeedsLlmParsing()
        if (events.isEmpty()) return Result.success()

        for (event in events) {
            runCatching {
                val tx = parseWithLlm(event.body, event.receivedAt, event.id)
                transactionRepository.insertOrMerge(tx)
            }
            // Always clear the flag — even on failure — to avoid infinite retry loops
            rawEventRepository.clearNeedsLlmParsing(event.id)
        }

        return Result.success()
    }

    private suspend fun parseWithLlm(rawText: String, timestamp: Long, eventId: Long): Transaction {
        val response = onDeviceBackend.chatOnce(
            systemInstruction = "You extract Nigerian bank transaction data. " +
                    "Always respond with only a JSON object, no explanation.",
            userMessage = "Extract from this bank SMS message:\n\"$rawText\"\n\n" +
                    "Return ONLY this JSON: " +
                    "{\"amount_kobo\": integer, \"type\": \"CREDIT\" or \"DEBIT\", " +
                    "\"merchant\": string, \"balance_kobo\": integer or null}",
        )

        val extracted = extractJson(response)
            ?: return failedTransaction(rawText, timestamp)

        return runCatching {
            val obj = jsonParser.parseToJsonElement(extracted).jsonObject as JsonObject
            val amountKobo = obj["amount_kobo"]?.jsonPrimitive?.longOrNull
                ?: return failedTransaction(rawText, timestamp)
            val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull
                ?: return failedTransaction(rawText, timestamp)
            val type = runCatching { TransactionType.valueOf(typeStr) }.getOrElse {
                return failedTransaction(rawText, timestamp)
            }
            val merchant = obj["merchant"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val balanceKobo = obj["balance_kobo"]?.jsonPrimitive?.longOrNull

            Transaction(
                amount = amountKobo,
                type = type,
                merchant = merchant.lowercase().trim(),
                balance = balanceKobo,
                source = Source.SMS,
                parsed = true,
                timestamp = timestamp,
                rawText = rawText,
            )
        }.getOrElse { failedTransaction(rawText, timestamp) }
    }

    private fun failedTransaction(rawText: String, timestamp: Long) = Transaction(
        amount = 0L,
        type = TransactionType.DEBIT,
        merchant = "unknown",
        source = Source.SMS,
        parsed = false,
        timestamp = timestamp,
        rawText = rawText,
    )

    companion object {
        const val TAG = "llm_parser_worker"
        const val WORK_NAME = "com.keel.agent.worker.LlmParserWorker"
    }
}

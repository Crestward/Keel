// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.keel.agent.embedding.EmbeddingEngine
import com.keel.agent.guardrail.GuardrailEngine
import com.keel.agent.notification.KeelNotificationManager
import com.keel.database.repository.AgentMemoryRepository
import com.keel.database.repository.InsightRepository
import com.keel.database.repository.RawEventRepository
import com.keel.database.repository.TransactionRepository
import com.keel.model.InsertResult
import com.keel.model.ParseResult
import com.keel.parser.ParserRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains all unprocessed [RawEvent] rows through [ParserRegistry]:
 *
 * - [ParseResult.Success]   → insert Transaction via [TransactionRepository.insertOrMerge]
 *                             + run safety guardrails via [GuardrailEngine]
 *                             + post any triggered insights via [KeelNotificationManager]
 *                             + mark raw event processed
 * - [ParseResult.NeedsLLM] → set `needsLlmParsing = true` on the raw event
 *                             (LlmParserWorker in Phase 5 picks these up)
 *                             + mark processed so this worker won't revisit it
 * - [ParseResult.Failure]  → leave unprocessed; WorkManager retries on next run
 *
 * Enqueued as an expedited one-shot request from [SmsBackfillWorker] completion
 * and from [SmsBroadcastReceiver] / [KeelNotificationListener] on every new event batch.
 */
@HiltWorker
class ParserWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rawEventRepository: RawEventRepository,
    private val transactionRepository: TransactionRepository,
    private val parserRegistry: ParserRegistry,
    private val guardrailEngine: GuardrailEngine,
    private val insightRepository: InsightRepository,
    private val notificationManager: KeelNotificationManager,
    private val embeddingEngine: EmbeddingEngine,
    private val agentMemoryRepository: AgentMemoryRepository,
    private val workManager: WorkManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val unprocessed = rawEventRepository.getUnprocessedSnapshot()
        if (unprocessed.isEmpty()) return Result.success()

        var parseFailures = 0
        var insertedCount = 0

        for (event in unprocessed) {
            val result = runCatching {
                parserRegistry.parse(
                    senderAddress = event.senderAddress,
                    senderPackage = event.senderPackage,
                    rawText = event.body,
                    source = event.source,
                    timestamp = event.receivedAt,
                )
            }.getOrElse { ParseResult.Failure("exception: ${it.message}") }

            when (result) {
                is ParseResult.Success -> {
                    val insertResult = transactionRepository.insertOrMerge(result.transaction)
                    rawEventRepository.markProcessedBatch(listOf(event.id))

                    // Only fire guardrails on a fresh insert (not a dedup merge).
                    // Merges don't represent a new charge — no alert warranted.
                    if (insertResult is InsertResult.Inserted) {
                        val txWithId = result.transaction.copy(id = insertResult.id)
                        fireGuardrails(txWithId)
                        storeEmbedding(txWithId)
                        insertedCount++
                    }
                }
                is ParseResult.NeedsLLM -> {
                    rawEventRepository.markNeedsLlmParsing(event.id)
                    rawEventRepository.markProcessedBatch(listOf(event.id))
                }
                is ParseResult.Failure -> {
                    // Leave unprocessed — will be retried.
                    parseFailures++
                }
            }
        }

        // Trigger an agent review immediately after new transactions arrive.
        // Expedited so it runs within seconds rather than waiting for the 6h periodic window.
        if (insertedCount > 0) {
            enqueueAgentWorker(insertedCount)
        }

        // Retry if more than half the batch failed — likely a transient issue.
        return if (parseFailures > unprocessed.size / 2) Result.retry() else Result.success()
    }

    /**
     * Enqueues an expedited [AgentWorker] tagged with the number of newly inserted
     * transactions. Uses KEEP policy: if a prior new-transaction run is still queued,
     * don't stack another — the existing one will process the same transactions.
     */
    private fun enqueueAgentWorker(count: Int) {
        val request = OneTimeWorkRequestBuilder<AgentWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    AgentWorker.KEY_TRIGGER to AgentWorker.TRIGGER_NEW_TRANSACTIONS,
                    AgentWorker.KEY_COUNT to count,
                )
            )
            .addTag(AgentWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            AGENT_ON_NEW_TX_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Runs all guardrail rules against [tx], inserts triggered insights, and posts
     * notifications. Any exception here must not fail the whole worker — guardrails
     * are best-effort safety nets, not the primary data pipeline.
     */
    private suspend fun fireGuardrails(tx: com.keel.model.Transaction) {
        runCatching {
            val guardrailInsights = guardrailEngine.evaluate(tx)
            if (guardrailInsights.isEmpty()) return@runCatching

            val insertedInsights = guardrailInsights.map { insight ->
                val id = insightRepository.insert(insight)
                insight.copy(id = id)
            }
            notificationManager.notifyBatch(insertedInsights)
        }
        // Swallow exceptions — a guardrail failure must never block transaction storage.
    }

    /**
     * Embeds the transaction text and stores in the archival memory index.
     * Best-effort: if EmbeddingEngine has no model or the call fails, log nothing and continue.
     */
    private suspend fun storeEmbedding(tx: com.keel.model.Transaction) {
        runCatching {
            val text = buildString {
                append(tx.merchant)
                tx.category?.let { append(" $it") }
                tx.rawText?.let { append(" $it") }
            }
            val embedding = embeddingEngine.embed(text)
            if (embedding.isNotEmpty()) {
                agentMemoryRepository.storeEmbedding(tx.id, embedding)
            }
        }
    }

    companion object {
        const val TAG = "ParserWorker"
        const val WORK_NAME = "com.keel.agent.worker.ParserWorker"
        private const val AGENT_ON_NEW_TX_WORK_NAME = "agent_on_new_transactions"
    }
}

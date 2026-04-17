// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.keel.database.repository.AgentMemoryRepository
import com.keel.llm.LLMBackend
import com.keel.model.AgentMemory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily maintenance job for the three-tier memory system.
 *
 * Steps executed in order (consolidation first, so content isn't lost before pruning):
 *
 *  1. **Consolidate expired RECALL** — reads expired RECALL rows and compresses them
 *     into a single CORE summary entry. If the LLM model is downloaded ([LLMBackend.isAvailable])
 *     it summarises via `chatOnce()`; otherwise falls back to plain concatenation.
 *     The CORE key `consolidated_recall_{yyyy-MM-dd}` is unique per day.
 *
 *  2. **Prune expired** — deletes all rows past their [AgentMemory.expiresAt].
 *
 *  3. **Prune stale RECALL** — deletes RECALL rows older than 7 days with
 *     [AgentMemory.accessCount] < 2 (low-signal entries the agent never re-read).
 *
 * Scheduled daily in [KeelApplication.enqueuePeriodicWorkers].
 */
@HiltWorker
class MemoryConsolidationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val memoryRepository: AgentMemoryRepository,
    private val backend: LLMBackend,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        consolidateExpiredRecall()
        memoryRepository.pruneExpired()
        memoryRepository.pruneStaleRecall()
        return Result.success()
    }

    /**
     * Reads expired RECALL entries and folds them into a CORE summary entry.
     *
     * **With model available:** sends entries to the LLM for compression into ≤80 words,
     * preserving key financial facts (amounts, merchants, patterns).
     *
     * **Without model:** plain concatenation — preserves content, but may be verbose.
     * The agent can still read and reason over it in future runs.
     */
    private suspend fun consolidateExpiredRecall() {
        val now = System.currentTimeMillis()
        val expired = memoryRepository.getExpiredRecall(now)
        if (expired.isEmpty()) return

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val key = "consolidated_recall_$date"

        val summary = if (backend.isAvailable) {
            llmSummary(expired, date)
        } else {
            plainSummary(expired, date)
        }

        memoryRepository.updateCoreMemory(key, summary)
    }

    /**
     * Uses the on-device LLM to compress RECALL entries into ≤80 words.
     * Falls back to [plainSummary] if the LLM call fails.
     */
    private suspend fun llmSummary(entries: List<AgentMemory>, date: String): String {
        val joined = entries.joinToString("\n") { "- ${it.key}: ${it.content}" }
        return runCatching {
            backend.chatOnce(
                systemInstruction = "You are a memory summariser for a personal finance agent. " +
                    "Summarise the following observations in under 80 words, " +
                    "preserving key financial facts (amounts, merchants, spending patterns). " +
                    "Write in past tense. Do not include the date.",
                userMessage = joined,
            ).trim().take(MAX_SUMMARY_CHARS)
        }.getOrElse {
            // LLM call failed — fall back to plain concatenation so no data is lost
            plainSummary(entries, date)
        }
    }

    private fun plainSummary(entries: List<AgentMemory>, date: String): String {
        val content = entries.joinToString("; ") { "${it.key}: ${it.content}" }
        return "Consolidated ${entries.size} recall entries on $date: $content"
            .take(MAX_SUMMARY_CHARS)
    }

    companion object {
        const val TAG = "memory_consolidation_worker"
        const val WORK_NAME = "com.keel.agent.worker.MemoryConsolidationWorker"

        /** Cap summary at 500 chars to stay within CORE memory context budget. */
        private const val MAX_SUMMARY_CHARS = 500
    }
}

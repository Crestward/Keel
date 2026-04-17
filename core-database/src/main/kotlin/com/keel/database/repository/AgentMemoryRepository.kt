// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.repository

import com.keel.database.dao.AgentMemoryDao
import com.keel.database.dao.EmbeddingDao
import com.keel.database.entity.AgentMemoryEntity
import com.keel.database.entity.EmbeddingEntity
import com.keel.database.entity.toModel
import com.keel.model.AgentMemory
import com.keel.model.MemoryType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Three-tier memory system used directly by the agent.
 *
 * CORE tier   — persistent facts (payday, income estimate, user name). Never expires.
 * RECALL tier — 7-day observations stored after each agent run.
 * ARCHIVAL tier — long-term, retrieved via MiniLM-L6-V2 embedding KNN.
 */
@Singleton
class AgentMemoryRepository @Inject constructor(
    private val memoryDao: AgentMemoryDao,
    private val embeddingDao: EmbeddingDao,
) {

    // ─── CORE tier ────────────────────────────────────────────────────────────

    /** Returns all CORE memories as a key→content map. */
    suspend fun getCoreMemory(): Map<String, String> =
        memoryDao.getCoreMemory().associate { it.key to it.content }

    /** Upserts a single CORE memory entry. CORE entries never expire. */
    suspend fun updateCoreMemory(key: String, value: String) {
        val existing = memoryDao.findByKey(key)
        if (existing != null) {
            memoryDao.update(existing.copy(content = value))
        } else {
            memoryDao.upsert(
                AgentMemoryEntity(
                    key = key,
                    content = value,
                    memoryType = MemoryType.CORE,
                    embedding = null,
                    expiresAt = null,
                )
            )
        }
    }

    // ─── RECALL tier ─────────────────────────────────────────────────────────

    /**
     * Stores an observation from the current agent run.
     * [expiresAt] defaults to 7 days from now if null.
     */
    suspend fun addRecallMemory(
        key: String,
        content: String,
        expiresAt: Long? = null,
    ) {
        val expiry = expiresAt ?: (System.currentTimeMillis() + SEVEN_DAYS_MS)
        memoryDao.upsert(
            AgentMemoryEntity(
                key = key,
                content = content,
                memoryType = MemoryType.RECALL,
                embedding = null,
                expiresAt = expiry,
            )
        )
    }

    /**
     * Returns RECALL memories that haven't expired since [start].
     * Used by ContextBuilder to populate the agent's recent observations.
     */
    suspend fun recallByDate(start: Long): List<AgentMemory> =
        memoryDao.getRecallSince(start).map { it.toModel() }

    // ─── ARCHIVAL tier ────────────────────────────────────────────────────────

    /**
     * KNN search over ARCHIVAL memory entries using cosine similarity.
     *
     * Returns memories whose embedding is within [threshold] distance of
     * [queryEmbedding], ordered by similarity, capped at [limit].
     */
    suspend fun archivalSearch(
        queryEmbedding: FloatArray,
        threshold: Float = 0.5f,
        limit: Int = 5,
    ): List<AgentMemory> {
        val all = memoryDao.getAllArchival()
        if (all.isEmpty()) return emptyList()

        val queryNorm = l2Norm(queryEmbedding)
        if (queryNorm == 0f) return emptyList()
        val qNormalized = FloatArray(queryEmbedding.size) { queryEmbedding[it] / queryNorm }

        return all
            .mapNotNull { entity ->
                val blob = entity.embedding ?: return@mapNotNull null
                val candidate = bytesToFloats(blob)
                if (candidate.size != queryEmbedding.size) return@mapNotNull null
                val score = cosineSimilarity(qNormalized, candidate)
                if (score < threshold) return@mapNotNull null
                Pair(entity, score)
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (entity, _) ->
                memoryDao.incrementAccessCount(entity.id)
                entity.toModel()
            }
    }

    /**
     * Stores the embedding for a parsed transaction in the embeddings table.
     * Called by the agent after every successful parse so future KNN searches
     * can surface semantically similar past transactions.
     */
    suspend fun storeEmbedding(transactionId: Long, embedding: FloatArray) {
        embeddingDao.insert(
            EmbeddingEntity(
                transactionId = transactionId,
                embedding = floatsToBytes(embedding),
            )
        )
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Returns expired RECALL entries for consolidation.
     * Call this BEFORE [pruneExpired] so content isn't lost.
     */
    suspend fun getExpiredRecall(now: Long): List<AgentMemory> =
        memoryDao.getExpiredRecall(now).map { it.toModel() }

    /** Deletes all memory rows past their [AgentMemory.expiresAt]. */
    suspend fun pruneExpired() = memoryDao.pruneExpired(System.currentTimeMillis())

    /**
     * Deletes RECALL rows older than 7 days with [AgentMemory.accessCount] < 2.
     * Low-access old recalls are unlikely to be useful for future context.
     */
    suspend fun pruneStaleRecall() = memoryDao.pruneStaleRecall(System.currentTimeMillis())

    // ─── Utilities ───────────────────────────────────────────────────────────

    private fun floatsToBytes(floats: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) buf.putFloat(f)
        return buf.array()
    }

    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buf.getFloat() }
    }

    private fun l2Norm(v: FloatArray): Float {
        var sum = 0f
        for (x in v) sum += x * x
        return sqrt(sum)
    }

    private fun cosineSimilarity(normA: FloatArray, b: FloatArray): Float {
        val bNorm = l2Norm(b)
        if (bNorm == 0f) return 0f
        var dot = 0f
        for (i in normA.indices) dot += normA[i] * (b[i] / bNorm)
        return dot
    }

    companion object {
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.keel.database.entity.EmbeddingEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

@Dao
abstract class EmbeddingDao {

    /** Insert or replace — called after every parsed transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(embedding: EmbeddingEntity): Long

    @Query("SELECT * FROM embeddings WHERE transaction_id = :transactionId LIMIT 1")
    abstract suspend fun findByTransactionId(transactionId: Long): EmbeddingEntity?

    /** Load all embeddings for in-memory KNN. Only called by [knnSearch]. */
    @Query("SELECT * FROM embeddings")
    protected abstract suspend fun getAll(): List<EmbeddingEntity>

    @Query("DELETE FROM embeddings WHERE transaction_id = :transactionId")
    abstract suspend fun deleteByTransactionId(transactionId: Long)

    /**
     * Pure-Kotlin KNN search over all transaction embeddings.
     *
     * Loads every embedding blob (384 float32 = 1536 bytes each), computes
     * cosine similarity against [queryEmbedding], and returns the [limit]
     * transaction IDs with the highest scores.
     *
     * Memory budget: 10k transactions × 1536 bytes ≈ 15 MB — within budget
     * for the portfolio-scale usage this app targets.
     */
    open suspend fun knnSearch(queryEmbedding: FloatArray, limit: Int): List<Long> {
        val all = getAll()
        if (all.isEmpty()) return emptyList()

        val queryNorm = l2Norm(queryEmbedding)
        if (queryNorm == 0f) return emptyList()
        val queryNormalized = FloatArray(queryEmbedding.size) { queryEmbedding[it] / queryNorm }

        return all
            .mapNotNull { entity ->
                val candidate = bytesToFloats(entity.embedding)
                if (candidate.size != queryEmbedding.size) return@mapNotNull null
                val score = cosineSimilarity(queryNormalized, candidate)
                Pair(entity.transactionId, score)
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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
}

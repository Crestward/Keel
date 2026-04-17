// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.keel.database.entity.AgentMemoryEntity
import com.keel.model.MemoryType

@Dao
interface AgentMemoryDao {

    /** Upsert by key — REPLACE discards the old row and inserts fresh */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: AgentMemoryEntity): Long

    @Update
    suspend fun update(memory: AgentMemoryEntity)

    @Query("SELECT * FROM agent_memory WHERE memory_type = 'CORE' ORDER BY access_count DESC")
    suspend fun getCoreMemory(): List<AgentMemoryEntity>

    /**
     * RECALL entries within a date range (by creation/expiry).
     * Used by ContextBuilder to include recent observations in the system prompt.
     */
    @Query(
        """SELECT * FROM agent_memory
           WHERE memory_type = 'RECALL'
             AND (expires_at IS NULL OR expires_at >= :since)
           ORDER BY expires_at DESC"""
    )
    suspend fun getRecallSince(since: Long): List<AgentMemoryEntity>

    /** All ARCHIVAL entries (with embeddings) — loaded for KNN search */
    @Query("SELECT * FROM agent_memory WHERE memory_type = 'ARCHIVAL' AND embedding IS NOT NULL")
    suspend fun getAllArchival(): List<AgentMemoryEntity>

    @Query("SELECT * FROM agent_memory WHERE key = :key LIMIT 1")
    suspend fun findByKey(key: String): AgentMemoryEntity?

    /** Prune RECALL rows older than 7 days with low access */
    @Query(
        """DELETE FROM agent_memory
           WHERE memory_type = 'RECALL'
             AND expires_at IS NOT NULL AND expires_at < :now
             AND access_count < 2"""
    )
    suspend fun pruneStaleRecall(now: Long)

    /** Prune all rows past their expiry — applies to any tier with expiresAt set */
    @Query("DELETE FROM agent_memory WHERE expires_at IS NOT NULL AND expires_at < :now")
    suspend fun pruneExpired(now: Long)

    @Query("UPDATE agent_memory SET access_count = access_count + 1 WHERE id = :id")
    suspend fun incrementAccessCount(id: Long)

    @Query("DELETE FROM agent_memory WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Returns RECALL entries whose expiry has passed — used by MemoryConsolidationWorker
     * before pruning, so their content can be summarised into a CORE entry.
     */
    @Query(
        """SELECT * FROM agent_memory
           WHERE memory_type = 'RECALL'
             AND expires_at IS NOT NULL AND expires_at < :now
           ORDER BY expires_at ASC"""
    )
    suspend fun getExpiredRecall(now: Long): List<AgentMemoryEntity>
}

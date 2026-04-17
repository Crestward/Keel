// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.keel.database.entity.InsightEntity
import com.keel.model.Severity
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(insight: InsightEntity): Long

    /**
     * Active = not dismissed AND (not snoozed OR snooze expired).
     * UI observes this as a Flow for live updates.
     */
    @Query(
        """SELECT * FROM insights
           WHERE dismissed = 0
             AND (snoozed_until IS NULL OR snoozed_until < :now)
           ORDER BY created_at DESC"""
    )
    fun getActiveFlow(now: Long): Flow<List<InsightEntity>>

    /**
     * Fetch insights created by a specific agent run.
     * Chat screen uses this to correlate agent responses to a user query.
     */
    @Query("SELECT * FROM insights WHERE agent_run_id = :agentRunId ORDER BY created_at ASC")
    suspend fun getByRunId(agentRunId: Long): List<InsightEntity>

    @Query("UPDATE insights SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE insights SET snoozed_until = :until WHERE id = :id")
    suspend fun snooze(id: Long, until: Long)

    @Query("SELECT * FROM insights WHERE id = :id")
    suspend fun getById(id: Long): InsightEntity?

    /** Frequency cap check: how many non-dismissed insights of this type since :since */
    @Query(
        """SELECT COUNT(*) FROM insights
           WHERE dismissed = 0 AND created_at >= :since"""
    )
    suspend fun getCountSince(since: Long): Int

    @Query("SELECT * FROM insights ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<InsightEntity>

    /** Frequency cap check filtered by severity (e.g., only WARNING-level) */
    @Query(
        """SELECT COUNT(*) FROM insights
           WHERE dismissed = 0 AND severity = :severity AND created_at >= :since"""
    )
    suspend fun getCountSince(severity: Severity, since: Long): Int
}

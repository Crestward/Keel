// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.keel.database.entity.AgentRunEntity

@Dao
interface AgentRunDao {

    /**
     * Insert a completed agent run audit record.
     * Uses REPLACE so if the same runId is somehow reused, it's overwritten cleanly.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: AgentRunEntity)

    /** Last N runs for the debug screen — ordered newest first */
    @Query("SELECT * FROM agent_runs ORDER BY timestamp DESC LIMIT :n")
    suspend fun getLastN(n: Int): List<AgentRunEntity>

    @Query("SELECT * FROM agent_runs WHERE id = :id")
    suspend fun getById(id: Long): AgentRunEntity?

    @Query("SELECT COUNT(*) FROM agent_runs")
    suspend fun getCount(): Int
}

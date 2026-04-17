// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keel.model.AgentTrigger

@Entity(
    tableName = "agent_runs",
    indices = [Index("timestamp")],
)
data class AgentRunEntity(
    /**
     * NOT autoGenerate — this ID is set to System.currentTimeMillis() at the start of
     * each ReAct loop run. This lets us correlate insights to runs BEFORE the run completes,
     * solving the chicken-and-egg problem with Room auto-generated IDs.
     */
    @PrimaryKey
    val id: Long,
    @ColumnInfo(name = "triggered_by") val triggeredBy: AgentTrigger,   // stored as JSON
    @ColumnInfo(name = "iteration_count") val iterationCount: Int,
    @ColumnInfo(name = "tool_calls_json") val toolCallsJson: String,
    @ColumnInfo(name = "insights_created") val insightsCreated: List<Long>,  // JSON array
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "termination_reason") val terminationReason: String,
    val timestamp: Long,
)

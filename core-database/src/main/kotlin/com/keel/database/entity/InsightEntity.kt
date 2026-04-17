// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keel.model.Severity

@Entity(
    tableName = "insights",
    indices = [
        Index("dismissed", "snoozed_until"),   // active insights query
        Index("agent_run_id"),                  // chat screen: fetch by run
        Index("created_at"),
    ],
)
data class InsightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val body: String,
    val severity: Severity,
    @ColumnInfo(name = "agent_generated") val agentGenerated: Boolean,
    @ColumnInfo(name = "agent_run_id") val agentRunId: Long?,
    val dismissed: Boolean = false,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

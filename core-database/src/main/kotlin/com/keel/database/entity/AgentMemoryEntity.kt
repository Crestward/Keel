// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keel.model.MemoryType

@Entity(
    tableName = "agent_memory",
    indices = [
        Index("memory_type"),             // tier queries
        Index("expires_at"),              // pruning
        Index("access_count"),            // stale recall pruning
        Index(value = ["key"], unique = true),  // upsert on key
    ],
)
data class AgentMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val content: String,
    @ColumnInfo(name = "memory_type") val memoryType: MemoryType,
    /**
     * 384-dim float32 embedding as little-endian IEEE-754 bytes (1536 bytes).
     * Only present for ARCHIVAL tier. Null for CORE and RECALL.
     */
    val embedding: ByteArray?,
    val confidence: Float = 1.0f,
    @ColumnInfo(name = "access_count") val accessCount: Int = 0,
    @ColumnInfo(name = "expires_at") val expiresAt: Long?,
)

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keel.model.Source

@Entity(
    tableName = "raw_events",
    indices = [
        Index("body_hash", "source", "received_at"),  // Level 1 dedup query
        Index("processed"),                            // unprocessed sweep
        Index("needs_llm_parsing"),                   // LlmParserWorker sweep
    ],
)
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sender_address") val senderAddress: String,
    @ColumnInfo(name = "sender_package") val senderPackage: String?,
    val body: String,
    @ColumnInfo(name = "body_hash") val bodyHash: String,
    val source: Source,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    val processed: Boolean = false,
    @ColumnInfo(name = "needs_llm_parsing") val needsLlmParsing: Boolean = false,
)

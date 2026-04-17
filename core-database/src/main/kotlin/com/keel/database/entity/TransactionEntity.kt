// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keel.model.Source
import com.keel.model.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        Index("timestamp"),                           // range queries
        Index("merchant", "timestamp"),               // merchant history
        Index("category", "timestamp"),               // category summaries
        Index("amount", "balance", "source"),         // Level 2 dedup: balance fingerprint
        Index("amount", "type", "merchant"),          // Level 3 dedup: amount+type+merchant
        Index("agent_run_id"),                        // chat screen correlation
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Amount in kobo (1 NGN = 100 kobo) — never float */
    val amount: Long,
    val type: TransactionType,
    val merchant: String,
    val category: String = "other",
    /** Post-transaction balance in kobo. Null if not in message. Critical for Level 2 dedup. */
    val balance: Long?,
    @ColumnInfo(name = "raw_text") val rawText: String = "",
    val source: Source,
    val parsed: Boolean = true,
    @ColumnInfo(name = "account_id") val accountId: Long?,
    @ColumnInfo(name = "agent_run_id") val agentRunId: Long?,
    val timestamp: Long,
)

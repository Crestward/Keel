// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the MiniLM-L6-V2 embedding for a single transaction.
 *
 * The [embedding] blob is 384 float32 values encoded as little-endian IEEE-754 bytes
 * (1536 bytes total). KNN search loads all blobs and computes cosine similarity in memory.
 *
 * Cascade-deletes when the parent transaction is deleted to prevent orphaned blobs.
 */
@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transaction_id"], unique = true),
    ],
)
data class EmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    /** 384-dim float32 as little-endian IEEE-754 bytes (1536 bytes). */
    val embedding: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingEntity) return false
        return id == other.id && transactionId == other.transactionId &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + transactionId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

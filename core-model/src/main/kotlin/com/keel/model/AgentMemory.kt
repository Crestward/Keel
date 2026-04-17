// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.model

import kotlinx.serialization.Serializable

/**
 * A single memory entry in the three-tier memory system:
 * - CORE: persistent facts (payday, income, preferences) — never expires
 * - RECALL: 7-day observations from recent agent runs
 * - ARCHIVAL: long-term, searched via MiniLM-L6-V2 embedding KNN
 */
@Serializable
data class AgentMemory(
    val id: Long = 0,
    val key: String,
    val content: String,
    val memoryType: MemoryType,
    /**
     * 384-dim float32 embedding (little-endian IEEE-754, 1536 bytes).
     * Only set for ARCHIVAL tier. Null for CORE and RECALL.
     */
    val embedding: ByteArray? = null,
    /** 0.0–1.0, confidence in this memory's accuracy */
    val confidence: Float = 1.0f,
    /** Incremented each time the agent reads this memory via search_memory */
    val accessCount: Int = 0,
    /** Epoch millis when this entry should be pruned. Null = never expires (CORE tier). */
    val expiresAt: Long? = null,
) {
    // ByteArray doesn't implement structural equality — override manually
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AgentMemory) return false
        return id == other.id && key == other.key && content == other.content &&
            memoryType == other.memoryType && embedding.contentEquals(other.embedding) &&
            confidence == other.confidence && accessCount == other.accessCount &&
            expiresAt == other.expiresAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + memoryType.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + confidence.hashCode()
        result = 31 * result + accessCount
        result = 31 * result + (expiresAt?.hashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEquals(other: ByteArray?): Boolean {
    if (this == null && other == null) return true
    if (this == null || other == null) return false
    return this.contentEquals(other)
}

@Serializable
enum class MemoryType { CORE, RECALL, ARCHIVAL }

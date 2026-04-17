// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.repository

import com.keel.database.dao.RawEventDao
import com.keel.database.entity.toEntity
import com.keel.database.entity.toModel
import com.keel.database.util.BodyHasher
import com.keel.model.InsertResult
import com.keel.model.RawEvent
import com.keel.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RawEventRepository @Inject constructor(
    private val dao: RawEventDao,
) {
    /** Serialises check-then-insert against concurrent ingestion callers. */
    private val dedupMutex = Mutex()

    /**
     * Level 1 dedup: if a raw event with the same body hash from the same source
     * arrived in the last 10 minutes, do not insert. Returns the result.
     *
     * NOTE: This only deduplicates at the raw-event level. Transaction-level dedup
     * (Levels 2 and 3) lives in TransactionRepository.insertOrMerge().
     */
    suspend fun insertIfNotDuplicate(event: RawEvent): InsertResult = dedupMutex.withLock {
        val tenMinutesAgo = event.receivedAt - TEN_MINUTES_MS
        val existing = dao.findByHashSince(event.bodyHash, event.source, tenMinutesAgo)
        if (existing != null) {
            return@withLock InsertResult.Duplicate(existing.id)
        }
        val id = dao.insert(event.toEntity())
        InsertResult.Inserted(id)
    }

    fun getUnprocessedFlow(): Flow<List<RawEvent>> =
        dao.getUnprocessedFlow().map { list -> list.map { it.toModel() } }

    suspend fun getUnprocessedSnapshot(): List<RawEvent> =
        dao.getUnprocessedSnapshot().map { it.toModel() }

    suspend fun markProcessedBatch(ids: List<Long>) = dao.markProcessedBatch(ids)

    suspend fun markNeedsLlmParsing(id: Long) = dao.markNeedsLlmParsing(id)

    suspend fun getNeedsLlmParsing(): List<RawEvent> = dao.getNeedsLlmParsing().map { it.toModel() }

    suspend fun clearNeedsLlmParsing(id: Long) = dao.clearNeedsLlmParsing(id)

    suspend fun getLatestTimestamp(source: Source): Long? = dao.getLatestTimestamp(source)

    companion object {
        private const val TEN_MINUTES_MS = 10 * 60 * 1000L

        /** Compute the body hash for a raw event body before constructing RawEvent */
        fun computeBodyHash(body: String): String = BodyHasher.hash(body)
    }
}

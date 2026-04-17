// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.keel.database.entity.RawEventEntity
import com.keel.model.Source
import kotlinx.coroutines.flow.Flow

@Dao
interface RawEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: RawEventEntity): Long

    @Query("SELECT * FROM raw_events WHERE processed = 0 ORDER BY received_at ASC")
    fun getUnprocessedFlow(): Flow<List<RawEventEntity>>

    /** Snapshot for WorkManager — avoid collecting a Flow inside a suspend fun */
    @Query("SELECT * FROM raw_events WHERE processed = 0 ORDER BY received_at ASC")
    suspend fun getUnprocessedSnapshot(): List<RawEventEntity>

    @Query("UPDATE raw_events SET processed = 1 WHERE id IN (:ids)")
    suspend fun markProcessedBatch(ids: List<Long>)

    @Query("UPDATE raw_events SET needs_llm_parsing = 1 WHERE id = :id")
    suspend fun markNeedsLlmParsing(id: Long)

    @Query("SELECT MAX(received_at) FROM raw_events WHERE source = :source")
    suspend fun getLatestTimestamp(source: Source): Long?

    /**
     * Level 1 dedup: find a raw event with the same body hash and source within the
     * given time window. Returns null if no match (safe to insert).
     */
    @Query(
        """SELECT * FROM raw_events
           WHERE body_hash = :hash
             AND source = :source
             AND received_at >= :since
           LIMIT 1"""
    )
    suspend fun findByHashSince(hash: String, source: Source, since: Long): RawEventEntity?

    /** Returns processed events that need LLM parsing — picked up by [LlmParserWorker] */
    @Query("SELECT * FROM raw_events WHERE processed = 1 AND needs_llm_parsing = 1 ORDER BY received_at ASC")
    suspend fun getNeedsLlmParsing(): List<RawEventEntity>

    @Query("UPDATE raw_events SET needs_llm_parsing = 0 WHERE id = :id")
    suspend fun clearNeedsLlmParsing(id: Long)

    @Query("SELECT * FROM raw_events WHERE id = :id")
    suspend fun getById(id: Long): RawEventEntity?

    @Query("SELECT COUNT(*) FROM raw_events WHERE processed = 0")
    fun getUnprocessedCountFlow(): Flow<Int>
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.keel.database.dao.AccountDao
import com.keel.database.dao.AgentMemoryDao
import com.keel.database.dao.AgentRunDao
import com.keel.database.dao.CategoryDao
import com.keel.database.dao.EmbeddingDao
import com.keel.database.dao.InsightDao
import com.keel.database.dao.MerchantDao
import com.keel.database.dao.RawEventDao
import com.keel.database.dao.TransactionDao
import com.keel.database.entity.AccountEntity
import com.keel.database.entity.AgentMemoryEntity
import com.keel.database.entity.AgentRunEntity
import com.keel.database.entity.CategoryEntity
import com.keel.database.entity.EmbeddingEntity
import com.keel.database.entity.InsightEntity
import com.keel.database.entity.MerchantEntity
import com.keel.database.entity.RawEventEntity
import com.keel.database.entity.TransactionEntity

/**
 * Single Room database for Keel.
 *
 * Schema version history:
 *  v1 (Phase 1): raw_events, transactions, insights, agent_memory, agent_runs
 *  v2 (Phase 3): + merchants, categories, accounts, embeddings
 */
@Database(
    entities = [
        RawEventEntity::class,
        TransactionEntity::class,
        InsightEntity::class,
        AgentMemoryEntity::class,
        AgentRunEntity::class,
        MerchantEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        EmbeddingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KeelDatabase : RoomDatabase() {

    abstract fun rawEventDao(): RawEventDao
    abstract fun transactionDao(): TransactionDao
    abstract fun insightDao(): InsightDao
    abstract fun agentMemoryDao(): AgentMemoryDao
    abstract fun agentRunDao(): AgentRunDao
    abstract fun merchantDao(): MerchantDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun embeddingDao(): EmbeddingDao

    companion object {
        const val DATABASE_NAME = "keel.db"
    }
}

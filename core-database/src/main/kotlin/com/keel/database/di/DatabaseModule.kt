// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.di

import android.content.Context
import androidx.room.Room
import com.keel.database.DatabaseMigrations
import com.keel.database.KeelDatabase
import com.keel.database.dao.AccountDao
import com.keel.database.dao.AgentMemoryDao
import com.keel.database.dao.AgentRunDao
import com.keel.database.dao.CategoryDao
import com.keel.database.dao.EmbeddingDao
import com.keel.database.dao.InsightDao
import com.keel.database.dao.MerchantDao
import com.keel.database.dao.RawEventDao
import com.keel.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KeelDatabase {
        val passphrase = DatabaseKeyManager.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(
            context,
            KeelDatabase::class.java,
            KeelDatabase.DATABASE_NAME,
        )
            .openHelperFactory(factory)
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .build()
    }

    @Provides fun provideRawEventDao(db: KeelDatabase): RawEventDao = db.rawEventDao()
    @Provides fun provideTransactionDao(db: KeelDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideInsightDao(db: KeelDatabase): InsightDao = db.insightDao()
    @Provides fun provideAgentMemoryDao(db: KeelDatabase): AgentMemoryDao = db.agentMemoryDao()
    @Provides fun provideAgentRunDao(db: KeelDatabase): AgentRunDao = db.agentRunDao()
    @Provides fun provideMerchantDao(db: KeelDatabase): MerchantDao = db.merchantDao()
    @Provides fun provideCategoryDao(db: KeelDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideAccountDao(db: KeelDatabase): AccountDao = db.accountDao()
    @Provides fun provideEmbeddingDao(db: KeelDatabase): EmbeddingDao = db.embeddingDao()
}

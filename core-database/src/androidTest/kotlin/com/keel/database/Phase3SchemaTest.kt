// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keel.database.entity.AccountEntity
import com.keel.database.entity.AgentMemoryEntity
import com.keel.database.entity.CategoryEntity
import com.keel.database.entity.EmbeddingEntity
import com.keel.database.entity.MerchantEntity
import com.keel.database.entity.TransactionEntity
import com.keel.database.seeder.CategorySeeder
import com.keel.model.MemoryType
import com.keel.model.Source
import com.keel.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class Phase3SchemaTest {

    private lateinit var db: KeelDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KeelDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ─── CategoryDao ──────────────────────────────────────────────────────────

    @Test
    fun categorySeeder_seedsExactly11Categories() = runBlocking {
        val seeder = CategorySeeder(db.categoryDao())
        seeder.seedIfEmpty()
        assertEquals(11, db.categoryDao().count())
    }

    @Test
    fun categorySeeder_isIdempotent() = runBlocking {
        val seeder = CategorySeeder(db.categoryDao())
        seeder.seedIfEmpty()
        seeder.seedIfEmpty()
        assertEquals(11, db.categoryDao().count())
    }

    @Test
    fun categoryDao_findBySlug_returnsCorrectRow() = runBlocking {
        val seeder = CategorySeeder(db.categoryDao())
        seeder.seedIfEmpty()
        val cat = db.categoryDao().findBySlug("food_dining")
        assertNotNull(cat)
        assertEquals("Food & Dining", cat!!.name)
        assertEquals("🍔", cat.icon)
    }

    // ─── MerchantDao ─────────────────────────────────────────────────────────

    @Test
    fun merchantDao_insertAndFindByName() = runBlocking {
        val merchant = MerchantEntity(name = "shoprite", category = "food_dining")
        db.merchantDao().insert(merchant)
        val found = db.merchantDao().findByName("shoprite")
        assertNotNull(found)
        assertEquals("shoprite", found!!.name)
    }

    @Test
    fun merchantDao_duplicateInsert_isIgnored() = runBlocking {
        val merchant = MerchantEntity(name = "dstv", isSubscription = true)
        db.merchantDao().insert(merchant)
        db.merchantDao().insert(merchant)
        assertEquals(1, db.merchantDao().getAll().size)
    }

    @Test
    fun merchantDao_getSubscriptions_returnsOnlySubscriptions() = runBlocking {
        db.merchantDao().insert(MerchantEntity(name = "netflix", isSubscription = true, subscriptionIntervalDays = 30))
        db.merchantDao().insert(MerchantEntity(name = "shoprite", isSubscription = false))
        val subs = db.merchantDao().getSubscriptions()
        assertEquals(1, subs.size)
        assertEquals("netflix", subs[0].name)
    }

    // ─── AccountDao ──────────────────────────────────────────────────────────

    @Test
    fun accountDao_insertAndFindByBankAndMasked() = runBlocking {
        val account = AccountEntity(bank = "GTBank", maskedNumber = "****5315", balanceKobo = 500_000L)
        db.accountDao().insert(account)
        val found = db.accountDao().findByBankAndMasked("GTBank", "****5315")
        assertNotNull(found)
        assertEquals(500_000L, found!!.balanceKobo)
    }

    @Test
    fun accountDao_uniqueConstraint_preventsAndIgnoresDuplicate() = runBlocking {
        val account = AccountEntity(bank = "Kuda", maskedNumber = "****1234")
        db.accountDao().insert(account)
        db.accountDao().insert(account)
        assertEquals(1, db.accountDao().getAll().size)
    }

    @Test
    fun accountDao_updateBalance() = runBlocking {
        val id = db.accountDao().insert(AccountEntity(bank = "UBA", maskedNumber = "****9999"))
        db.accountDao().updateBalance(id, 1_000_000L)
        val updated = db.accountDao().getById(id)
        assertEquals(1_000_000L, updated!!.balanceKobo)
    }

    // ─── EmbeddingDao + KNN ──────────────────────────────────────────────────

    @Test
    fun embeddingDao_insertAndFindByTransactionId() = runBlocking {
        val txId = insertTestTransaction()
        val embedding = randomEmbeddingBytes(384)
        db.embeddingDao().insert(EmbeddingEntity(transactionId = txId, embedding = embedding))
        val found = db.embeddingDao().findByTransactionId(txId)
        assertNotNull(found)
        assertTrue(found!!.embedding.contentEquals(embedding))
    }

    @Test
    fun embeddingDao_knnSearch_returnsTopN() = runBlocking {
        // Insert 3 transactions with embeddings
        val ids = (1..3).map { insertTestTransaction() }
        val embeddings = ids.map { randomEmbeddingFloats(384) }
        ids.zip(embeddings).forEach { (txId, emb) ->
            db.embeddingDao().insert(EmbeddingEntity(transactionId = txId, embedding = floatsToBytes(emb)))
        }

        // Query with the first embedding — it should rank top
        val results = db.embeddingDao().knnSearch(embeddings[0], limit = 2)
        assertEquals(2, results.size)
        // First result should be the most similar (same embedding)
        assertEquals(ids[0], results[0])
    }

    @Test
    fun embeddingDao_cascadeDeleteOnTransactionDelete() = runBlocking {
        val txId = insertTestTransaction()
        db.embeddingDao().insert(EmbeddingEntity(transactionId = txId, embedding = randomEmbeddingBytes(384)))
        assertNotNull(db.embeddingDao().findByTransactionId(txId))

        // Room cascades: deleting a transaction should remove its embedding
        db.transactionDao().deleteById(txId)
        assertNull(db.embeddingDao().findByTransactionId(txId))
    }

    // ─── AgentMemoryDao (Phase 3 additions) ──────────────────────────────────

    @Test
    fun agentMemoryDao_getAllArchival_returnsOnlyArchivalWithEmbedding() = runBlocking {
        val dao = db.agentMemoryDao()
        dao.upsert(testMemory("core_key", "core val", com.keel.model.MemoryType.CORE, null))
        dao.upsert(testMemory("recall_key", "recall val", com.keel.model.MemoryType.RECALL, null))
        dao.upsert(testMemory("arch_key", "arch val", com.keel.model.MemoryType.ARCHIVAL, randomEmbeddingBytes(384)))
        val archival = dao.getAllArchival()
        assertEquals(1, archival.size)
        assertEquals("arch_key", archival[0].key)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun insertTestTransaction(): Long {
        return db.transactionDao().insert(
            TransactionEntity(
                amount = 5_000_00L,
                type = TransactionType.DEBIT,
                merchant = "test_merchant",
                balance = null,
                source = Source.SMS,
                accountId = null,
                agentRunId = null,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    private fun randomEmbeddingFloats(dim: Int): FloatArray =
        FloatArray(dim) { (Math.random() * 2 - 1).toFloat() }

    private fun randomEmbeddingBytes(dim: Int): ByteArray =
        floatsToBytes(randomEmbeddingFloats(dim))

    private fun floatsToBytes(floats: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) buf.putFloat(f)
        return buf.array()
    }

    private fun testMemory(
        key: String,
        content: String,
        type: MemoryType,
        embedding: ByteArray?,
    ) = AgentMemoryEntity(
        key = key,
        content = content,
        memoryType = type,
        embedding = embedding,
        expiresAt = null,
    )
}

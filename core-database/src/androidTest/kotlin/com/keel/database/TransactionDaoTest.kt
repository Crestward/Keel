// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.keel.database.dao.TransactionDao
import com.keel.database.entity.TransactionEntity
import com.keel.model.Source
import com.keel.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: KeelDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KeelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_and_retrieve() = runTest {
        val id = dao.insert(makeTx(amount = 500_000, merchant = "Shoprite"))
        val tx = dao.getById(id)
        assertNotNull(tx)
        assertEquals(500_000L, tx!!.amount)
        assertEquals("Shoprite", tx.merchant)
    }

    @Test
    fun getAllFlow_emits_inserted_rows() = runTest {
        dao.insert(makeTx(merchant = "KFC"))
        dao.insert(makeTx(merchant = "UberEats"))

        dao.getAllFlow().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sumByTypeAndRange_sums_only_matching_rows() = runTest {
        val base = 1_000_000L  // some timestamp base
        dao.insert(makeTx(amount = 300_000, type = TransactionType.DEBIT, timestamp = base + 100))
        dao.insert(makeTx(amount = 200_000, type = TransactionType.DEBIT, timestamp = base + 200))
        dao.insert(makeTx(amount = 100_000, type = TransactionType.CREDIT, timestamp = base + 300))
        // This one is outside the range
        dao.insert(makeTx(amount = 999_000, type = TransactionType.DEBIT, timestamp = base + 10_000))

        val sum = dao.sumByTypeAndRange(TransactionType.DEBIT, base, base + 1000)
        assertEquals(500_000L, sum)
    }

    @Test
    fun findByAmountAndBalance_matches_level2_dedup() = runTest {
        val now = System.currentTimeMillis()
        // Insert a NOTIFICATION transaction with balance
        dao.insert(makeTx(
            amount = 150_000_00L, // ₦150,000.00 in kobo
            balance = 120_191_78L,
            source = Source.NOTIFICATION,
            timestamp = now - 1000,
        ))

        // SMS with same amount + type + balance = should find it
        val match = dao.findByAmountAndBalance(
            amount = 150_000_00L,
            type = TransactionType.DEBIT,
            balance = 120_191_78L,
            since = now - 24 * 60 * 60 * 1000L,
        )
        assertNotNull(match)
        assertEquals(Source.NOTIFICATION, match!!.source)
    }

    @Test
    fun findByAmountAndBalance_no_match_when_balance_differs() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(makeTx(amount = 500_000, balance = 1_000_000, source = Source.SMS, timestamp = now - 1000))

        val match = dao.findByAmountAndBalance(
            amount = 500_000,
            type = TransactionType.DEBIT,
            balance = 999_000,  // different balance
            since = now - 24 * 60 * 60 * 1000L,
        )
        assertNull(match)
    }

    @Test
    fun findByAmountAndBalance_no_match_when_type_differs() = runTest {
        // Regression: CREDIT and DEBIT with coincident balance must not collide
        val now = System.currentTimeMillis()
        dao.insert(makeTx(
            amount = 500_000,
            type = TransactionType.CREDIT,
            balance = 1_000_000,
            timestamp = now - 1000,
        ))

        val match = dao.findByAmountAndBalance(
            amount = 500_000,
            type = TransactionType.DEBIT,
            balance = 1_000_000,
            since = now - 24 * 60 * 60 * 1000L,
        )
        assertNull(match)
    }

    @Test
    fun findByAmountAndMerchant_matches_level3_dedup() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(makeTx(
            amount = 10_000,
            type = TransactionType.DEBIT,
            merchant = "dstv",
            timestamp = now - 30 * 60 * 1000, // 30 min ago
        ))

        val match = dao.findByAmountAndMerchant(
            amount = 10_000,
            type = TransactionType.DEBIT,
            merchant = "dstv",
            since = now - 2 * 60 * 60 * 1000L,
        )
        assertNotNull(match)
    }

    @Test
    fun query_respects_limit_cap() = runTest {
        val now = System.currentTimeMillis()
        repeat(30) { i ->
            dao.insert(makeTx(timestamp = now - i * 1000L))
        }

        val results = dao.query(from = 0, to = now + 1000, category = null, merchant = null, limit = 100)
        // limit is capped at 20 in the query
        assertEquals(20, results.size)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeTx(
        amount: Long = 100_000,
        type: TransactionType = TransactionType.DEBIT,
        merchant: String = "test_merchant",
        category: String = "other",
        balance: Long? = null,
        source: Source = Source.SMS,
        timestamp: Long = System.currentTimeMillis(),
    ) = TransactionEntity(
        amount = amount,
        type = type,
        merchant = merchant,
        category = category,
        balance = balance,
        source = source,
        parsed = true,
        accountId = null,
        agentRunId = null,
        timestamp = timestamp,
    )
}

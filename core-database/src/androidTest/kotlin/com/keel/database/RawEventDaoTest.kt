// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.keel.database.dao.RawEventDao
import com.keel.database.entity.RawEventEntity
import com.keel.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In-memory Room tests for RawEventDao.
 * Uses real Room with no SQLCipher (in-memory builder doesn't support SQLCipher).
 * We're testing query correctness, not encryption.
 */
@RunWith(AndroidJUnit4::class)
class RawEventDaoTest {

    private lateinit var db: KeelDatabase
    private lateinit var dao: RawEventDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KeelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.rawEventDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_and_retrieve_by_id() = runTest {
        val entity = makeRawEvent(body = "GTBank: Debit ₦5,000.00")
        val id = dao.insert(entity)
        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals("GTBank: Debit ₦5,000.00", retrieved!!.body)
    }

    @Test
    fun unprocessed_flow_emits_only_unprocessed() = runTest {
        dao.insert(makeRawEvent(body = "msg1", processed = false))
        dao.insert(makeRawEvent(body = "msg2", processed = true))
        dao.insert(makeRawEvent(body = "msg3", processed = false))

        dao.getUnprocessedFlow().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assert(items.all { !it.processed })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun markProcessedBatch_updates_multiple_rows() = runTest {
        val id1 = dao.insert(makeRawEvent(body = "msg1"))
        val id2 = dao.insert(makeRawEvent(body = "msg2"))
        val id3 = dao.insert(makeRawEvent(body = "msg3"))

        dao.markProcessedBatch(listOf(id1, id2))

        val unprocessed = dao.getUnprocessedSnapshot()
        assertEquals(1, unprocessed.size)
        assertEquals(id3, unprocessed.first().id)
    }

    @Test
    fun findByHashSince_returns_match_within_window() = runTest {
        val now = System.currentTimeMillis()
        val hash = "abc123"
        dao.insert(makeRawEvent(bodyHash = hash, source = Source.SMS, receivedAt = now - 5 * 60 * 1000))

        val result = dao.findByHashSince(hash, Source.SMS, now - 10 * 60 * 1000)
        assertNotNull(result)
    }

    @Test
    fun findByHashSince_returns_null_outside_window() = runTest {
        val now = System.currentTimeMillis()
        val hash = "abc123"
        // Inserted 15 minutes ago — outside the 10-minute window
        dao.insert(makeRawEvent(bodyHash = hash, source = Source.SMS, receivedAt = now - 15 * 60 * 1000))

        val result = dao.findByHashSince(hash, Source.SMS, now - 10 * 60 * 1000)
        assertNull(result)
    }

    @Test
    fun findByHashSince_different_source_not_matched() = runTest {
        val now = System.currentTimeMillis()
        val hash = "abc123"
        dao.insert(makeRawEvent(bodyHash = hash, source = Source.NOTIFICATION, receivedAt = now - 1000))

        // SMS query should not find a NOTIFICATION event
        val result = dao.findByHashSince(hash, Source.SMS, now - 10 * 60 * 1000)
        assertNull(result)
    }

    @Test
    fun getLatestTimestamp_returns_max_received_at() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(makeRawEvent(receivedAt = now - 3000, source = Source.SMS))
        dao.insert(makeRawEvent(receivedAt = now - 1000, source = Source.SMS))
        dao.insert(makeRawEvent(receivedAt = now - 2000, source = Source.SMS))

        val latest = dao.getLatestTimestamp(Source.SMS)
        assertEquals(now - 1000, latest)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeRawEvent(
        body: String = "test body",
        bodyHash: String = "hash_${System.nanoTime()}",
        source: Source = Source.SMS,
        receivedAt: Long = System.currentTimeMillis(),
        processed: Boolean = false,
    ) = RawEventEntity(
        senderAddress = "GTBank",
        senderPackage = null,
        body = body,
        bodyHash = bodyHash,
        source = source,
        receivedAt = receivedAt,
        processed = processed,
    )
}

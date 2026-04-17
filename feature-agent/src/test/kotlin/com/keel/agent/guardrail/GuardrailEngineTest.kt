// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.guardrail

import com.keel.database.dao.AccountDao
import com.keel.database.dao.TransactionDao
import com.keel.database.entity.AccountEntity
import com.keel.database.entity.TransactionEntity
import com.keel.model.Severity
import com.keel.model.Source
import com.keel.model.Transaction
import com.keel.model.TransactionType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GuardrailEngineTest {

    private val transactionDao: TransactionDao = mockk()
    private val accountDao: AccountDao = mockk()
    private val engine = GuardrailEngine(transactionDao, accountDao)

    /**
     * Stub all DAO calls to safe "no-signal" defaults before each test.
     *
     * MockK returns a relaxed mock entity (not null) for data class return types even
     * when the return type is nullable — so we must be explicit for every method the
     * engine calls.  Individual tests override only what they care about.
     */
    @Before
    fun stubSafeDefaults() {
        // LowBalance: accountDao not consulted when tx.balance is set
        coEvery { accountDao.getById(any()) } returns null

        // DuplicateCharge: no prior transaction
        coEvery { transactionDao.findDuplicateCharge(any(), any(), any(), any()) } returns null

        // LargeUnexpected: not enough history by default
        coEvery { transactionDao.countDebitsByMerchant(any(), any()) } returns 0
        coEvery { transactionDao.averageDebitByMerchant(any(), any()) } returns 0L
    }

    // ─── LowBalanceGuardrail ──────────────────────────────────────────────────

    @Test
    fun `LowBalanceGuardrail fires CRITICAL when transaction balance is below 5000 NGN`() = runTest {
        // ₦3,000 = 300,000 kobo — below the ₦5,000 = 500,000 kobo threshold
        val tx = debitTx(amount = 100_000L, balance = 300_000L)

        val insights = engine.evaluate(tx)

        assertEquals(1, insights.size)
        assertEquals(Severity.CRITICAL, insights[0].severity)
        assertTrue(insights[0].title.contains("Low Balance", ignoreCase = true))
    }

    @Test
    fun `LowBalanceGuardrail does NOT fire when balance is above threshold`() = runTest {
        val tx = debitTx(amount = 100_000L, balance = 600_000L)  // ₦6,000 — above threshold

        val insights = engine.evaluate(tx)

        assertTrue("No insight expected for balance above threshold", insights.isEmpty())
    }

    @Test
    fun `LowBalanceGuardrail falls back to AccountDao when transaction balance is null`() = runTest {
        val tx = debitTx(amount = 50_000L, balance = null, accountId = 42L)
        coEvery { accountDao.getById(42L) } returns AccountEntity(
            id = 42L, bank = "GTBank", maskedNumber = "***1234", balanceKobo = 200_000L
        )

        val insights = engine.evaluate(tx)

        assertEquals(1, insights.size)
        assertEquals(Severity.CRITICAL, insights[0].severity)
    }

    // ─── DuplicateChargeGuardrail ─────────────────────────────────────────────

    @Test
    fun `DuplicateChargeGuardrail fires WARNING when same merchant and amount appear within 24h`() = runTest {
        val tx = debitTx(merchant = "mtn", amount = 150_000L, id = 99L)
        val priorTx = entityTx(id = 55L, merchant = "mtn", amount = 150_000L)
        coEvery {
            transactionDao.findDuplicateCharge(
                merchant = "mtn",
                amount = 150_000L,
                since = any(),
                excludeId = 99L,
            )
        } returns priorTx

        val insights = engine.evaluate(tx)

        assertEquals(1, insights.size)
        assertEquals(Severity.WARNING, insights[0].severity)
        assertTrue(insights[0].title.contains("Duplicate", ignoreCase = true))
    }

    @Test
    fun `DuplicateChargeGuardrail does NOT fire when no prior transaction found`() = runTest {
        val tx = debitTx(merchant = "dstv", amount = 450_000L, id = 100L)
        // safe default already stubs findDuplicateCharge to return null

        val insights = engine.evaluate(tx)

        assertTrue("No duplicate insight expected", insights.isEmpty())
    }

    @Test
    fun `DuplicateChargeGuardrail does NOT fire for CREDIT transactions`() = runTest {
        val tx = creditTx(merchant = "employer", amount = 5_000_000L, id = 200L)

        val insights = engine.evaluate(tx)

        assertTrue("DuplicateCharge should not fire for CREDIT", insights.none {
            it.title.contains("Duplicate", ignoreCase = true)
        })
    }

    // ─── LargeUnexpectedDebitGuardrail ────────────────────────────────────────

    @Test
    fun `LargeUnexpectedDebitGuardrail fires WARNING when debit exceeds 3x merchant average`() = runTest {
        // Average: ₦1,000 (100,000 kobo). This charge: ₦4,000 (400,000 kobo) = 4× average.
        val tx = debitTx(merchant = "netflix", amount = 400_000L, id = 300L)
        coEvery { transactionDao.countDebitsByMerchant("netflix", 300L) } returns 5
        coEvery { transactionDao.averageDebitByMerchant("netflix", 300L) } returns 100_000L

        val insights = engine.evaluate(tx)

        assertEquals(1, insights.size)
        assertEquals(Severity.WARNING, insights[0].severity)
        assertTrue(insights[0].title.contains("Large", ignoreCase = true))
    }

    @Test
    fun `LargeUnexpectedDebitGuardrail does NOT fire when insufficient history`() = runTest {
        val tx = debitTx(merchant = "new_vendor", amount = 900_000L, id = 301L)
        // countDebitsByMerchant returns 2 — below MIN_HISTORY_COUNT = 3
        coEvery { transactionDao.countDebitsByMerchant("new_vendor", 301L) } returns 2

        val insights = engine.evaluate(tx)

        assertTrue("Should not fire with insufficient history", insights.isEmpty())
    }

    @Test
    fun `LargeUnexpectedDebitGuardrail does NOT fire when amount is within 3x average`() = runTest {
        // Average: ₦1,000. This charge: ₦2,500 = 2.5× — below the 3× threshold.
        val tx = debitTx(merchant = "netflix", amount = 250_000L, id = 302L)
        coEvery { transactionDao.countDebitsByMerchant("netflix", 302L) } returns 10
        coEvery { transactionDao.averageDebitByMerchant("netflix", 302L) } returns 100_000L

        val insights = engine.evaluate(tx)

        assertTrue("Should not fire for 2.5× charge", insights.isEmpty())
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun debitTx(
        id: Long = 1L,
        merchant: String = "vendor",
        amount: Long = 100_000L,
        balance: Long? = null,
        accountId: Long? = null,
    ) = Transaction(
        id = id,
        amount = amount,
        type = TransactionType.DEBIT,
        merchant = merchant,
        balance = balance,
        accountId = accountId,
        source = Source.SMS,
        timestamp = System.currentTimeMillis(),
    )

    private fun creditTx(
        id: Long = 1L,
        merchant: String = "employer",
        amount: Long = 5_000_000L,
        balance: Long? = null,
    ) = Transaction(
        id = id,
        amount = amount,
        type = TransactionType.CREDIT,
        merchant = merchant,
        balance = balance,
        source = Source.SMS,
        timestamp = System.currentTimeMillis(),
    )

    private fun entityTx(
        id: Long = 55L,
        merchant: String = "vendor",
        amount: Long = 100_000L,
    ) = TransactionEntity(
        id = id,
        amount = amount,
        type = TransactionType.DEBIT,
        merchant = merchant,
        balance = null,
        source = Source.SMS,
        accountId = null,
        agentRunId = null,
        timestamp = System.currentTimeMillis() - 3_600_000L,
    )
}

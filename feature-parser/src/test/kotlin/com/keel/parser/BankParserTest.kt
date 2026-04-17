// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.parser

import com.keel.model.ParseResult
import com.keel.model.Source
import com.keel.model.TransactionType
import com.keel.parser.model.BankParserConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * 26 pure JVM tests for [BankParser]. Covers 3–4 format variants per bank
 * plus the NeedsLLM fallback path (unrecognised sender, unmatched pattern).
 *
 * Configs are loaded from `src/test/resources/parsers/` (copies of the assets)
 * so tests run without Android context.
 */
class BankParserTest {

    companion object {
        private lateinit var parser: BankParser
        private val json = Json { ignoreUnknownKeys = true }

        @BeforeClass
        @JvmStatic
        fun setup() {
            val bankNames = listOf(
                "gtbank", "access", "firstbank", "uba",
                "kuda", "opay", "palmpay", "zenith", "moniepoint",
            )
            val configs = bankNames.mapNotNull { name ->
                runCatching {
                    val text = BankParserTest::class.java
                        .getResourceAsStream("/parsers/$name.json")
                        ?.bufferedReader()?.readText()
                        ?: return@mapNotNull null
                    json.decodeFromString<BankParserConfig>(text)
                }.getOrNull()
            }
            parser = BankParser(configs)
        }

        private fun parse(sender: String, body: String) = parser.parse(
            senderAddress = sender,
            senderPackage = null,
            rawText = body,
            source = Source.SMS,
            timestamp = 1_000_000L,
        )
    }

    // ─── Fallback ─────────────────────────────────────────────────────────────

    @Test fun unknown_sender_returns_NeedsLLM() {
        val result = parse("UnknownBank", "Debit of NGN5,000")
        assertTrue(result is ParseResult.NeedsLLM)
    }

    @Test fun unmatched_pattern_returns_NeedsLLM() {
        val result = parse("GTBank", "Your GTBank statement is ready for download.")
        assertTrue(result is ParseResult.NeedsLLM)
    }

    // ─── GTBank ───────────────────────────────────────────────────────────────

    @Test fun gtbank_structured_debit_alert() {
        // Real GTWorld format: Acct/Amt DR/Desc/Avail Bal (confirmed from banks.md)
        val body = """
            Acct: ******5315
            Amt: NGN5,000.00 DR
            Desc: VIA GTWORLD POS--SHOPRITE
            Avail Bal: NGN12,345.67
            Date: 2026-03-06 11:26:39 AM
        """.trimIndent()
        val tx = successTx(parse("GTBank", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(1_234_567L, tx.balance)
        assertNotNull(tx.merchant)
    }

    @Test fun gtbank_structured_credit_alert() {
        val body = """
            GTBank Credit Alert
            Acct:****5315
            Amt:NGN50,000.00
            Desc:NIP/JOHN DOE
            Avail Bal:NGN62,345.67
        """.trimIndent()
        val tx = successTx(parse("GTBank", body))
        assertEquals(5_000_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(6_234_567L, tx.balance)
    }

    @Test fun gtbank_prose_debit_with_balance() {
        val body = "GTBank: Your acct ****5315 was debited NGN3,500.00 for ATM. Avail Bal:NGN8,845.67"
        val tx = successTx(parse("GTBank", body))
        assertEquals(350_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(884_567L, tx.balance)
    }

    @Test fun gtbank_case_insensitive_sender() {
        val body = "GTBank Credit Alert\nAmt:NGN10,000.00\nDesc:Transfer\nAvail Bal:NGN20,000.00"
        // Both "GTBANK" and "GTBank" should match
        assertTrue(parse("GTBANK", body) is ParseResult.Success)
    }

    // ─── Access Bank ──────────────────────────────────────────────────────────

    @Test fun access_debit_of_format() {
        val body = "Access Bank: Debit of NGN5,000.00 from Acct ****1234. Desc: POS SHOPRITE. Bal:NGN12,345.67"
        val tx = successTx(parse("AccessBnk", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(1_234_567L, tx.balance)
    }

    @Test fun access_credit_of_format() {
        val body = "Access Bank: Credit of NGN50,000.00 to Acct ****1234. Desc: NIP FRM JOHN DOE. Bal:NGN62,345.67"
        val tx = successTx(parse("AccessBank", body))
        assertEquals(5_000_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
    }

    @Test fun access_txn_pipe_debit() {
        val body = "Txn: Debit|Acct: ****1234|Amt: NGN3,500.00|Desc: Airtime purchase|Bal: NGN8,845.67"
        val tx = successTx(parse("AccessBnk", body))
        assertEquals(350_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(884_567L, tx.balance)
    }

    @Test fun access_txn_pipe_credit() {
        val body = "Txn: Credit|Acct: ****1234|Amt: NGN50,000.00|Desc: NIP FRM JOHN DOE|Bal: NGN62,345.67"
        val tx = successTx(parse("AccessBnk", body))
        assertEquals(5_000_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(6_234_567L, tx.balance)
        assertNotNull(tx.merchant)
    }

    // ─── First Bank ───────────────────────────────────────────────────────────

    @Test fun firstbank_real_format_debit() {
        // Exact SMS from banks.md — ACCTNO Amt: Date: Desc: FIP:... Bal: NGN...CR.
        val body = "310XXXX426 Amt: NGN150,000.00 Date: 28-JUN-2025 06:44:37 Desc: FIP:MB:UBA/ATINUKE IBUKUN ADE/FBNMOBILE:. Bal: NGN120,191.78CR. Dial *894*11# to get loan"
        val tx = successTx(parse("FirstBank", body))
        assertEquals(15_000_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(12_019_178L, tx.balance)  // CR suffix on balance is stripped by regex
    }

    @Test fun firstbank_received_prose_credit() {
        val body = "FirstBank: You have received NGN50,000.00 from JOHN DOE in your account ****1234. Available balance: NGN62,345.67"
        val tx = successTx(parse("FirstBank", body))
        assertEquals(5_000_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(6_234_567L, tx.balance)
    }

    @Test fun firstbank_alert_debit() {
        val body = "Alert! Debit on your FirstBank Acct ****1234. Amount: NGN5,000.00. Merchant: SHOPRITE. Balance: NGN12,345.67"
        val tx = successTx(parse("FirstBank", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(1_234_567L, tx.balance)
    }

    // ─── UBA ──────────────────────────────────────────────────────────────────

    @Test fun uba_cr_alert() {
        val body = "UBA: Cr alert on acct ****1234: NGN50,000.00 Received from JOHN DOE 07/04/2026. Avail Bal: NGN62,345.67"
        val tx = successTx(parse("UBA", body))
        assertEquals(5_000_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(6_234_567L, tx.balance)
    }

    @Test fun uba_dr_alert() {
        val body = "UBA: Dr alert on acct ****1234: NGN5,000.00 Deducted for ATM WITHDRAWAL. Avail Bal: NGN12,345.67"
        val tx = successTx(parse("UBAConnect", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(1_234_567L, tx.balance)
    }

    // ─── Kuda ─────────────────────────────────────────────────────────────────

    @Test fun kuda_credit_dash_format() {
        val body = "Credit - NGN5,000 received from JOHN DOE on your Kuda account. Available balance: NGN12,345."
        val tx = successTx(parse("Kuda", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
    }

    @Test fun kuda_debit_dash_format() {
        val body = "Debit - NGN3,500 paid to SHOPRITE with your Kuda card. Available balance: NGN8,845."
        val tx = successTx(parse("Kuda", body))
        assertEquals(350_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
    }

    @Test fun kuda_you_just_received() {
        val body = "You just received NGN10,000.00 from JANE DOE. Balance: NGN20,000.00"
        val tx = successTx(parse("Kuda", body))
        assertEquals(1_000_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
    }

    @Test fun kuda_you_just_sent() {
        val body = "You just sent NGN5,000.00 to SHOPRITE. Balance: NGN7,000.00"
        val tx = successTx(parse("Kuda", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(700_000L, tx.balance)
    }

    // ─── OPay ─────────────────────────────────────────────────────────────────

    @Test fun opay_you_received() {
        val body = "OPay: You received NGN5,000.00 from JOHN DOE. Balance: NGN12,345.67"
        val tx = successTx(parse("OPay", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(1_234_567L, tx.balance)
    }

    @Test fun opay_transfer_successful() {
        val body = "Transfer Successful! NGN5,000.00 transferred to JOHN DOE. New Balance: NGN7,345.67"
        val tx = successTx(parse("OPay", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
    }

    // ─── PalmPay ──────────────────────────────────────────────────────────────

    @Test fun palmpay_received_from() {
        val body = "PalmPay: NGN5,000.00 received from JOHN DOE. Bal: NGN12,345.67"
        val tx = successTx(parse("PalmPay", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(1_234_567L, tx.balance)
    }

    @Test fun palmpay_transfer_was_successful() {
        val body = "Transfer of NGN3,500.00 to SHOPRITE was successful. Bal: NGN8,845.67"
        val tx = successTx(parse("PalmPay", body))
        assertEquals(350_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(884_567L, tx.balance)
    }

    // ─── Zenith ───────────────────────────────────────────────────────────────

    @Test fun zenith_debit_alert_pipe_format() {
        val body = "ZENITH BANK DEBIT ALERT|AMT=NGN5,000.00|DESC=POS PURCHASE SHOPRITE|BAL=NGN12,345.67|DATE=07-APR-26"
        val tx = successTx(parse("ZenithBank", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(1_234_567L, tx.balance)
    }

    @Test fun zenith_credit_alert_pipe_format() {
        val body = "ZENITH BANK CREDIT ALERT|AMT=NGN50,000.00|DESC=NIP CREDIT FROM JOHN DOE|BAL=NGN62,345.67"
        val tx = successTx(parse("Zenith", body))
        assertEquals(5_000_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(6_234_567L, tx.balance)
    }

    // ─── Moniepoint ───────────────────────────────────────────────────────────

    @Test fun moniepoint_credit_structured() {
        val body = "[Moniepoint] Credit: Your account ****1234 has been credited with NGN50,000.00. Desc: Transfer from JOHN DOE. Bal: NGN62,345.67"
        val tx = successTx(parse("Moniepoint", body))
        assertEquals(5_000_000L, tx.amount)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(6_234_567L, tx.balance)
    }

    @Test fun moniepoint_debit_structured() {
        val body = "[Moniepoint] Debit: Your account ****1234 has been debited NGN5,000.00. Desc: POS - SHOPRITE. Bal: NGN12,345.67"
        val tx = successTx(parse("Moniepoint", body))
        assertEquals(500_000L, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(1_234_567L, tx.balance)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun successTx(result: ParseResult): com.keel.model.Transaction {
        assertTrue("Expected Success but got $result", result is ParseResult.Success)
        return (result as ParseResult.Success).transaction
    }
}

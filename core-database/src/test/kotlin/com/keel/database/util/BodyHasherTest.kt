// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure JVM tests for BodyHasher normalisation + hashing. Guards against regressions
 * in the F11 dedup body-hash step, which is the first line of defence against
 * duplicate SMS/notification bodies.
 */
class BodyHasherTest {

    @Test
    fun hash_is_deterministic_for_same_input() {
        val body = "GTB: Debit of NGN 5,000.00 on acct *1234. Bal: NGN 12,345.67"
        assertEquals(BodyHasher.hash(body), BodyHasher.hash(body))
    }

    @Test
    fun hash_length_is_16_chars() {
        val h = BodyHasher.hash("anything at all")
        assertEquals(16, h.length)
    }

    @Test
    fun hash_ignores_case_differences() {
        val a = "GTB: Debit NGN 5000 on *1234"
        val b = "gtb: debit ngn 5000 on *1234"
        assertEquals(BodyHasher.hash(a), BodyHasher.hash(b))
    }

    @Test
    fun hash_ignores_extra_whitespace() {
        val a = "Debit NGN 5000 on *1234 Bal 12345"
        val b = "Debit   NGN\t5000  on  *1234\nBal  12345"
        assertEquals(BodyHasher.hash(a), BodyHasher.hash(b))
    }

    @Test
    fun hash_ignores_zero_width_and_tab_characters() {
        val a = "Debit NGN 5000 on *1234"
        // Zero-width space (U+200B) + tab + non-breaking space (U+00A0) interleaved.
        val b = "Debit\u200B NGN\t5000 on\u00A0*1234"
        assertEquals(BodyHasher.hash(a), BodyHasher.hash(b))
    }

    @Test
    fun hash_ignores_cosmetic_punctuation_outside_financial_set() {
        val a = "Debit NGN 5000 on *1234 Bal 12345"
        val b = "Debit! NGN (5000) on *1234; Bal= 12345?"
        assertEquals(BodyHasher.hash(a), BodyHasher.hash(b))
    }

    @Test
    fun hash_preserves_financial_punctuation_marks() {
        // '.' , ':' , '/' , '#' , '*' , '-' carry meaning in bank messages and
        // must survive normalisation so distinct messages don't collide.
        val a = "Bal: 12,345.67"
        val b = "Bal: 12 34567"
        assertNotEquals(BodyHasher.hash(a), BodyHasher.hash(b))
    }

    @Test
    fun hash_differs_for_different_amounts() {
        val a = "Debit NGN 5000 on *1234"
        val b = "Debit NGN 6000 on *1234"
        assertNotEquals(BodyHasher.hash(a), BodyHasher.hash(b))
    }

    @Test
    fun hash_differs_for_different_accounts() {
        val a = "Debit NGN 5000 on *1234"
        val b = "Debit NGN 5000 on *5678"
        assertNotEquals(BodyHasher.hash(a), BodyHasher.hash(b))
    }

    @Test
    fun normalize_strips_all_letters_outside_ascii_lowercase_alphanumerics() {
        // Unicode letter variants are stripped — only bare a-z 0-9 + finance punct survive.
        val out = BodyHasher.normalize("Café ₦500.00")
        // 'é' is stripped; '₦' survives as a financial marker.
        assertEquals("caf₦500.00", out)
    }
}

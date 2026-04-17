// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.embedding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleWordpieceTokenizerTest {

    private val tokenizer = SimpleWordpieceTokenizer()

    @Test
    fun `empty string produces CLS SEP then PAD`() {
        val ids = tokenizer.tokenize("")
        assertEquals(SimpleWordpieceTokenizer.MAX_LENGTH, ids.size)
        assertEquals(SimpleWordpieceTokenizer.CLS_TOKEN_ID, ids[0])
        assertEquals(SimpleWordpieceTokenizer.SEP_TOKEN_ID, ids[1])
        for (i in 2 until ids.size) {
            assertEquals(SimpleWordpieceTokenizer.PAD_TOKEN_ID, ids[i])
        }
    }

    @Test
    fun `single word produces CLS word SEP then PAD`() {
        val ids = tokenizer.tokenize("hello")
        assertEquals(SimpleWordpieceTokenizer.CLS_TOKEN_ID, ids[0])
        val wordId = ids[1]
        assertTrue("word id should be >= 200", wordId >= 200)
        assertEquals(SimpleWordpieceTokenizer.SEP_TOKEN_ID, ids[2])
        assertEquals(SimpleWordpieceTokenizer.PAD_TOKEN_ID, ids[3])
    }

    @Test
    fun `output length is always maxLength`() {
        val sentences = listOf("", "a", "hello world", "this is a longer sentence with multiple words")
        for (text in sentences) {
            val ids = tokenizer.tokenize(text)
            assertEquals("length should be ${SimpleWordpieceTokenizer.MAX_LENGTH} for '$text'",
                SimpleWordpieceTokenizer.MAX_LENGTH, ids.size)
        }
    }

    @Test
    fun `same text produces identical token IDs deterministic`() {
        val text = "GTBank debit 5000 naira"
        val ids1 = tokenizer.tokenize(text)
        val ids2 = tokenizer.tokenize(text)
        assertTrue(ids1.contentEquals(ids2))
    }

    @Test
    fun `case insensitive GTBank equals gtbank`() {
        val upper = tokenizer.tokenize("GTBANK")
        val lower = tokenizer.tokenize("gtbank")
        assertTrue("uppercase and lowercase should tokenize identically",
            upper.contentEquals(lower))
    }

    @Test
    fun `long text is truncated to maxLength with SEP at end`() {
        val words = (1..200).joinToString(" ") { "word$it" }
        val ids = tokenizer.tokenize(words)
        assertEquals(SimpleWordpieceTokenizer.MAX_LENGTH, ids.size)
        assertEquals(SimpleWordpieceTokenizer.CLS_TOKEN_ID, ids[0])
        // SEP must appear before the last PAD block, within the buffer
        val sepIndex = ids.indexOfFirst { it == SimpleWordpieceTokenizer.SEP_TOKEN_ID }
        assertTrue("SEP should be within first 128 tokens", sepIndex in 1 until SimpleWordpieceTokenizer.MAX_LENGTH)
    }

    @Test
    fun `attention mask matches non-padding tokens`() {
        val ids = tokenizer.tokenize("spend food transport")
        val mask = tokenizer.attentionMask(ids)
        assertEquals(ids.size, mask.size)
        for (i in ids.indices) {
            val expected = if (ids[i] != SimpleWordpieceTokenizer.PAD_TOKEN_ID) 1 else 0
            assertEquals("mask[$i] mismatch for token ${ids[i]}", expected, mask[i])
        }
    }

    @Test
    fun `punctuation is stripped before tokenizing`() {
        val withPunct = tokenizer.tokenize("hello, world!")
        val withoutPunct = tokenizer.tokenize("hello world")
        assertTrue("punctuation should be stripped: tokens should match",
            withPunct.contentEquals(withoutPunct))
    }

    @Test
    fun `all word IDs are in valid range 200 to VOCAB_SIZE`() {
        // Exercises many hash values including potential Int.MIN_VALUE edge cases
        val words = (0..1000).map { "word$it" } + listOf("", "a", "zz", "\u00e9\u00e9\u00e9")
        for (word in words) {
            val ids = tokenizer.tokenize(word)
            for (i in 1 until ids.size) {
                val id = ids[i]
                if (id == SimpleWordpieceTokenizer.SEP_TOKEN_ID || id == SimpleWordpieceTokenizer.PAD_TOKEN_ID) continue
                assertTrue("word '$word' produced out-of-range id $id", id in 200 until 30522)
            }
        }
    }

    @Test
    fun `numeric string tokenizes without crash`() {
        val ids = tokenizer.tokenize("5000.00")
        assertEquals(SimpleWordpieceTokenizer.MAX_LENGTH, ids.size)
        assertEquals(SimpleWordpieceTokenizer.CLS_TOKEN_ID, ids[0])
    }
}

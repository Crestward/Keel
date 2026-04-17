// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.embedding

/**
 * Minimal WordPiece-inspired tokenizer for MiniLM-L6-V2 inference.
 *
 * Uses deterministic hash-based pseudo-IDs in the BERT vocabulary range instead of
 * the full 30,522-token vocabulary file. This avoids shipping a multi-MB vocab asset
 * while remaining compatible with the float32 input tensors that MiniLM expects.
 *
 * Consistency guarantee: the same text always produces the same token IDs across
 * runs — necessary for embedding stability.
 *
 * Special token IDs (matching standard BERT):
 *   PAD = 0, CLS = 101, SEP = 102, UNK = 100
 */
class SimpleWordpieceTokenizer(val maxLength: Int = MAX_LENGTH) {

    /**
     * Tokenizes [text] into a fixed-length int array of token IDs.
     *
     * Output layout: [CLS, t1, t2, ..., tN, SEP, PAD, PAD, ...]
     * Length is always exactly [maxLength].
     */
    fun tokenize(text: String): IntArray {
        val tokens = IntArray(maxLength) { PAD_TOKEN_ID }
        tokens[0] = CLS_TOKEN_ID

        val words = splitIntoWords(text)
        var pos = 1
        for (word in words) {
            if (pos >= maxLength - 1) break  // reserve last slot for SEP
            tokens[pos++] = wordToId(word)
        }

        tokens[pos] = SEP_TOKEN_ID
        return tokens
    }

    /**
     * Returns the attention mask for a token array (1 = real token, 0 = padding).
     * Pass the result of [tokenize].
     */
    fun attentionMask(tokenIds: IntArray): IntArray {
        return IntArray(tokenIds.size) { i ->
            if (tokenIds[i] != PAD_TOKEN_ID) 1 else 0
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun splitIntoWords(text: String): List<String> {
        // Lowercase, split on whitespace and common punctuation, filter blanks.
        return text.lowercase()
            .replace(PUNCTUATION_RE, " ")
            .trim()
            .split(WHITESPACE_RE)
            .filter { it.isNotEmpty() }
    }

    /**
     * Deterministic hash → pseudo-ID in [200, VOCAB_SIZE).
     * Range [0, 199] is reserved for special tokens and common BERT tokens
     * so we start at 200 to avoid accidental collisions.
     */
    private fun wordToId(word: String): Int {
        // djb2-style hash — fast, deterministic, good distribution
        var hash = 5381
        for (c in word) hash = hash * 33 + c.code
        return 200 + (hash and 0x7FFFFFFF) % (VOCAB_SIZE - 200)
    }

    companion object {
        const val PAD_TOKEN_ID = 0
        const val UNK_TOKEN_ID = 100
        const val CLS_TOKEN_ID = 101
        const val SEP_TOKEN_ID = 102
        const val MAX_LENGTH = 128
        private const val VOCAB_SIZE = 30522

        private val PUNCTUATION_RE = Regex("[.,!?;:()\\[\\]{}\"'\\-/\\\\@#\$%^&*+=<>|~`]")
        private val WHITESPACE_RE = Regex("\\s+")
    }
}

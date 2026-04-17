// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

/**
 * Extracts the outermost JSON object from [text].
 *
 * LiteRT-LM has no constrained decoding (setResponseJsonSchema does not exist
 * in the Kotlin API — GitHub Issue #1662). The model sometimes wraps its JSON in
 * prose ("Here is my response: {...}"). This function finds the first `{` and
 * tracks brace depth to extract the complete object, ignoring any surrounding text.
 *
 * Returns null if no complete JSON object is found.
 *
 * Usage in [ReActLoop]:
 *   val json = extractJson(rawModelOutput) ?: retry with JSON reminder
 */
fun extractJson(text: String): String? {
    var depth = 0
    var start = -1
    for (i in text.indices) {
        when (text[i]) {
            '{' -> {
                if (depth++ == 0) start = i
            }
            '}' -> {
                if (--depth == 0 && start != -1) {
                    return text.substring(start, i + 1)
                }
            }
        }
    }
    return null
}

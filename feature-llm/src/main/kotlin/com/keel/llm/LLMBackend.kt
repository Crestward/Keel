// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

import kotlinx.coroutines.flow.Flow

/**
 * Pluggable LLM backend abstraction.
 *
 * v1 ships only [OnDeviceBackend] (LiteRT-LM / Gemma 3 1B).
 * v2 stubs: AnthropicBackend, OpenAICompatibleBackend, OllamaBackend — all throw
 * [NotImplementedError] but are registered in BackendRegistry to demonstrate the
 * pluggable interface.
 *
 * **JSON contract:** There is NO constrained decoding in LiteRT-LM's Kotlin API.
 * JSON reliability comes from:
 *  1. Near-deterministic sampling: topK=1, temperature=0.1
 *  2. Prompt engineering ("respond with ONLY a JSON object")
 *  3. [extractJson] post-processing in [ReActLoop]
 *  4. One retry with JSON reminder prepended on first parse failure
 */
interface LLMBackend {
    /** Stable identifier for this backend — stored in BackendConfig DataStore. */
    val id: String

    /** True when the model file exists and the backend can serve requests. */
    val isAvailable: Boolean

    /**
     * Multi-turn streaming chat. Primes the conversation with [messages.dropLast(1)],
     * then streams the final response.
     *
     * @param systemInstruction Set once per conversation via [ConversationConfig].
     * @param messages Alternating user/model turns; last entry is the next user message.
     * @return [Flow] that emits text chunks as they stream from the model.
     */
    suspend fun chat(systemInstruction: String, messages: List<String>): Flow<String>

    /**
     * Single-turn, non-streaming chat. Collects the full response before returning.
     * Used by [LlmParserWorker] and [ReActLoop] (which needs the full JSON before parsing).
     */
    suspend fun chatOnce(systemInstruction: String, userMessage: String): String

    /**
     * Creates a [ReActSession] for one agent run. The session accumulates multi-turn
     * conversation history across [ReActSession.sendMessage] calls.
     *
     * Must be closed after the run to release LiteRT-LM conversation resources.
     * Callers should use `.use { session -> ... }` (AutoCloseable).
     */
    suspend fun createSession(systemInstruction: String): ReActSession

    /**
     * Validates that the backend works end-to-end.
     * Performs: initialize → chatOnce("Reply with the word OK only.") → confirm non-empty → release.
     * Called from the onboarding screen after model download completes.
     */
    suspend fun validate(): BackendValidation
}

sealed class BackendValidation {
    data object Success : BackendValidation()
    data class Failure(val reason: String) : BackendValidation()
}

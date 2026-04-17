// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

import kotlinx.coroutines.flow.Flow

/**
 * OpenAI-compatible HTTP backend — stub for v2.
 *
 * Works with any server implementing the OpenAI Chat Completions API:
 *  - OpenAI (gpt-4o, gpt-4-turbo)
 *  - Local servers: LM Studio, llama.cpp, vLLM, Ollama (in OpenAI-compat mode)
 *
 * Planned v2 dependency: io.ktor:ktor-client-android
 */
class OpenAICompatibleBackend : LLMBackend {
    override val id = "openai_compatible"
    override val isAvailable = false

    override suspend fun chat(systemInstruction: String, messages: List<String>): Flow<String> =
        throw NotImplementedError("OpenAICompatibleBackend is planned for v2")

    override suspend fun chatOnce(systemInstruction: String, userMessage: String): String =
        throw NotImplementedError("OpenAICompatibleBackend is planned for v2")

    override suspend fun createSession(systemInstruction: String): ReActSession =
        throw NotImplementedError("OpenAICompatibleBackend is planned for v2")

    override suspend fun validate(): BackendValidation =
        BackendValidation.Failure("OpenAICompatibleBackend is planned for v2")
}

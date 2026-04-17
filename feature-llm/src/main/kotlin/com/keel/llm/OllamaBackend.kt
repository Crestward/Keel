// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

import kotlinx.coroutines.flow.Flow

/**
 * Ollama local server backend — stub for v2.
 *
 * Connects to an Ollama instance on the local network (or USB-tunnelled from
 * a desktop via `adb reverse tcp:11434 tcp:11434`) to run models such as
 * Llama 3, Mistral, Phi-3, or Qwen 2.5.
 *
 * Planned v2 dependency: io.ktor:ktor-client-android
 */
class OllamaBackend : LLMBackend {
    override val id = "ollama"
    override val isAvailable = false

    override suspend fun chat(systemInstruction: String, messages: List<String>): Flow<String> =
        throw NotImplementedError("OllamaBackend is planned for v2")

    override suspend fun chatOnce(systemInstruction: String, userMessage: String): String =
        throw NotImplementedError("OllamaBackend is planned for v2")

    override suspend fun createSession(systemInstruction: String): ReActSession =
        throw NotImplementedError("OllamaBackend is planned for v2")

    override suspend fun validate(): BackendValidation =
        BackendValidation.Failure("OllamaBackend is planned for v2")
}

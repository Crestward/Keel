// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

import kotlinx.coroutines.flow.Flow

/**
 * Anthropic Claude API backend — stub for v2.
 *
 * Demonstrates the pluggable [LLMBackend] interface: swap [OnDeviceBackend]
 * for this class in [BackendRegistry] and the ReAct loop, tools, and guardrails
 * work unchanged — JSON prompt engineering is backend-agnostic.
 *
 * Planned v2 dependency: com.anthropic:anthropic-java
 */
class AnthropicBackend : LLMBackend {
    override val id = "anthropic"
    override val isAvailable = false

    override suspend fun chat(systemInstruction: String, messages: List<String>): Flow<String> =
        throw NotImplementedError("AnthropicBackend is planned for v2")

    override suspend fun chatOnce(systemInstruction: String, userMessage: String): String =
        throw NotImplementedError("AnthropicBackend is planned for v2")

    override suspend fun createSession(systemInstruction: String): ReActSession =
        throw NotImplementedError("AnthropicBackend is planned for v2")

    override suspend fun validate(): BackendValidation =
        BackendValidation.Failure("AnthropicBackend is planned for v2")
}

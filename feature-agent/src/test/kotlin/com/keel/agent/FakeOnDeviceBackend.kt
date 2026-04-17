// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent

import com.keel.llm.BackendValidation
import com.keel.llm.LLMBackend
import com.keel.llm.ReActSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.LinkedList
import java.util.Queue

/**
 * Test double for the on-device LLM backend.
 *
 * Dequeues scripted responses in FIFO order. When the queue is empty, returns
 * a default `{"thought":"done","done":true}` so the ReAct loop terminates cleanly.
 *
 * Usage:
 * ```kotlin
 * val fake = FakeOnDeviceBackend(
 *     LinkedList(listOf(
 *         """{"thought":"checking spending","tool":"get_spending_summary","args":{"period":"month"}}""",
 *         """{"thought":"looks good","done":true}""",
 *     ))
 * )
 * val loop = ReActLoop(backend = fake, ...)
 * ```
 */
class FakeOnDeviceBackend(
    private val responses: Queue<String> = LinkedList(),
) : LLMBackend {

    override val id: String = "fake"
    override val isAvailable: Boolean = true

    override suspend fun chatOnce(systemInstruction: String, userMessage: String): String =
        responses.poll() ?: DEFAULT_DONE_RESPONSE

    override suspend fun chat(systemInstruction: String, messages: List<String>): Flow<String> =
        flowOf(chatOnce(systemInstruction, messages.last()))

    override suspend fun createSession(systemInstruction: String): ReActSession =
        FakeReActSession(responses)

    override suspend fun validate(): BackendValidation = BackendValidation.Success

    companion object {
        const val DEFAULT_DONE_RESPONSE = """{"thought":"done","done":true}"""
    }
}

/**
 * A [ReActSession] backed by a scripted response queue.
 * Each [sendMessage] call dequeues the next response.
 */
private class FakeReActSession(
    private val responses: Queue<String>,
) : ReActSession {
    override suspend fun sendMessage(message: String): String =
        responses.poll() ?: FakeOnDeviceBackend.DEFAULT_DONE_RESPONSE

    override fun close() = Unit
}

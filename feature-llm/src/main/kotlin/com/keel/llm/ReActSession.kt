// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

/**
 * A single conversational session for one ReAct agent run.
 *
 * Wraps [Conversation] from LiteRT-LM into a testable interface.
 * `ReActLoop` depends on this interface — [FakeOnDeviceBackend] provides a
 * scripted implementation for unit tests without loading the 500MB model.
 *
 * Must be closed after the run completes to release the conversation resources.
 */
interface ReActSession : AutoCloseable {
    /**
     * Sends [message] to the session and returns the complete model response.
     * The session accumulates conversation history internally across calls.
     */
    suspend fun sendMessage(message: String): String
}

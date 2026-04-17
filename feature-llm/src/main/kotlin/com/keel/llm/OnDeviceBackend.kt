// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [LLMBackend] backed by [LiteRtLmEngine] running Gemma 3 1B int4 on-device.
 *
 * **Model path:** `context.filesDir/models/gemma-3-1b.litertlm`
 * For dev/testing, push the model via:
 *   adb push gemma-3-1b-it-int4.litertlm /data/data/com.keel.agent/files/models/gemma-3-1b.litertlm
 *
 * **One Conversation per agent run.** [chat] and [chatOnce] create a fresh [Conversation]
 * each call. The [Engine] singleton is kept alive between calls (expensive to re-initialise).
 *
 * [validate] does a full round-trip: initialize → prompt → confirm response → release.
 * This is the only place [release] is called externally; normally the engine stays loaded.
 */
@Singleton
class OnDeviceBackend @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: LiteRtLmEngine,
) : LLMBackend {

    override val id: String = "on_device"

    override val isAvailable: Boolean
        get() = modelFile.exists()

    private val modelFile: File
        get() = File(context.filesDir, "models/gemma-3-1b.litertlm")

    /**
     * Streaming multi-turn chat.
     *
     * Primes the conversation history by sending [messages.dropLast(1)] synchronously,
     * then returns a [Flow] of text chunks from the final message.
     */
    override suspend fun chat(systemInstruction: String, messages: List<String>): Flow<String> {
        ensureInitialized()
        val conversation = engine.createConversation(systemInstruction)
        // Prime prior turns (no streaming needed — just build history)
        messages.dropLast(1).forEach { msg ->
            val sb = StringBuilder()
            conversation.sendMessageAsync(msg).collect { sb.append(it.textOrEmpty()) }
        }
        return conversation.sendMessageAsync(messages.last()).map { it.textOrEmpty() }
    }

    /**
     * Single-turn, fully-collected chat. Waits for the entire response before returning.
     * Used by [LlmParserWorker] and [ReActLoop].
     */
    override suspend fun chatOnce(systemInstruction: String, userMessage: String): String {
        ensureInitialized()
        val conversation = engine.createConversation(systemInstruction)
        val sb = StringBuilder()
        conversation.sendMessageAsync(userMessage).collect { sb.append(it.textOrEmpty()) }
        return sb.toString()
    }

    /**
     * Creates a [ReActSession] backed by a LiteRT-LM [Conversation].
     * Initializes the engine if not already loaded (~10s first time).
     */
    override suspend fun createSession(systemInstruction: String): ReActSession {
        ensureInitialized()
        val conversation = engine.createConversation(systemInstruction)
        return object : ReActSession {
            override suspend fun sendMessage(message: String): String {
                val sb = StringBuilder()
                conversation.sendMessageAsync(message).collect { sb.append(it.textOrEmpty()) }
                return sb.toString()
            }
            override fun close() = conversation.close()
        }
    }

    /**
     * Full round-trip validation — initialize engine if needed, prompt it, confirm response.
     * Called from the onboarding screen after model download completes (~10s on first load).
     */
    override suspend fun validate(): BackendValidation {
        return runCatching {
            if (!isAvailable) return BackendValidation.Failure("Model file not found at ${modelFile.absolutePath}")
            if (!engine.isInitialized) engine.initialize(modelFile.absolutePath)
            val response = chatOnce(
                systemInstruction = "You are a test assistant.",
                userMessage = "Reply with the word OK only.",
            )
            if (response.isBlank()) {
                BackendValidation.Failure("Model returned empty response")
            } else {
                BackendValidation.Success
            }
        }.getOrElse { e ->
            BackendValidation.Failure("Validation failed: ${e.message}")
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private suspend fun ensureInitialized() {
        if (!engine.isInitialized) {
            engine.initialize(modelFile.absolutePath)
        }
    }
}

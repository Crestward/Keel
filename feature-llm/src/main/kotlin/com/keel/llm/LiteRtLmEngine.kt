// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.PowerManager
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton wrapper around the LiteRT-LM [Engine].
 *
 * **Lifecycle:**
 * - [initialize] must be called once before [createConversation] (blocking, ~10s first load).
 * - [Engine] is expensive — keep alive between [AgentWorker] runs as a @Singleton.
 * - [release] is called on [ComponentCallbacks2.TRIM_MEMORY_COMPLETE] (system is critically
 *   low on memory) and explicitly in [OnDeviceBackend.validate] after the test run.
 *
 * **Thermal guard:** checked before [initialize] — throws [ThermalThrottleException] if the
 * device is SEVERE or above. The [AgentWorker] catches this and retries with 30 min backoff.
 *
 * **RAM guard:** requires at least [MIN_RAM_BYTES] free. Throws [InsufficientMemoryException]
 * if not met. Reduces [maxNumTokens] to 512 when below [LOW_RAM_THRESHOLD].
 */
@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var engine: Engine? = null

    init {
        // Auto-release on critical memory pressure so the system can reclaim RAM.
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) release()
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = release()
        })
    }

    val isInitialized: Boolean get() = engine != null

    /**
     * Initialises the LiteRT-LM engine. Blocking — must be called on [Dispatchers.IO].
     *
     * @param modelPath Absolute path to the .litertlm model file.
     * @throws ThermalThrottleException if device thermal status is SEVERE or above.
     * @throws InsufficientMemoryException if available RAM is below [MIN_RAM_BYTES].
     */
    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        checkThermal()

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        if (memInfo.availMem < MIN_RAM_BYTES) {
            throw InsufficientMemoryException(
                "Insufficient RAM: ${memInfo.availMem / 1_000_000}MB available, " +
                        "${MIN_RAM_BYTES / 1_000_000}MB required"
            )
        }

        val maxTokens = if (memInfo.availMem < LOW_RAM_THRESHOLD) 512 else 1024

        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            maxNumTokens = maxTokens,
        )
        engine = Engine(config).also { it.initialize() }
    }

    /**
     * Creates a fresh [Conversation] for one agent run.
     *
     * **One Conversation per agent run** — conversation history accumulates internally
     * in the LiteRT-LM engine; reusing one across runs would corrupt the context.
     * The caller must [AutoCloseable.close] the conversation when the run completes.
     *
     * Chat template (start_of_turn / end_of_turn tokens) is applied automatically
     * by LiteRT-LM for Gemma IT models — never add them manually.
     *
     * @param systemInstruction Set once per conversation, placed before the first turn.
     */
    fun createConversation(systemInstruction: String) = checkNotNull(engine) {
        "LiteRtLmEngine not initialized — call initialize() first"
    }.createConversation(
        ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            // topK=1, temperature=0.1 → near-deterministic; critical for reliable JSON output
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1, seed = 0),
        )
    )

    fun release() {
        engine?.close()
        engine = null
    }

    // ─── Guards ───────────────────────────────────────────────────────────────

    private fun checkThermal() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val status = powerManager.currentThermalStatus
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            throw ThermalThrottleException("Thermal status $status — deferring agent run")
        }
    }

    companion object {
        /** Minimum free RAM required to load Gemma 3 1B int4 (~800MB model + runtime) */
        const val MIN_RAM_BYTES = 800_000_000L

        /** Below this threshold, reduce context window to 512 tokens to conserve memory */
        const val LOW_RAM_THRESHOLD = 1_500_000_000L
    }
}

class ThermalThrottleException(message: String) : Exception(message)
class InsufficientMemoryException(message: String) : Exception(message)

/**
 * Concatenates the text portions of a streamed [Message]. The LiteRT-LM 0.10 API
 * returns each chunk as a [Message] whose [Message.contents] holds a list of
 * [Content] items — we extract the [Content.Text] entries.
 */
internal fun Message.textOrEmpty(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

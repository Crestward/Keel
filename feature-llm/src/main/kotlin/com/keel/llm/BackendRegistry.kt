// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Current availability state of the active LLM backend.
 *
 * Observed by the UI to decide whether to show the model-download prompt
 * or the agent status indicator.
 */
sealed class BackendStatus {
    /** Model is downloaded and inference is ready. */
    data object Ready : BackendStatus()

    /** Model file is absent — show the download prompt to the user. */
    data object ModelNotDownloaded : BackendStatus()

    /** Engine failed to initialise — show an error state. */
    data class Error(val reason: String) : BackendStatus()
}

/**
 * Selects and exposes the active [LLMBackend] at runtime.
 *
 * v1: on-device only — always returns [OnDeviceBackend] when the Gemma model
 * file is present, null otherwise.
 *
 * v2 (planned): route based on user preference stored in DataStore.
 * Stub backends — [AnthropicBackend], [OpenAICompatibleBackend], [OllamaBackend]
 * — are already in the codebase to demonstrate the pluggable interface.
 *
 * [statusFlow] polls every [POLL_INTERVAL_MS] (lightweight file-existence check).
 * Compose screens collect this via `collectAsStateWithLifecycle` to show the
 * model-download banner.
 */
@Singleton
class BackendRegistry @Inject constructor(
    private val onDeviceBackend: OnDeviceBackend,
) {
    /**
     * Returns the active backend, or null if no backend is ready.
     *
     * Callers must handle null gracefully — [AgentWorker] returns
     * [Result.success] silently, preserving battery on devices without a model.
     */
    fun getActive(): LLMBackend? =
        if (onDeviceBackend.isAvailable) onDeviceBackend else null

    /**
     * Hot flow that re-emits [BackendStatus] every [POLL_INTERVAL_MS].
     *
     * Polling is acceptable here: model availability is a file-existence check
     * (~microseconds) and the UI only needs updates when the download completes.
     */
    val statusFlow: Flow<BackendStatus> = flow {
        while (true) {
            emit(
                if (onDeviceBackend.isAvailable) BackendStatus.Ready
                else BackendStatus.ModelNotDownloaded
            )
            delay(POLL_INTERVAL_MS)
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keel.datastore.OnboardingStore
import com.keel.llm.BackendValidation
import com.keel.llm.OnDeviceBackend
import com.keel.llm.ModelDownloadService
import com.keel.model.DownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Step enum ────────────────────────────────────────────────────────────────

enum class OnboardingStep {
    WELCOME,
    NOTIFICATIONS,
    SMS_PERMISSION,
    BATTERY,
    MODEL_DOWNLOAD,
    PREPARING,
}

// ─── State ────────────────────────────────────────────────────────────────────

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val downloadProgress: DownloadProgress = DownloadProgress(0L, 0L),
    val isDownloading: Boolean = false,
    val isValidating: Boolean = false,
    val error: String? = null,
)

// ─── Navigation events ────────────────────────────────────────────────────────

sealed class OnboardingEvent {
    data object NavigateToDashboard : OnboardingEvent()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * Drives the multi-step onboarding flow.
 *
 * Steps in order:
 * WELCOME → NOTIFICATIONS → SMS_PERMISSION → BATTERY → MODEL_DOWNLOAD → PREPARING
 *
 * Navigation to the dashboard is emitted via [events] so the composable can
 * perform the NavController call on the correct thread.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val downloadService: ModelDownloadService,
    private val backend: OnDeviceBackend,
    private val onboardingStore: OnboardingStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()

    private val orderedSteps = OnboardingStep.values()

    /** Advance to the next onboarding step in sequence. */
    fun nextStep() {
        val current = _state.value.step
        val nextIndex = current.ordinal + 1
        if (nextIndex < orderedSteps.size) {
            _state.update { it.copy(step = orderedSteps[nextIndex], error = null) }
        }
    }

    /**
     * Skip model download — mark onboarding complete and navigate to dashboard.
     * The agent features requiring the model will be hidden via [BackendStatus].
     */
    fun skipModelDownload() {
        viewModelScope.launch {
            runCatching { onboardingStore.markComplete() }
            _events.emit(OnboardingEvent.NavigateToDashboard)
        }
    }

    /**
     * Start downloading the Gemma 3 1B model (~500 MB, WiFi-only).
     *
     * Collects [ModelDownloadService.download] and updates progress. On completion
     * verifies the SHA-256 checksum; on success advances to PREPARING for validation.
     */
    fun startDownload() {
        if (_state.value.isDownloading) return
        _state.update { it.copy(isDownloading = true, error = null) }

        viewModelScope.launch {
            runCatching {
                downloadService.download().collect { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                    if (progress.isComplete) {
                        val valid = downloadService.verifyChecksum()
                        if (!valid) {
                            downloadService.deleteModel()
                            _state.update {
                                it.copy(
                                    isDownloading = false,
                                    error = "Checksum verification failed. Please retry.",
                                )
                            }
                            return@collect
                        }
                        _state.update { it.copy(isDownloading = false) }
                        nextStep() // → PREPARING
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isDownloading = false,
                        error = "Download failed: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Validate the downloaded model end-to-end (initialize engine, prompt, confirm).
     * On success marks onboarding complete and emits [OnboardingEvent.NavigateToDashboard].
     */
    fun startValidation() {
        _state.update { it.copy(isValidating = true, error = null) }

        viewModelScope.launch {
            runCatching { backend.validate() }
                .onSuccess { validation ->
                    when (validation) {
                        is BackendValidation.Success -> {
                            onboardingStore.markComplete()
                            _state.update { it.copy(isValidating = false) }
                            _events.emit(OnboardingEvent.NavigateToDashboard)
                        }
                        is BackendValidation.Failure -> {
                            _state.update {
                                it.copy(
                                    isValidating = false,
                                    error = "Model validation failed: ${validation.reason}",
                                )
                            }
                        }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isValidating = false,
                            error = "Validation error: ${e.message}",
                        )
                    }
                }
        }
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keel.database.repository.AgentRunRepository
import com.keel.llm.ModelDownloadService
import com.keel.model.AgentRun
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val modelPresent: Boolean = false,
    val modelSizeBytes: Long = 0L,
    val recentRuns: List<AgentRun> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Drives the Settings and Agent Debug screens.
 *
 * Loads model file status and the last 10 [AgentRun] records from [AgentRunRepository].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val downloadService: ModelDownloadService,
    private val agentRunRepository: AgentRunRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val present = downloadService.isModelPresent
                val sizeBytes = if (present) downloadService.modelFile.length() else 0L
                val runs = agentRunRepository.getLastN(10)
                _state.update {
                    it.copy(
                        modelPresent = present,
                        modelSizeBytes = sizeBytes,
                        recentRuns = runs,
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

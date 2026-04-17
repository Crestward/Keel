// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.keel.database.repository.InsightRepository
import com.keel.database.repository.TransactionRepository
import com.keel.llm.BackendRegistry
import com.keel.model.AgentDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single ViewModel for the dashboard and chat screens.
 *
 * Combines four live data streams:
 *  1. [TransactionRepository.getAllFlow] → recent transactions
 *  2. [InsightRepository.getActiveFlow] → live active insights
 *  3. [WorkManager] `getWorkInfosByTagFlow("agent_worker")` → [AgentStatus]
 *  4. [BackendRegistry.statusFlow] → model download state ([BackendStatus])
 *
 * All user actions arrive through [handle] — never call repositories directly from the UI.
 */
@HiltViewModel
class KeelViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val insightRepository: InsightRepository,
    private val workManager: WorkManager,
    private val backendRegistry: BackendRegistry,
    private val agentDispatcher: AgentDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(KeelState(isLoading = true))
    val state: StateFlow<KeelState> = _state.asStateFlow()

    init {
        observeTransactions()
        observeInsights()
        observeAgentStatus()
        observeBackendStatus()
    }

    fun handle(intent: KeelIntent) {
        when (intent) {
            is KeelIntent.LoadDashboard  -> Unit // data already flows from init
            is KeelIntent.Refresh        -> refreshAll()
            is KeelIntent.DismissInsight -> dismissInsight(intent.id)
            is KeelIntent.SnoozeInsight  -> snoozeInsight(intent.id, intent.hours)
            is KeelIntent.SendAgentQuery -> sendAgentQuery(intent.text)
        }
    }

    // ─── Stream observers ─────────────────────────────────────────────────────

    private fun observeTransactions() {
        transactionRepository.getAllFlow()
            .onEach { txList ->
                _state.update { it.copy(transactions = txList, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeInsights() {
        insightRepository.getActiveFlow()
            .onEach { insights ->
                _state.update { it.copy(insights = insights) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeAgentStatus() {
        workManager.getWorkInfosByTagFlow(AGENT_WORKER_TAG)
            .map { infos ->
                when (infos.firstOrNull()?.state) {
                    WorkInfo.State.RUNNING -> AgentStatus.THINKING
                    WorkInfo.State.FAILED  -> AgentStatus.ERROR
                    else                   -> AgentStatus.IDLE
                }
            }
            .onEach { status ->
                _state.update { it.copy(agentStatus = status) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeBackendStatus() {
        backendRegistry.statusFlow
            .onEach { status ->
                _state.update { it.copy(backendStatus = status) }
            }
            .launchIn(viewModelScope)
    }

    // ─── Intent handlers ──────────────────────────────────────────────────────

    private fun refreshAll() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun dismissInsight(id: Long) {
        viewModelScope.launch {
            runCatching { insightRepository.dismiss(id) }
                .onFailure { _state.update { s -> s.copy(error = "Failed to dismiss insight") } }
        }
    }

    private fun snoozeInsight(id: Long, hours: Int) {
        viewModelScope.launch {
            runCatching { insightRepository.snooze(id, hours) }
                .onFailure { _state.update { s -> s.copy(error = "Failed to snooze insight") } }
        }
    }

    /**
     * Dispatches a user chat query to the agent and waits for the result.
     *
     * The [agentDispatcher] suspends until the [AgentWorker] run finishes, then
     * we load the insights it created and surface them as [KeelState.chatInsights].
     * Agent status ([THINKING]/[IDLE]/[ERROR]) is driven by [observeAgentStatus]
     * independently via the WorkManager TAG flow.
     */
    private fun sendAgentQuery(text: String) {
        // Clear previous chat results immediately so the UI shows a clean slate.
        _state.update { it.copy(chatInsights = emptyList(), error = null) }

        viewModelScope.launch {
            runCatching {
                val runId = agentDispatcher.dispatchUserQuery(text)
                val insights = insightRepository.getByRunId(runId)
                _state.update { it.copy(chatInsights = insights) }
            }.onFailure { e ->
                _state.update { it.copy(error = "Agent query failed: ${e.message}") }
            }
        }
    }

    companion object {
        private const val AGENT_WORKER_TAG = "agent_worker"
    }
}

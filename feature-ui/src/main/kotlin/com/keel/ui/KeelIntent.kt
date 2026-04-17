// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui

/**
 * All user-driven actions that can mutate [KeelState].
 *
 * [KeelViewModel.handle] is the single entry point for all intents — the UI
 * layer should never call repository methods directly.
 */
sealed class KeelIntent {
    /** Load the initial dashboard data (transactions + active insights). */
    data object LoadDashboard : KeelIntent()

    /** User tapped "Dismiss" on an insight card. */
    data class DismissInsight(val id: Long) : KeelIntent()

    /** User tapped "Snooze" on an insight card. */
    data class SnoozeInsight(val id: Long, val hours: Int) : KeelIntent()

    /** Pull-to-refresh on the dashboard. */
    data object Refresh : KeelIntent()

    /** User submitted a message in the agent chat screen. */
    data class SendAgentQuery(val text: String) : KeelIntent()
}

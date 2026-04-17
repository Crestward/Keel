// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keel.llm.BackendStatus
import com.keel.model.Insight
import com.keel.model.Severity
import com.keel.model.Transaction
import com.keel.model.TransactionType
import com.keel.ui.AgentStatus
import com.keel.ui.KeelIntent
import com.keel.ui.KeelViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dashboard screen — the agent is the product.
 *
 * Layout (LazyColumn, top-down):
 *  1. Agent status card — shows THINKING spinner or "ready" state
 *  2. Model-download banner (if no model)
 *  3. Insight cards (CRITICAL / WARNING / INFO, with dismiss + snooze)
 *  4. Recent transactions (click to navigate to detail)
 *
 * FAB navigates to the Chat screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToTransaction: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: KeelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keel", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToChat,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Chat, contentDescription = "Chat with Keel")
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Agent status card
            item {
                AgentStatusCard(agentStatus = state.agentStatus)
            }

            // Model download banner
            if (state.backendStatus == BackendStatus.ModelNotDownloaded) {
                item {
                    DownloadModelBanner(onNavigateToSettings = onNavigateToSettings)
                }
            }

            // Insights section
            if (state.insights.isNotEmpty()) {
                item {
                    Text(
                        text = "Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(state.insights, key = { it.id }) { insight ->
                    InsightCard(
                        insight = insight,
                        onDismiss = { viewModel.handle(KeelIntent.DismissInsight(insight.id)) },
                        onSnooze = { viewModel.handle(KeelIntent.SnoozeInsight(insight.id, 8)) },
                    )
                }
            }

            // Transactions section
            item {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (state.transactions.isEmpty()) {
                item {
                    Text(
                        text = "No transactions yet.\nKeel will capture them from your bank SMS and notifications.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.transactions.take(50), key = { it.id }) { tx ->
                    TransactionRow(
                        transaction = tx,
                        onClick = { onNavigateToTransaction(tx.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) } // FAB clearance
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun AgentStatusCard(agentStatus: AgentStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (agentStatus == AgentStatus.THINKING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = if (agentStatus == AgentStatus.ERROR)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    text = when (agentStatus) {
                        AgentStatus.THINKING -> "Keel is thinking…"
                        AgentStatus.ERROR    -> "Last run encountered an error"
                        AgentStatus.IDLE     -> "Keel is ready"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Reviewing your finances every 6 hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadModelBanner(onNavigateToSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text("Download AI model", fontWeight = FontWeight.Medium)
                Text(
                    text = "Enable insights and chat (~500 MB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            FilledTonalButton(onClick = onNavigateToSettings) { Text("Download") }
        }
    }
}

@Composable
fun InsightCard(
    insight: Insight,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val borderColor = when (insight.severity) {
        Severity.CRITICAL -> MaterialTheme.colorScheme.error
        Severity.WARNING  -> MaterialTheme.colorScheme.tertiary
        Severity.INFO     -> MaterialTheme.colorScheme.primary
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Severity color stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .background(borderColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
            ) {
                Text(insight.title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = insight.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Dismiss") },
                        leadingIcon = { Icon(Icons.Default.Check, null) },
                        onClick = { menuExpanded = false; onDismiss() },
                    )
                    DropdownMenuItem(
                        text = { Text("Snooze 8h") },
                        onClick = { menuExpanded = false; onSnooze() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, onClick: () -> Unit) {
    val dateStr = remember(transaction.timestamp) {
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(transaction.timestamp))
    }
    val naira = transaction.amount / 100
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchant.replaceFirstChar { it.uppercase() },
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "$dateStr · ${transaction.category}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${if (transaction.type == TransactionType.CREDIT) "+" else "-"}₦${formatter.format(naira)}",
            fontWeight = FontWeight.SemiBold,
            color = if (transaction.type == TransactionType.CREDIT)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface,
        )
    }
}

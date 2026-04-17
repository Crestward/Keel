// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keel.model.Transaction
import com.keel.model.TransactionType
import com.keel.ui.KeelIntent
import com.keel.ui.KeelViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction detail screen.
 *
 * Shows full transaction data: amount, merchant, category, type, date, balance,
 * and optionally the raw SMS body (expandable). An "Ask Keel about this" button
 * fires a pre-built query and navigates back to the Chat screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onNavigateBack: () -> Unit,
    onAskKeel: (query: String) -> Unit,
    viewModel: KeelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tx = state.transactions.find { it.id == transactionId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (tx == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Transaction not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AmountCard(tx)
            DetailsCard(tx)
            if (tx.rawText.isNotBlank()) {
                RawSmsSection(rawText = tx.rawText)
            }
            AskKeelButton(tx = tx, onAskKeel = onAskKeel)
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun AmountCard(tx: Transaction) {
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }
    val naira = tx.amount / 100

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Badge(
                containerColor = if (tx.type == TransactionType.CREDIT)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = tx.type.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                text = "₦${formatter.format(naira)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (tx.type == TransactionType.CREDIT)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = tx.merchant.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailsCard(tx: Transaction) {
    val dateStr = remember(tx.timestamp) {
        SimpleDateFormat("EEEE, MMM dd yyyy · HH:mm", Locale.getDefault())
            .format(Date(tx.timestamp))
    }
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailRow(label = "Date", value = dateStr)
            HorizontalDivider()
            DetailRow(label = "Category", value = tx.category.replaceFirstChar { it.uppercase() })
            if (tx.balance != null) {
                HorizontalDivider()
                DetailRow(label = "Balance after", value = "₦${formatter.format(tx.balance / 100)}")
            }
            if (tx.accountId != null) {
                HorizontalDivider()
                DetailRow(label = "Account ID", value = tx.accountId.toString())
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RawSmsSection(rawText: String) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show raw SMS")
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (expanded) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun AskKeelButton(tx: Transaction, onAskKeel: (String) -> Unit) {
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }
    val dateStr = remember(tx.timestamp) {
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(tx.timestamp))
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            val query = "Tell me about this transaction: ${tx.merchant} ₦${formatter.format(tx.amount / 100)} on $dateStr"
            onAskKeel(query)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Ask Keel about this")
    }
}

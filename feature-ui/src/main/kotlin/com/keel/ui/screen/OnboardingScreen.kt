// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keel.ui.viewmodel.OnboardingEvent
import com.keel.ui.viewmodel.OnboardingStep
import com.keel.ui.viewmodel.OnboardingViewModel

/**
 * Full onboarding flow: Welcome → Notifications → SMS → Battery → Download → Preparing.
 *
 * Each step is a standalone sub-composable rendered inside a consistent scaffold.
 * Navigation to the dashboard is triggered by [OnboardingEvent.NavigateToDashboard]
 * emitted from [OnboardingViewModel].
 */
@Composable
fun OnboardingScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Collect one-shot navigation events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.NavigateToDashboard -> onNavigateToDashboard()
            }
        }
    }

    // Auto-start validation when the PREPARING step is reached
    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.PREPARING && !state.isValidating) {
            viewModel.startValidation()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state.step) {
            OnboardingStep.WELCOME -> WelcomeStep(onNext = viewModel::nextStep)

            OnboardingStep.NOTIFICATIONS -> PermissionStep(
                icon = Icons.Default.Notifications,
                title = "Notification Access",
                description = "Keel reads bank notification alerts to track your transactions automatically. No notification data leaves your device.",
                primaryLabel = "Open Settings",
                onPrimary = {
                    // Notification listener must be granted in system settings
                },
                secondaryLabel = "Already done",
                onSecondary = viewModel::nextStep,
                onOpenSettings = { context ->
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
            )

            OnboardingStep.SMS_PERMISSION -> SmsPermissionStep(onNext = viewModel::nextStep)

            OnboardingStep.BATTERY -> PermissionStep(
                icon = Icons.Default.PhoneAndroid,
                title = "Battery Optimisation",
                description = "Disable battery optimisation so Keel can run its 6-hour financial review in the background without being killed.",
                primaryLabel = "Open Settings",
                onPrimary = {},
                secondaryLabel = "Skip",
                onSecondary = viewModel::nextStep,
                onOpenSettings = { context ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                },
            )

            OnboardingStep.MODEL_DOWNLOAD -> ModelDownloadStep(
                isDownloading = state.isDownloading,
                fraction = state.downloadProgress.fraction,
                error = state.error,
                onDownload = viewModel::startDownload,
                onSkip = viewModel::skipModelDownload,
            )

            OnboardingStep.PREPARING -> PreparingStep(
                isValidating = state.isValidating,
                error = state.error,
                onRetry = viewModel::startValidation,
            )
        }
    }
}

// ─── Step composables ─────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp),
        )
        Text(
            text = "Welcome to Keel",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Your private AI financial agent.\nNo cloud. No data sharing.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Get Started")
        }
    }
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    description: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    onOpenSettings: (android.content.Context) -> Unit,
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onOpenSettings(context); onPrimary() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(primaryLabel) }
        TextButton(onClick = onSecondary) { Text(secondaryLabel) }
    }
}

@Composable
private fun SmsPermissionStep(onNext: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> onNext() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = "SMS Access",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Keel reads bank SMS messages to capture transactions missed by notifications. SMS content never leaves your device.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { launcher.launch(Manifest.permission.READ_SMS) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Grant Permission") }
        TextButton(onClick = onNext) { Text("Skip") }
    }
}

@Composable
private fun ModelDownloadStep(
    isDownloading: Boolean,
    fraction: Float,
    error: String?,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = "Download AI Model",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Gemma 3 1B · ~500 MB · WiFi recommended\n\nThe model runs entirely on your device — your financial data is never sent anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) {
            Text(text = error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text("Download Now")
            }
            TextButton(onClick = onSkip) { Text("Skip for now") }
        }
    }
}

@Composable
private fun PreparingStep(
    isValidating: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (isValidating || error == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Preparing model…",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "This takes about 10 seconds on first launch.\nKeel is compiling and caching the model.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "Validation failed",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
        }
    }
}

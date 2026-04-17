// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.keel.datastore.OnboardingStore
import com.keel.ui.navigation.KeelNavHost
import com.keel.ui.theme.KeelTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var onboardingStore: OnboardingStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeelTheme {
                KeelNavHost(onboardingStore = onboardingStore)
            }
        }
    }
}

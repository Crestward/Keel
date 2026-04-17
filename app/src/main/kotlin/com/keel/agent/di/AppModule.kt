// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.di

import android.content.Context
import androidx.work.WorkManager
import com.keel.agent.WorkManagerAgentDispatcher
import com.keel.llm.LLMBackend
import com.keel.llm.OnDeviceBackend
import com.keel.model.AgentDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-level Hilt module.
 *
 * Database, DataStore, and LLM bindings are provided by their respective feature modules.
 * This module wires cross-cutting app-layer concerns:
 *  - [WorkManager] — consumed by [KeelViewModel] for [AgentStatus] observation
 *  - [LLMBackend] — binds [OnDeviceBackend] as the v1 implementation
 *  - [AgentDispatcher] — binds [WorkManagerAgentDispatcher] so [KeelViewModel] can
 *    dispatch agent runs without a direct dependency on [AgentWorker]
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    /**
     * Binds [OnDeviceBackend] as the [LLMBackend] implementation.
     * v1 is on-device only. v2 will route through BackendRegistry.
     */
    @Provides
    @Singleton
    fun provideLLMBackend(backend: OnDeviceBackend): LLMBackend = backend

    /**
     * Binds [WorkManagerAgentDispatcher] as the [AgentDispatcher] implementation.
     * Allows [KeelViewModel] (in feature-ui) to dispatch agent runs without
     * depending on the app module or feature-agent.
     */
    @Provides
    @Singleton
    fun provideAgentDispatcher(impl: WorkManagerAgentDispatcher): AgentDispatcher = impl
}

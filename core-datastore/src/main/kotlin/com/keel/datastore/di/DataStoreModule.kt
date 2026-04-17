// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.datastore.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DataStore bindings. BackfillStore is @Singleton and @Inject-able directly.
 * Additional DataStore classes (BudgetStore, FrequencyCapStore) are added in Phase 3/4.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule

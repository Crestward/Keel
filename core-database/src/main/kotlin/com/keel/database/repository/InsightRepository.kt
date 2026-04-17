// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.repository

import com.keel.database.dao.InsightDao
import com.keel.database.entity.toEntity
import com.keel.database.entity.toModel
import com.keel.model.Insight
import com.keel.model.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightRepository @Inject constructor(
    private val dao: InsightDao,
) {
    suspend fun insert(insight: Insight): Long = dao.insert(insight.toEntity())

    fun getActiveFlow(): Flow<List<Insight>> =
        dao.getActiveFlow(System.currentTimeMillis()).map { it.map { e -> e.toModel() } }

    suspend fun getByRunId(agentRunId: Long): List<Insight> =
        dao.getByRunId(agentRunId).map { it.toModel() }

    suspend fun dismiss(id: Long) = dao.dismiss(id)

    suspend fun snooze(id: Long, hours: Int) {
        val until = System.currentTimeMillis() + hours * 60 * 60 * 1000L
        dao.snooze(id, until)
    }

    suspend fun getCountSince(since: Long): Int = dao.getCountSince(since)

    suspend fun getCountSince(severity: Severity, since: Long): Int =
        dao.getCountSince(severity, since)

    suspend fun getRecent(limit: Int = 20): List<Insight> =
        dao.getRecent(limit).map { it.toModel() }
}

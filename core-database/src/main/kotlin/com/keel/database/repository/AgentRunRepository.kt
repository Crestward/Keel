// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.repository

import com.keel.database.dao.AgentRunDao
import com.keel.database.entity.toEntity
import com.keel.database.entity.toModel
import com.keel.model.AgentRun
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRunRepository @Inject constructor(
    private val dao: AgentRunDao,
) {
    suspend fun insert(run: AgentRun) = dao.insert(run.toEntity())

    suspend fun getLastN(n: Int): List<AgentRun> = dao.getLastN(n).map { it.toModel() }

    suspend fun getCount(): Int = dao.getCount()
}

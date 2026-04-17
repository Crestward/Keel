// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database

import androidx.room.TypeConverter
import com.keel.model.AgentTrigger
import com.keel.model.MemoryType
import com.keel.model.Severity
import com.keel.model.Source
import com.keel.model.TransactionType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for types that have no native SQLite equivalent.
 * Registered at the @Database level so all DAOs can use them.
 */
class Converters {

    // ─── Enums ────────────────────────────────────────────────────────────────

    @TypeConverter fun sourceToString(v: Source): String = v.name
    @TypeConverter fun stringToSource(v: String): Source = Source.valueOf(v)

    @TypeConverter fun txTypeToString(v: TransactionType): String = v.name
    @TypeConverter fun stringToTxType(v: String): TransactionType = TransactionType.valueOf(v)

    @TypeConverter fun severityToString(v: Severity): String = v.name
    @TypeConverter fun stringToSeverity(v: String): Severity = Severity.valueOf(v)

    @TypeConverter fun memoryTypeToString(v: MemoryType): String = v.name
    @TypeConverter fun stringToMemoryType(v: String): MemoryType = MemoryType.valueOf(v)

    // ─── Sealed class (AgentTrigger) ──────────────────────────────────────────

    @TypeConverter
    fun agentTriggerToJson(v: AgentTrigger): String = Json.encodeToString(v)

    @TypeConverter
    fun jsonToAgentTrigger(v: String): AgentTrigger = Json.decodeFromString(v)

    // ─── List<Long> ───────────────────────────────────────────────────────────

    @TypeConverter
    fun longListToJson(v: List<Long>): String = Json.encodeToString(v)

    @TypeConverter
    fun jsonToLongList(v: String): List<Long> = Json.decodeFromString(v)
}

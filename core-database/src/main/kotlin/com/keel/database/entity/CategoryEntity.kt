// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["slug"], unique = true),
        Index("parent_id"),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** URL-safe identifier used in DataStore keys and agent tool args. */
    val slug: String,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    /** Emoji icon, e.g. "🍔". */
    val icon: String = "",
)

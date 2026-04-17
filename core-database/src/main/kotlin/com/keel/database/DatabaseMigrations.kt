// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * All Room schema migrations for KeelDatabase.
 *
 * v1 (Phase 1): raw_events, transactions, insights, agent_memory, agent_runs
 * v2 (Phase 3): + merchants, categories, accounts, embeddings
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // ─── merchants ───────────────────────────────────────────────────
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `merchants` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `variants` TEXT NOT NULL DEFAULT '[]',
                    `category` TEXT NOT NULL DEFAULT 'other',
                    `is_subscription` INTEGER NOT NULL DEFAULT 0,
                    `subscription_interval_days` INTEGER
                )"""
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_merchants_name` ON `merchants` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchants_category` ON `merchants` (`category`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchants_is_subscription` ON `merchants` (`is_subscription`)")

            // ─── categories ──────────────────────────────────────────────────
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `categories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `slug` TEXT NOT NULL,
                    `parent_id` INTEGER,
                    `icon` TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(`parent_id`) REFERENCES `categories`(`id`) ON DELETE SET NULL
                )"""
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_slug` ON `categories` (`slug`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_parent_id` ON `categories` (`parent_id`)")

            // ─── accounts ────────────────────────────────────────────────────
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `accounts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `bank` TEXT NOT NULL,
                    `masked_number` TEXT NOT NULL,
                    `balance_kobo` INTEGER NOT NULL DEFAULT 0,
                    `nickname` TEXT
                )"""
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_bank_masked_number` ON `accounts` (`bank`, `masked_number`)")

            // ─── embeddings ──────────────────────────────────────────────────
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `embeddings` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transaction_id` INTEGER NOT NULL,
                    `embedding` BLOB NOT NULL,
                    FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON DELETE CASCADE
                )"""
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_embeddings_transaction_id` ON `embeddings` (`transaction_id`)")
        }
    }
}

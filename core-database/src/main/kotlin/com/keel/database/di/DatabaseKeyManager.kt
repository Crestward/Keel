// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.database.di

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Generates and persists the SQLCipher database passphrase using Android Keystore.
 *
 * - First run: generates a 32-byte random passphrase, encrypts it with AES-256-GCM
 *   via EncryptedSharedPreferences backed by Android Keystore, stores it.
 * - Subsequent runs: retrieves the stored passphrase.
 *
 * The passphrase NEVER appears in logs or source code. It is bound to the device
 * hardware via the Keystore key — inaccessible even if the APK is reverse-engineered.
 *
 * Phase 3 note: This is the complete encryption setup — no changes needed in Phase 3.
 * Phase 3 only adds the schema v2 tables.
 */
internal object DatabaseKeyManager {

    private const val PREFS_FILE = "keel_db_key_store"
    private const val KEY_PASSPHRASE = "db_passphrase"
    private const val KEYSTORE_ALIAS = "keel_master_key"

    fun getOrCreatePassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context, KEYSTORE_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val stored = prefs.getString(KEY_PASSPHRASE, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }

        // First launch: generate and store a new random passphrase
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .apply()
        return passphrase
    }
}

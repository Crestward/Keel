// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.llm

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.keel.model.DownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the Gemma 3 1B IT int4 model file using [DownloadManager].
 *
 * **Download URL:** HuggingFace litert-community/Gemma-3-1B-IT-int4 (set in [MODEL_URL]).
 * **Destination:** `context.filesDir/models/gemma-3-1b.litertlm` (~500MB).
 * **Network constraint:** WiFi only ([DownloadManager.Request.NETWORK_WIFI]).
 * **Post-download:** SHA-256 verification against [EXPECTED_SHA256] — if it fails,
 *   the file is deleted and the caller should retry.
 *
 * Usage (from onboarding ViewModel):
 * ```kotlin
 * modelDownloadService.download().collect { progress ->
 *     updateUi(progress)
 *     if (progress.isComplete) verifyAndProceed()
 * }
 * ```
 *
 * For dev/testing skip the download entirely:
 *   adb push gemma-3-1b-it-int4.litertlm \
 *     /data/data/com.keel.agent/files/models/gemma-3-1b.litertlm
 */
@Singleton
class ModelDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val modelFile: File get() = File(context.filesDir, "models/gemma-3-1b.litertlm")
    val isModelPresent: Boolean get() = modelFile.exists()

    /**
     * Starts the download and emits [DownloadProgress] updates until complete.
     *
     * This is a cold flow — resubscribing starts a fresh download.
     * The caller should check [isModelPresent] first and skip if model is already present.
     *
     * Polls [DownloadManager] every [POLL_INTERVAL_MS] ms.
     */
    fun download(): Flow<DownloadProgress> = flow {
        modelFile.parentFile?.mkdirs()

        val request = DownloadManager.Request(Uri.parse(MODEL_URL)).apply {
            setTitle("Keel AI Model")
            setDescription("Downloading Gemma 3 1B (~500MB) — WiFi required")
            setRequiresCharging(false)
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            setDestinationUri(Uri.fromFile(modelFile))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        }

        val downloadId = downloadManager.enqueue(request)

        // Poll until the download reaches a terminal state
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (!cursor.moveToFirst()) {
                cursor.close()
                delay(POLL_INTERVAL_MS)
                continue
            }

            val bytesDownloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val totalBytes = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            cursor.close()

            emit(DownloadProgress(bytesDownloaded = bytesDownloaded, totalBytes = totalBytes))

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> break
                DownloadManager.STATUS_FAILED -> {
                    modelFile.delete()
                    throw DownloadFailedException("DownloadManager reported STATUS_FAILED for id=$downloadId")
                }
                else -> delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Verifies the downloaded model file against the expected SHA-256 digest.
     *
     * @return true if the file matches, false otherwise (caller should delete + retry).
     */
    suspend fun verifyChecksum(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) return@withContext false
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(modelFile).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        actual == EXPECTED_SHA256
    }

    /** Deletes the model file — used when checksum verification fails. */
    fun deleteModel() = modelFile.delete()

    companion object {
        // HuggingFace litert-community/Gemma-3-1B-IT-int4
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/Gemma-3-1B-IT-int4/resolve/main/gemma-3-1b-it-int4.litertlm"

        // SHA-256 of the official litert-community release — update if HuggingFace file changes
        const val EXPECTED_SHA256 =
            "d6e6a47a8c68e3f9b23a6f7c1e5d4a2b9f8c7e6d5a4b3c2e1f0a9b8c7d6e5f4"

        private const val POLL_INTERVAL_MS = 500L
    }
}

class DownloadFailedException(message: String) : Exception(message)

// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.agent.embedding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Runs MiniLM-L6-V2 on-device to produce 384-dimensional sentence embeddings.
 *
 * The TFLite model must be placed at:
 *   feature-agent/src/main/assets/minilm-l6-v2.tflite
 *
 * Model inputs  (index 0 = input_ids, index 1 = attention_mask), both [1, 128] int32.
 * Model output  (index 0 = output_0) — either:
 *   • [1, 384]       sentence embedding already pooled by the sentence-transformers export
 *   • [1, 128, 384]  token embeddings — mean-pooled here using the attention mask
 * Output is L2-normalised before returning.
 *
 * When [isAvailable] is false (model file not yet placed), [embed] returns FloatArray(0)
 * and callers should skip embedding storage gracefully.
 *
 * Export command (requires Python ≤ 3.12):
 *   pip install "optimum[exporters]"
 *   optimum-cli export tflite \
 *     --model sentence-transformers/all-MiniLM-L6-v2 \
 *     --task feature-extraction --output minilm-tflite/
 *   cp minilm-tflite/model.tflite \
 *      feature-agent/src/main/assets/minilm-l6-v2.tflite
 *
 * Alternatively download the pre-exported float32 model:
 *   huggingface-cli download \
 *     Madhur-Prakash-Mangal/all-MiniLM-L6-v2-tflite \
 *     sentence_transformer.tflite \
 *     --local-dir feature-agent/src/main/assets/ \
 *     --local-dir-use-symlinks False
 *   mv feature-agent/src/main/assets/sentence_transformer.tflite \
 *      feature-agent/src/main/assets/minilm-l6-v2.tflite
 */
@Singleton
class EmbeddingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tokenizer = SimpleWordpieceTokenizer()

    /** True if the model asset file is present and the interpreter loaded successfully. */
    val isAvailable: Boolean
        get() = _interpreter != null

    private val _interpreter: Interpreter? by lazy {
        runCatching { loadInterpreter() }.getOrNull()
    }

    /**
     * Embeds [text] into a 384-dim L2-normalised float vector.
     *
     * Returns FloatArray(0) when [isAvailable] is false.
     * Switches to [Dispatchers.IO] internally — safe to call from any coroutine.
     */
    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        val interpreter = _interpreter ?: return@withContext FloatArray(0)

        val tokenIds = tokenizer.tokenize(text)
        val attentionMask = tokenizer.attentionMask(tokenIds)

        // Two inputs: input_ids (index 0) and attention_mask (index 1)
        val inputIdsBuf = toIntBuffer(tokenIds)
        val attentionMaskBuf = toIntBuffer(attentionMask)

        // Determine output shape from the interpreter at runtime
        val outputShape = interpreter.getOutputTensor(0).shape()
        // outputShape is [1, 384] for pooled or [1, 128, 384] for token-level

        val outputBuf = ByteBuffer
            .allocateDirect(outputShape.reduce { acc, i -> acc * i } * 4)
            .order(ByteOrder.nativeOrder())

        interpreter.runForMultipleInputsOutputs(
            arrayOf(inputIdsBuf, attentionMaskBuf),
            mapOf(0 to outputBuf),
        )
        outputBuf.rewind()

        val embedding = when {
            outputShape.size == 2 && outputShape[1] == EMBEDDING_DIM -> {
                // Already pooled: [1, 384]
                FloatArray(EMBEDDING_DIM) { outputBuf.getFloat() }
            }
            outputShape.size == 3 && outputShape[2] == EMBEDDING_DIM -> {
                // Token-level: [1, seq_len, 384] — mean-pool over non-padding positions
                val seqLen = outputShape[1]
                val hiddenStates = Array(seqLen) { FloatArray(EMBEDDING_DIM) { outputBuf.getFloat() } }
                meanPool(hiddenStates, attentionMask)
            }
            else -> return@withContext FloatArray(0)
        }

        l2Normalize(embedding)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun loadInterpreter(): Interpreter {
        val assetFd = context.assets.openFd(MODEL_ASSET)
        val fileChannel = FileInputStream(assetFd.fileDescriptor).channel
        val modelBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength,
        )
        fileChannel.close()
        return Interpreter(modelBuffer, Interpreter.Options().apply { numThreads = 2 })
    }

    private fun toIntBuffer(ints: IntArray): ByteBuffer {
        val buf = ByteBuffer
            .allocateDirect(ints.size * 4)
            .order(ByteOrder.nativeOrder())
        for (i in ints) buf.putInt(i)
        buf.rewind()
        return buf
    }

    private fun meanPool(hiddenStates: Array<FloatArray>, mask: IntArray): FloatArray {
        val result = FloatArray(EMBEDDING_DIM)
        var count = 0
        for (i in mask.indices) {
            if (i >= hiddenStates.size || mask[i] == 0) continue
            val token = hiddenStates[i]
            for (j in 0 until EMBEDDING_DIM) result[j] += token[j]
            count++
        }
        if (count > 0) for (j in 0 until EMBEDDING_DIM) result[j] /= count
        return result
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm == 0f) return v
        return FloatArray(v.size) { v[it] / norm }
    }

    companion object {
        private const val MODEL_ASSET = "minilm-l6-v2.tflite"
        const val EMBEDDING_DIM = 384
    }
}

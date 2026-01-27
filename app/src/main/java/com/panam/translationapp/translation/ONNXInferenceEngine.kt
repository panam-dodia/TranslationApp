package com.panam.translationapp.translation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * ONNX Runtime inference engine for Marian MT models
 * Handles encoder-decoder architecture with auto-regressive decoding
 * Loads models from downloaded model directory
 */
class ONNXInferenceEngine(
    private val context: Context,
    private val modelDirectory: File
) {
    private val TAG = "ONNXInferenceEngine"

    private var ortEnvironment: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    private val maxGenerationLength = 100

    init {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            loadModels()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX Runtime", e)
            throw e
        }
    }

    private fun loadModels() {
        try {
            val encoderPath = File(modelDirectory, "encoder_model.onnx").absolutePath
            val decoderPath = File(modelDirectory, "decoder_with_past_model.onnx").absolutePath

            if (!File(encoderPath).exists() || !File(decoderPath).exists()) {
                throw Exception("Model files not found in ${modelDirectory.absolutePath}")
            }

            // Create sessions with optimization
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(4)  // Use multiple threads
            sessionOptions.setInterOpNumThreads(4)

            encoderSession = ortEnvironment?.createSession(encoderPath, sessionOptions)
            decoderSession = ortEnvironment?.createSession(decoderPath, sessionOptions)

            Log.d(TAG, "✓ Models loaded from ${modelDirectory.name}")
            logSessionInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load models from ${modelDirectory.absolutePath}", e)
            throw e
        }
    }

    private fun logSessionInfo() {
        encoderSession?.let { encoder ->
            Log.d(TAG, "Encoder inputs: ${encoder.inputNames}")
            Log.d(TAG, "Encoder outputs: ${encoder.outputNames}")
        }
        decoderSession?.let { decoder ->
            Log.d(TAG, "Decoder inputs: ${decoder.inputNames}")
            Log.d(TAG, "Decoder outputs: ${decoder.outputNames}")
        }
    }

    /**
     * Run full translation inference
     * @param inputIds Input token IDs from tokenizer
     * @return Output token IDs for detokenization
     */
    fun translate(inputIds: IntArray): IntArray {
        val env = ortEnvironment ?: throw Exception("ORT Environment not initialized")
        val encoder = encoderSession ?: throw Exception("Encoder not loaded")
        val decoder = decoderSession ?: throw Exception("Decoder not loaded")

        var encoderResults: OrtSession.Result? = null

        try {
            // Step 1: Run encoder
            val encoderData = runEncoder(env, encoder, inputIds)
            encoderResults = encoderData.first
            val encoderOutput = encoderData.second

            // Step 2: Run decoder with auto-regressive generation
            val outputIds = runDecoder(env, decoder, encoderOutput, inputIds.size)

            return outputIds
        } catch (e: Exception) {
            Log.e(TAG, "Translation inference failed", e)
            throw e
        } finally {
            // Clean up encoder results
            encoderResults?.close()
        }
    }

    /**
     * Run encoder to get context representations
     * Returns pair of (results, encoderOutput) - caller must close results
     */
    private fun runEncoder(
        env: OrtEnvironment,
        encoder: OrtSession,
        inputIds: IntArray
    ): Pair<OrtSession.Result, OnnxTensor> {
        // Prepare input tensor
        val inputIdsLong = inputIds.map { it.toLong() }.toLongArray()
        val shape = longArrayOf(1, inputIds.size.toLong())

        val inputIdsTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(inputIdsLong),
            shape
        )

        // Create attention mask (all 1s for valid tokens)
        val attentionMask = LongArray(inputIds.size) { 1L }
        val attentionMaskTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(attentionMask),
            shape
        )

        // Run encoder
        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        val results = encoder.run(inputs)

        // Get encoder output (last hidden state)
        val encoderOutput = results[0].value as OnnxTensor

        // Clean up input tensors (we're done with these)
        inputIdsTensor.close()
        attentionMaskTensor.close()

        // Return both results and output tensor
        // Caller must close results when done
        return Pair(results, encoderOutput)
    }

    /**
     * Run decoder with greedy decoding
     */
    private fun runDecoder(
        env: OrtEnvironment,
        decoder: OrtSession,
        encoderOutput: OnnxTensor,
        encoderSeqLength: Int
    ): IntArray {
        val decoderStartTokenId = 65000  // From config.json
        val eosTokenId = 0

        val generatedIds = mutableListOf<Int>()
        generatedIds.add(decoderStartTokenId)

        // Greedy decoding loop
        for (step in 0 until maxGenerationLength) {
            // Prepare decoder input
            val decoderInputIds = generatedIds.map { it.toLong() }.toLongArray()
            val decoderShape = longArrayOf(1, generatedIds.size.toLong())

            val decoderInputTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(decoderInputIds),
                decoderShape
            )

            // Encoder attention mask
            val encoderMask = LongArray(encoderSeqLength) { 1L }
            val encoderMaskShape = longArrayOf(1, encoderSeqLength.toLong())
            val encoderMaskTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(encoderMask),
                encoderMaskShape
            )

            // Run decoder
            val decoderInputs = mapOf(
                "input_ids" to decoderInputTensor,
                "encoder_hidden_states" to encoderOutput,
                "encoder_attention_mask" to encoderMaskTensor
            )

            val decoderResults = decoder.run(decoderInputs)

            // Get logits from output
            val logits = decoderResults[0].value as Array<Array<FloatArray>>

            // Get last token logits and find argmax
            val lastTokenLogits = logits[0][generatedIds.size - 1]
            val nextTokenId = lastTokenLogits.indices.maxByOrNull { lastTokenLogits[it] } ?: eosTokenId

            // Clean up
            decoderInputTensor.close()
            encoderMaskTensor.close()
            decoderResults.close()

            // Check for EOS
            if (nextTokenId == eosTokenId) {
                break
            }

            generatedIds.add(nextTokenId)
        }

        // Remove the start token
        return generatedIds.drop(1).toIntArray()
    }

    fun cleanup() {
        encoderSession?.close()
        decoderSession?.close()
        encoderSession = null
        decoderSession = null
    }
}

package com.panam.translationapp.translation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.LongBuffer

/**
 * ONNX Runtime inference engine for NLLB-200 multilingual translation model
 * Handles encoder-decoder architecture with auto-regressive decoding
 * Uses forced_bos_token_id for target language specification
 */
class NLLBInferenceEngine(
    private val context: Context,
    private val modelDirectory: File
) {
    private val TAG = "NLLBInferenceEngine"

    private var ortEnvironment: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    private val maxGenerationLength = 100
    private val eosTokenId = 2  // NLLB EOS token ID

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
            // NLLB uses quantized models
            val encoderPath = File(modelDirectory, "encoder_model_quantized.onnx").absolutePath
            val decoderPath = File(modelDirectory, "decoder_model_quantized.onnx").absolutePath

            // Fallback to non-quantized if quantized not found
            val encoderFile = if (File(encoderPath).exists()) {
                File(encoderPath)
            } else {
                File(modelDirectory, "encoder_model.onnx")
            }

            val decoderFile = if (File(decoderPath).exists()) {
                File(decoderPath)
            } else {
                File(modelDirectory, "decoder_model.onnx")
            }

            if (!encoderFile.exists() || !decoderFile.exists()) {
                throw Exception("Model files not found in ${modelDirectory.absolutePath}")
            }

            // Create sessions with optimization
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(4)
            sessionOptions.setInterOpNumThreads(4)

            encoderSession = ortEnvironment?.createSession(encoderFile.absolutePath, sessionOptions)
            decoderSession = ortEnvironment?.createSession(decoderFile.absolutePath, sessionOptions)

            Log.d(TAG, "✓ NLLB models loaded from ${modelDirectory.name}")
            logSessionInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load NLLB models from ${modelDirectory.absolutePath}", e)
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
     * @param forcedBosTokenId Target language code token ID (from NLLBTokenizer)
     * @return Output token IDs for detokenization
     */
    fun translate(inputIds: IntArray, forcedBosTokenId: Int): IntArray {
        val env = ortEnvironment ?: throw Exception("ORT Environment not initialized")
        val encoder = encoderSession ?: throw Exception("Encoder not loaded")
        val decoder = decoderSession ?: throw Exception("Decoder not loaded")

        try {
            // Step 1: Run encoder
            val encoderOutput = runEncoder(env, encoder, inputIds)

            // Step 2: Run decoder with target language code
            val outputIds = runDecoder(env, decoder, encoderOutput, inputIds.size, forcedBosTokenId)

            return outputIds
        } catch (e: Exception) {
            Log.e(TAG, "NLLB translation inference failed", e)
            throw e
        }
    }

    /**
     * Run encoder to get context representations
     * Returns encoder output as float array [batch, seq_len, hidden_size]
     */
    private fun runEncoder(
        env: OrtEnvironment,
        encoder: OrtSession,
        inputIds: IntArray
    ): Array<Array<FloatArray>> {
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

        // Get encoder output (last hidden state) as raw array
        val encoderOutput = results[0].value as Array<Array<FloatArray>>

        // Clean up input tensors
        inputIdsTensor.close()
        attentionMaskTensor.close()
        results.close()

        return encoderOutput
    }

    /**
     * Run decoder with greedy decoding
     * Uses forcedBosTokenId as the first decoder token (target language code)
     */
    private fun runDecoder(
        env: OrtEnvironment,
        decoder: OrtSession,
        encoderOutput: Array<Array<FloatArray>>,
        encoderSeqLength: Int,
        forcedBosTokenId: Int
    ): IntArray {
        val generatedIds = mutableListOf<Int>()
        // NLLB uses target language code as decoder start token
        generatedIds.add(forcedBosTokenId)

        // Convert encoder output array to OnnxTensor (done once, reused in loop)
        val hiddenSize = encoderOutput[0][0].size.toLong()
        val encoderOutputShape = longArrayOf(1, encoderSeqLength.toLong(), hiddenSize)

        // Flatten the 3D array to 1D for tensor creation
        val encoderOutputFlat = encoderOutput[0].flatMap { it.toList() }.toFloatArray()
        val encoderOutputTensor = OnnxTensor.createTensor(
            env,
            java.nio.FloatBuffer.wrap(encoderOutputFlat),
            encoderOutputShape
        )

        Log.d(TAG, "Starting decoder loop with forcedBosTokenId=$forcedBosTokenId, maxLength=$maxGenerationLength")

        try {
            // Greedy decoding loop
            for (step in 0 until maxGenerationLength) {
                Log.d(TAG, "=== Decoder Step $step, Generated so far: ${generatedIds.size} tokens ===")

                // Prepare decoder input
                val decoderInputIds = generatedIds.map { it.toLong() }.toLongArray()
                val decoderShape = longArrayOf(1, generatedIds.size.toLong())

                Log.d(TAG, "Decoder input IDs: ${decoderInputIds.joinToString(", ")}")

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
                    "encoder_hidden_states" to encoderOutputTensor,
                    "encoder_attention_mask" to encoderMaskTensor
                )

            val decoderResults = decoder.run(decoderInputs)

            // Get logits from first output
            val logitsOutput = decoderResults[0].value

            // Handle different output types
            val nextTokenId = when (logitsOutput) {
                is OnnxTensor -> {
                    // OnnxTensor format
                    val logitsBuffer = logitsOutput.floatBuffer
                    val vocabSize = logitsOutput.info.shape[2].toInt()
                    val lastTokenPosition = generatedIds.size - 1
                    val lastTokenOffset = lastTokenPosition * vocabSize
                    val lastTokenLogits = FloatArray(vocabSize)
                    logitsBuffer.position(lastTokenOffset)
                    logitsBuffer.get(lastTokenLogits)
                    lastTokenLogits.indices.maxByOrNull { lastTokenLogits[it] } ?: eosTokenId
                }
                is Array<*> -> {
                    // Array format (nested arrays)
                    val logits = logitsOutput as Array<Array<FloatArray>>
                    val lastTokenPosition = generatedIds.size - 1
                    Log.d(TAG, "Extracting logits for position $lastTokenPosition (total positions: ${logits[0].size})")

                    val lastTokenLogits = logits[0][lastTokenPosition]
                    val tokenId = lastTokenLogits.indices.maxByOrNull { lastTokenLogits[it] } ?: eosTokenId

                    Log.d(TAG, "Selected token ID: $tokenId (max logit: ${lastTokenLogits[tokenId]})")
                    tokenId
                }
                else -> {
                    Log.e(TAG, "Unexpected logits type: ${logitsOutput?.javaClass?.name}")
                    throw Exception("Unsupported decoder output format: ${logitsOutput?.javaClass?.name}")
                }
            }

            // Clean up
            decoderInputTensor.close()
            encoderMaskTensor.close()
            decoderResults.close()

            // Check for EOS
            Log.d(TAG, "Token check: nextTokenId=$nextTokenId, eosTokenId=$eosTokenId, match=${nextTokenId == eosTokenId}")

            if (nextTokenId == eosTokenId) {
                Log.d(TAG, "✓ Reached EOS token at step $step - stopping generation")
                break
            }

            generatedIds.add(nextTokenId)
            Log.d(TAG, "✓ Added token $nextTokenId - total tokens now: ${generatedIds.size}")
            }

        Log.d(TAG, "Decoder loop finished. Total generated tokens: ${generatedIds.size}")

            // Remove the language code start token
            return generatedIds.drop(1).toIntArray()
        } finally {
            // Clean up encoder output tensor
            encoderOutputTensor.close()
        }
    }

    fun cleanup() {
        encoderSession?.close()
        decoderSession?.close()
        encoderSession = null
        decoderSession = null
    }
}

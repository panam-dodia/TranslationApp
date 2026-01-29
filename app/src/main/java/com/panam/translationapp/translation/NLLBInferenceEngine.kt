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
    private var decoderWithPastSession: OrtSession? = null  // Decoder with KV cache

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
            val decoderWithPastPath = File(modelDirectory, "decoder_with_past_model_quantized.onnx").absolutePath

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

            val decoderWithPastFile = if (File(decoderWithPastPath).exists()) {
                File(decoderWithPastPath)
            } else {
                File(modelDirectory, "decoder_with_past_model.onnx")
            }

            if (!encoderFile.exists() || !decoderFile.exists()) {
                throw Exception("Model files not found in ${modelDirectory.absolutePath}")
            }

            // Check for decoder_with_past (required for KV cache)
            if (!decoderWithPastFile.exists()) {
                Log.w(TAG, "⚠ decoder_with_past_model not found - KV cache disabled")
                Log.w(TAG, "   Translation will be slow and may produce poor results")
                Log.w(TAG, "   Re-export model with use_cache=True")
            }

            // Create sessions with optimization
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(4)
            sessionOptions.setInterOpNumThreads(4)

            encoderSession = ortEnvironment?.createSession(encoderFile.absolutePath, sessionOptions)
            decoderSession = ortEnvironment?.createSession(decoderFile.absolutePath, sessionOptions)

            // Load decoder with past if available
            if (decoderWithPastFile.exists()) {
                decoderWithPastSession = ortEnvironment?.createSession(decoderWithPastFile.absolutePath, sessionOptions)
                Log.d(TAG, "✓ Loaded decoder_with_past for KV cache")
            }

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
            Log.d(TAG, "Decoder inputs (${decoder.inputNames.size}): ${decoder.inputNames}")
            Log.d(TAG, "Decoder outputs (${decoder.outputNames.size}): ${decoder.outputNames}")
        }
        decoderWithPastSession?.let { decoderWithPast ->
            Log.d(TAG, "Decoder-with-past inputs (${decoderWithPast.inputNames.size}): ${decoderWithPast.inputNames}")
            Log.d(TAG, "Decoder-with-past outputs (${decoderWithPast.outputNames.size}): ${decoderWithPast.outputNames}")

            // Check if model supports KV cache
            val hasPastKeyValues = decoderWithPast.inputNames.any { it.contains("past") }
            if (hasPastKeyValues) {
                Log.d(TAG, "✓ Model SUPPORTS KV cache (has past_key_values inputs)")
            } else {
                Log.w(TAG, "⚠ Decoder-with-past does NOT have past_key_values inputs!")
            }
        } ?: run {
            Log.w(TAG, "⚠ No decoder_with_past model - KV cache disabled")
            Log.w(TAG, "   Translation will be slow and may produce poor results")
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
     * Run decoder with greedy decoding using KV cache
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

        // Encoder attention mask (created once, reused)
        val encoderMask = LongArray(encoderSeqLength) { 1L }
        val encoderMaskShape = longArrayOf(1, encoderSeqLength.toLong())
        val encoderMaskTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(encoderMask),
            encoderMaskShape
        )

        val hasKVCache = decoderWithPastSession != null
        Log.d(TAG, "Starting decoder loop with forcedBosTokenId=$forcedBosTokenId, maxLength=$maxGenerationLength")
        Log.d(TAG, "KV cache available: $hasKVCache")

        // Store past key-value cache
        var pastKeyValues: OrtSession.Result? = null

        try {
            // Greedy decoding loop
            for (step in 0 until maxGenerationLength) {
                Log.d(TAG, "=== Decoder Step $step, Generated so far: ${generatedIds.size} tokens ===")

                // Choose which decoder to use
                val currentDecoder = if (pastKeyValues == null || !hasKVCache) {
                    // First iteration: use regular decoder (no past inputs)
                    Log.d(TAG, "Using decoder_model (no cache)")
                    decoder
                } else {
                    // Subsequent iterations: use decoder_with_past (with KV cache)
                    Log.d(TAG, "Using decoder_with_past_model (with cache)")
                    decoderWithPastSession!!
                }

                // Prepare decoder input - only last token when using cache
                val inputIds = if (pastKeyValues == null) {
                    // First iteration: use all generated tokens so far
                    generatedIds.map { it.toLong() }.toLongArray()
                } else {
                    // Subsequent iterations: only the last token (with KV cache)
                    longArrayOf(generatedIds.last().toLong())
                }

                val decoderShape = longArrayOf(1, inputIds.size.toLong())
                Log.d(TAG, "Decoder input IDs: ${inputIds.joinToString(", ")} (using cache: ${pastKeyValues != null})")

                val decoderInputTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(inputIds),
                    decoderShape
                )

                // Build decoder inputs
                val decoderInputs = mutableMapOf<String, OnnxTensor>(
                    "input_ids" to decoderInputTensor,
                    "encoder_hidden_states" to encoderOutputTensor,
                    "encoder_attention_mask" to encoderMaskTensor
                )

                // Add past key-value cache if available
                if (pastKeyValues != null && hasKVCache) {
                    // Add all past_key_values inputs from previous iteration
                    val outputNames = pastKeyValues.iterator().asSequence().map { it.key }.toList()
                    var addedCount = 0

                    for (outputName in outputNames) {
                        // Skip the logits output (first output)
                        if (outputName == "logits") continue

                        // Convert "present.X.Y.Z" to "past_key_values.X.Y.Z"
                        val inputName = outputName.replace("present.", "past_key_values.")

                        val pastTensor = pastKeyValues[outputName] as? OnnxTensor
                        if (pastTensor != null) {
                            decoderInputs[inputName] = pastTensor
                            addedCount++
                        }
                    }
                    Log.d(TAG, "Added $addedCount past_key_values tensors to decoder_with_past")
                }

                // Run decoder
                val decoderResults = currentDecoder.run(decoderInputs)

                // Get logits from first output
                val logitsOutput = decoderResults[0].value

                // Extract next token ID from logits
                val nextTokenId = when (logitsOutput) {
                    is OnnxTensor -> {
                        // OnnxTensor format
                        val logitsBuffer = logitsOutput.floatBuffer
                        val vocabSize = logitsOutput.info.shape[2].toInt()
                        val lastTokenPosition = (logitsOutput.info.shape[1] - 1).toInt()
                        val lastTokenOffset = lastTokenPosition * vocabSize
                        val lastTokenLogits = FloatArray(vocabSize)
                        logitsBuffer.position(lastTokenOffset)
                        logitsBuffer.get(lastTokenLogits)
                        lastTokenLogits.indices.maxByOrNull { lastTokenLogits[it] } ?: eosTokenId
                    }
                    is Array<*> -> {
                        // Array format (nested arrays)
                        val logits = logitsOutput as Array<Array<FloatArray>>
                        val lastTokenPosition = logits[0].size - 1
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

                // Clean up input tensor for this iteration
                decoderInputTensor.close()

                // Clean up old pastKeyValues before replacing
                pastKeyValues?.close()

                // Store new pastKeyValues for next iteration
                pastKeyValues = decoderResults

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
            // Clean up resources
            encoderOutputTensor.close()
            encoderMaskTensor.close()
            pastKeyValues?.close()
        }
    }

    fun cleanup() {
        encoderSession?.close()
        decoderSession?.close()
        decoderWithPastSession?.close()
        encoderSession = null
        decoderSession = null
        decoderWithPastSession = null
    }
}

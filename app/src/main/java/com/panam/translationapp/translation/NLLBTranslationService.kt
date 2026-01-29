package com.panam.translationapp.translation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NLLB-200-based translation service using single multilingual model
 * Handles ALL language pairs with one model (no more Xenova dependency!)
 *
 * Benefits over OPUS-MT:
 * - Single ~890MB model handles all 132 language pairs
 * - Direct translation between any languages (no pivot through English)
 * - No dependency on Xenova's limited ONNX exports
 * - Easier to maintain and extend
 */
class NLLBTranslationService(private val context: Context) : TranslationService {
    private val TAG = "NLLBTranslationService"

    private val modelDownloader = NLLBModelDownloader(context)

    // Single tokenizer and engine for all language pairs
    private var tokenizer: NLLBTokenizer? = null
    private var engine: NLLBInferenceEngine? = null

    override suspend fun translate(
        text: String,
        fromLang: Language,
        toLang: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if model is downloaded
            if (!modelDownloader.isNLLBModelDownloaded()) {
                return@withContext Result.failure(
                    Exception("NLLB model not downloaded. Please download the model first.")
                )
            }

            // Load model if not already loaded
            if (tokenizer == null || engine == null) {
                loadModel()
            }

            val tok = tokenizer ?: return@withContext Result.failure(Exception("Tokenizer not loaded"))
            val eng = engine ?: return@withContext Result.failure(Exception("Inference engine not loaded"))

            Log.d(TAG, "Translating: \"$text\" (${fromLang.code}→${toLang.code})")

            // Step 1: Tokenize with source language code
            val inputIds = tok.encode(text, fromLang.nllbCode)
            Log.d(TAG, "✓ Tokenized: ${inputIds.size} tokens (source: ${fromLang.nllbCode})")

            // Step 2: Get target language token ID for forced_bos_token_id
            val targetLangTokenId = tok.getLanguageCodeId(toLang.nllbCode)
            Log.d(TAG, "✓ Target language: ${toLang.nllbCode} (token ID: $targetLangTokenId)")

            // Step 3: Inference with forced target language
            val outputIds = eng.translate(inputIds, targetLangTokenId)
            Log.d(TAG, "✓ Generated: ${outputIds.size} tokens")

            // Step 4: Decode
            val translatedText = tok.decode(outputIds)
            Log.d(TAG, "✓ Result: \"$translatedText\"")

            if (translatedText.isBlank()) {
                return@withContext Result.failure(Exception("Translation produced empty result"))
            }

            Result.success(translatedText)
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            Result.failure(e)
        }
    }

    private fun loadModel() {
        val modelDir = modelDownloader.getNLLBModelDirectory()

        Log.d(TAG, "Loading NLLB model...")

        try {
            tokenizer = NLLBTokenizer(modelDir)
            engine = NLLBInferenceEngine(context, modelDir)

            Log.d(TAG, "✓ NLLB model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load NLLB model", e)
            throw e
        }
    }

    /**
     * Download NLLB model (single download for all language pairs)
     */
    suspend fun downloadModel(
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<Unit> {
        return modelDownloader.downloadNLLBModel(onProgress)
    }

    /**
     * Check if NLLB model is downloaded
     * Since it's a single multilingual model, if downloaded, ALL pairs are available
     */
    override fun isModelDownloaded(fromLang: Language, toLang: Language): Boolean {
        return modelDownloader.isNLLBModelDownloaded()
    }

    /**
     * Check if models for a language are available
     * With NLLB, if the model is downloaded, ALL languages are available
     */
    fun isLanguageDownloaded(language: Language): Boolean {
        return modelDownloader.isNLLBModelDownloaded()
    }

    /**
     * Get NLLB model size
     */
    fun getModelSize(): Long {
        return modelDownloader.getNLLBModelSize()
    }

    /**
     * Delete NLLB model
     */
    fun deleteModel(): Boolean {
        // Unload from memory
        cleanup()

        // Delete from disk
        return modelDownloader.deleteNLLBModel()
    }

    override fun cleanup() {
        engine?.cleanup()
        engine = null
        tokenizer = null
    }
}

package com.panam.translationapp.translation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ONNX-based translation service using OPUS-MT models
 * Downloads models on-demand and caches them locally
 */
class ONNXTranslationService(private val context: Context) : TranslationService {
    private val TAG = "ONNXTranslationService"

    private val modelDownloader = ModelDownloader(context)

    // Cache for loaded models (key: "en-es")
    private val loadedTokenizers = mutableMapOf<String, MarianTokenizer>()
    private val loadedEngines = mutableMapOf<String, ONNXInferenceEngine>()

    private var currentModelKey: String? = null

    override suspend fun translate(
        text: String,
        fromLang: Language,
        toLang: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val modelKey = "${fromLang.code}-${toLang.code}"

            // Check if model is downloaded, if not return error
            if (!modelDownloader.isModelDownloaded(fromLang, toLang)) {
                return@withContext Result.failure(
                    Exception("Model for ${fromLang.displayName}→${toLang.displayName} not downloaded. Please download it first.")
                )
            }

            // Load model if not already loaded or if different language pair
            if (!loadedEngines.containsKey(modelKey)) {
                loadModel(fromLang, toLang)
            }

            val tokenizer = loadedTokenizers[modelKey]
                ?: return@withContext Result.failure(Exception("Tokenizer not loaded"))
            val engine = loadedEngines[modelKey]
                ?: return@withContext Result.failure(Exception("Inference engine not loaded"))

            Log.d(TAG, "Translating: \"$text\" (${fromLang.code}→${toLang.code})")

            // Step 1: Tokenize
            val inputIds = tokenizer.encode(text)
            Log.d(TAG, "✓ Tokenized: ${inputIds.size} tokens")

            // Step 2: Inference
            val outputIds = engine.translate(inputIds)
            Log.d(TAG, "✓ Generated: ${outputIds.size} tokens")

            // Step 3: Decode
            val translatedText = tokenizer.decode(outputIds)
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

    private fun loadModel(fromLang: Language, toLang: Language) {
        val modelKey = "${fromLang.code}-${toLang.code}"
        val modelDir = modelDownloader.getModelDirectory(fromLang, toLang)

        Log.d(TAG, "Loading model: $modelKey")

        try {
            val tokenizer = MarianTokenizer(modelDir)
            val engine = ONNXInferenceEngine(context, modelDir)

            loadedTokenizers[modelKey] = tokenizer
            loadedEngines[modelKey] = engine

            currentModelKey = modelKey
            Log.d(TAG, "✓ Model $modelKey loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model $modelKey", e)
            throw e
        }
    }

    /**
     * Download model for a language pair
     * Call this before translating if model is not downloaded
     */
    suspend fun downloadModel(
        fromLang: Language,
        toLang: Language,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<Unit> {
        return modelDownloader.downloadModelPair(fromLang, toLang, onProgress)
    }

    override fun isModelDownloaded(fromLang: Language, toLang: Language): Boolean {
        return modelDownloader.isModelDownloaded(fromLang, toLang)
    }

    fun getModelSize(fromLang: Language, toLang: Language): Long {
        return modelDownloader.getModelSize(fromLang, toLang)
    }

    fun deleteModel(fromLang: Language, toLang: Language): Boolean {
        val modelKey = "${fromLang.code}-${toLang.code}"

        // Unload from memory if loaded
        loadedTokenizers.remove(modelKey)
        loadedEngines[modelKey]?.cleanup()
        loadedEngines.remove(modelKey)

        // Delete from disk
        return modelDownloader.deleteModel(fromLang, toLang)
    }

    override fun cleanup() {
        loadedEngines.values.forEach { it.cleanup() }
        loadedEngines.clear()
        loadedTokenizers.clear()
        currentModelKey = null
    }
}


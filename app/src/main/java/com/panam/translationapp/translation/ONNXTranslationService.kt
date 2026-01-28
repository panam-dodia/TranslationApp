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
            // Check if direct translation model exists
            if (modelDownloader.isModelDownloaded(fromLang, toLang)) {
                // Direct translation available
                return@withContext translateDirect(text, fromLang, toLang)
            }

            // Check if pivot translation through English is possible
            if (fromLang != Language.ENGLISH && toLang != Language.ENGLISH) {
                // Try pivot: source → English → target
                if (modelDownloader.isModelDownloaded(fromLang, Language.ENGLISH) &&
                    modelDownloader.isModelDownloaded(Language.ENGLISH, toLang)) {
                    Log.d(TAG, "Using pivot translation: ${fromLang.code}→en→${toLang.code}")
                    return@withContext translateViaPivot(text, fromLang, toLang)
                }
            }

            // Build helpful error message
            val modelKey = "${fromLang.code}-${toLang.code}"
            val errorMessage = if (modelDownloader.getModelBaseUrl(fromLang, toLang) == null) {
                // Model doesn't exist in ONNX format
                "${fromLang.displayName}→${toLang.displayName} translation is not available yet. " +
                "This model hasn't been converted to ONNX format. " +
                "Try translating in the opposite direction or using a different language pair."
            } else {
                // Model exists but not downloaded
                "No translation model available for ${fromLang.displayName}→${toLang.displayName}. " +
                "Please download the required models."
            }

            return@withContext Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Direct translation using a single model
     */
    private suspend fun translateDirect(
        text: String,
        fromLang: Language,
        toLang: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val modelKey = "${fromLang.code}-${toLang.code}"

            // Load model if not already loaded
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
            Log.e(TAG, "Direct translation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Pivot translation through English (source → English → target)
     * Used when no direct model exists between language pairs
     */
    private suspend fun translateViaPivot(
        text: String,
        fromLang: Language,
        toLang: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Pivot translation: ${fromLang.displayName}→English→${toLang.displayName}")

            // Step 1: Translate to English
            val englishResult = translateDirect(text, fromLang, Language.ENGLISH)
            if (englishResult.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to translate to English: ${englishResult.exceptionOrNull()?.message}")
                )
            }

            val englishText = englishResult.getOrNull()!!
            Log.d(TAG, "✓ Step 1: $text → $englishText")

            // Step 2: Translate from English to target
            val finalResult = translateDirect(englishText, Language.ENGLISH, toLang)
            if (finalResult.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to translate from English: ${finalResult.exceptionOrNull()?.message}")
                )
            }

            val finalText = finalResult.getOrNull()!!
            Log.d(TAG, "✓ Step 2: $englishText → $finalText")
            Log.d(TAG, "✓ Pivot complete: \"$text\" → \"$finalText\"")

            Result.success(finalText)
        } catch (e: Exception) {
            Log.e(TAG, "Pivot translation failed", e)
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

    /**
     * Download all required models for a language (to/from English)
     * This enables translation to/from any other supported language
     * Note: Some languages may only have one direction available
     */
    suspend fun downloadLanguageModels(
        language: Language,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<Unit> {
        if (language == Language.ENGLISH) {
            return Result.success(Unit) // English doesn't need models for itself
        }

        var successCount = 0
        var failureMessages = mutableListOf<String>()

        // Try to download both directions: language ↔ English
        // Check if model URL exists before attempting download

        // 1. Try language → English
        if (modelDownloader.getModelBaseUrl(language, Language.ENGLISH) != null) {
            val toEnglishResult = modelDownloader.downloadModelPair(language, Language.ENGLISH) { msg, prog ->
                onProgress("${language.displayName}→English: $msg", prog * 0.5f)
            }
            if (toEnglishResult.isSuccess) {
                successCount++
            } else {
                failureMessages.add("${language.displayName}→English failed: ${toEnglishResult.exceptionOrNull()?.message}")
            }
        } else {
            Log.w(TAG, "${language.displayName}→English model not available in ONNX format")
            failureMessages.add("${language.displayName}→English: Model not available")
        }

        // 2. Try English → language
        if (modelDownloader.getModelBaseUrl(Language.ENGLISH, language) != null) {
            val fromEnglishResult = modelDownloader.downloadModelPair(Language.ENGLISH, language) { msg, prog ->
                onProgress("English→${language.displayName}: $msg", 0.5f + (prog * 0.5f))
            }
            if (fromEnglishResult.isSuccess) {
                successCount++
            } else {
                failureMessages.add("English→${language.displayName} failed: ${fromEnglishResult.exceptionOrNull()?.message}")
            }
        } else {
            Log.w(TAG, "English→${language.displayName} model not available in ONNX format")
            failureMessages.add("English→${language.displayName}: Model not available")
        }

        // Return success if at least one direction worked
        return if (successCount > 0) {
            if (successCount == 1) {
                Log.w(TAG, "Only one direction available for ${language.displayName}. Some translations may not work.")
            }
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to download any models for ${language.displayName}: ${failureMessages.joinToString(", ")}"))
        }
    }

    /**
     * Check if models for a language are downloaded
     * Returns true if at least one direction is available
     */
    fun isLanguageDownloaded(language: Language): Boolean {
        if (language == Language.ENGLISH) return true
        // Accept if at least one direction is available
        return modelDownloader.isModelDownloaded(language, Language.ENGLISH) ||
               modelDownloader.isModelDownloaded(Language.ENGLISH, language)
    }

    override fun isModelDownloaded(fromLang: Language, toLang: Language): Boolean {
        // Check if direct model exists
        if (modelDownloader.isModelDownloaded(fromLang, toLang)) {
            return true
        }

        // Check if pivot through English is possible
        if (fromLang != Language.ENGLISH && toLang != Language.ENGLISH) {
            return modelDownloader.isModelDownloaded(fromLang, Language.ENGLISH) &&
                   modelDownloader.isModelDownloaded(Language.ENGLISH, toLang)
        }

        return false
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


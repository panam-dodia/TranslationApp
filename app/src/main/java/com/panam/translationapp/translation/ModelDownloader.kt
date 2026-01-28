package com.panam.translationapp.translation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads ONNX models on-demand when user needs a language pair
 * Models are cached locally and reused
 */
class ModelDownloader(private val context: Context) {
    private val TAG = "ModelDownloader"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelsDir = File(context.filesDir, "models")

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    /**
     * Download complete model package for a language pair from Hugging Face
     * Uses quantized ONNX models for smaller size and faster inference
     */
    suspend fun downloadModelPair(
        fromLang: Language,
        toLang: Language,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelKey = "${fromLang.code}-${toLang.code}"
            val modelDir = File(modelsDir, modelKey)

            if (isModelDownloaded(fromLang, toLang)) {
                return@withContext Result.success(Unit)
            }

            modelDir.mkdirs()

            // Get base URL for this language pair
            val baseUrl = getModelBaseUrl(fromLang, toLang)
                ?: return@withContext Result.failure(Exception("Model URL not configured for $modelKey"))

            // Files to download with their locations
            // ONNX models are in /onnx/ subdirectory, configs are in root
            val filesToDownload = listOf(
                // Quantized ONNX models (smaller size, good quality)
                "onnx/encoder_model_quantized.onnx" to "encoder_model.onnx",
                "onnx/decoder_with_past_model_quantized.onnx" to "decoder_with_past_model.onnx",

                // Configuration files from root directory
                "vocab.json" to "vocab.json",
                "config.json" to "config.json",
                "generation_config.json" to "generation_config.json",
                "tokenizer_config.json" to "tokenizer_config.json",
                "source.spm" to "source.spm",
                "target.spm" to "target.spm"
            )

            for ((index, filePair) in filesToDownload.withIndex()) {
                val (remotePath, localName) = filePair
                val progress = index.toFloat() / filesToDownload.size
                onProgress("Downloading $localName", progress)

                val result = downloadFile(
                    url = "$baseUrl/$remotePath",
                    destFile = File(modelDir, localName)
                )

                if (result.isFailure) {
                    // Clean up partial download
                    modelDir.deleteRecursively()
                    return@withContext Result.failure(
                        Exception("Failed to download $remotePath: ${result.exceptionOrNull()?.message}")
                    )
                }
            }

            onProgress("Complete", 1f)
            Log.d(TAG, "✓ Model $modelKey downloaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model", e)
            Result.failure(e)
        }
    }

    private suspend fun downloadFile(url: String, destFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (destFile.exists()) {
                return@withContext Result.success(Unit)
            }

            Log.d(TAG, "Downloading: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))

            FileOutputStream(destFile).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $url", e)
            destFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Get base URL for model files from Hugging Face
     * Using Xenova's pre-converted OPUS-MT ONNX models
     *
     * These models are:
     * - Free to use (CC-BY 4.0 license - commercial use allowed)
     * - Quantized for mobile (~50-100MB per direction)
     * - High quality translations
     */
    fun getModelBaseUrl(fromLang: Language, toLang: Language): String? {
        val modelKey = "${fromLang.code}-${toLang.code}"

        // Map of supported language pairs with their Hugging Face model URLs
        // IMPORTANT: Only Xenova models have quantized ONNX versions
        // Helsinki-NLP models are PyTorch/TensorFlow only (not compatible with our ONNX setup)
        val supportedModels = mapOf(
            // === ENGLISH-CENTRIC PAIRS (ONNX available from Xenova) ===
            // English to other languages
            "en-es" to "https://huggingface.co/Xenova/opus-mt-en-es/resolve/main",
            "en-fr" to "https://huggingface.co/Xenova/opus-mt-en-fr/resolve/main",
            "en-de" to "https://huggingface.co/Xenova/opus-mt-en-de/resolve/main",
            "en-it" to "https://huggingface.co/Xenova/opus-mt-en-it/resolve/main",
            "en-pt" to "https://huggingface.co/Xenova/opus-mt-en-pt/resolve/main",
            "en-zh" to "https://huggingface.co/Xenova/opus-mt-en-zh/resolve/main",
            "en-ja" to "https://huggingface.co/Xenova/opus-mt-en-jap/resolve/main",
            "en-ar" to "https://huggingface.co/Xenova/opus-mt-en-ar/resolve/main",
            "en-ru" to "https://huggingface.co/Xenova/opus-mt-en-ru/resolve/main",
            "en-hi" to "https://huggingface.co/Xenova/opus-mt-en-hi/resolve/main",
            "en-ko" to "https://huggingface.co/Xenova/opus-mt-en-ko/resolve/main",

            // Other languages to English
            "es-en" to "https://huggingface.co/Xenova/opus-mt-es-en/resolve/main",
            "fr-en" to "https://huggingface.co/Xenova/opus-mt-fr-en/resolve/main",
            "de-en" to "https://huggingface.co/Xenova/opus-mt-de-en/resolve/main",
            "it-en" to "https://huggingface.co/Xenova/opus-mt-it-en/resolve/main",
            "pt-en" to "https://huggingface.co/Xenova/opus-mt-pt-en/resolve/main",
            "zh-en" to "https://huggingface.co/Xenova/opus-mt-zh-en/resolve/main",
            "ja-en" to "https://huggingface.co/Xenova/opus-mt-jap-en/resolve/main",
            "ar-en" to "https://huggingface.co/Xenova/opus-mt-ar-en/resolve/main",
            "ru-en" to "https://huggingface.co/Xenova/opus-mt-ru-en/resolve/main",
            "ko-en" to "https://huggingface.co/Xenova/opus-mt-ko-en/resolve/main"

            // Note: Some reverse pairs (hi-en, ko-en, etc.) may not be available in ONNX
            // When a direction is missing, only one-way translation will work
            // Direct non-English pairs (es-fr, de-fr, etc.) exist but are NOT in ONNX format
            // For non-English pairs, automatic pivot through English is used
        )

        // If not found in map, return null (model not available)
        val url = supportedModels[modelKey]
        if (url == null) {
            Log.w(TAG, "Model $modelKey not available. Available pairs: ${supportedModels.keys}")
        }
        return url
    }

    fun isModelDownloaded(fromLang: Language, toLang: Language): Boolean {
        val modelKey = "${fromLang.code}-${toLang.code}"
        val modelDir = File(modelsDir, modelKey)

        return modelDir.exists() &&
                File(modelDir, "encoder_model.onnx").exists() &&
                File(modelDir, "decoder_with_past_model.onnx").exists() &&
                File(modelDir, "vocab.json").exists()
    }

    fun deleteModel(fromLang: Language, toLang: Language): Boolean {
        val modelKey = "${fromLang.code}-${toLang.code}"
        val modelDir = File(modelsDir, modelKey)
        return modelDir.deleteRecursively()
    }

    fun getModelSize(fromLang: Language, toLang: Language): Long {
        val modelKey = "${fromLang.code}-${toLang.code}"
        val modelDir = File(modelsDir, modelKey)

        if (!modelDir.exists()) return 0L

        return modelDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    fun getModelDirectory(fromLang: Language, toLang: Language): File {
        val modelKey = "${fromLang.code}-${toLang.code}"
        return File(modelsDir, modelKey)
    }
}

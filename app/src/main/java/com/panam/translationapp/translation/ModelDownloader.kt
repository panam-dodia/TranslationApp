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
     * Download complete model package for a language pair
     * Includes encoder, decoder, and vocab files
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

            // Download all required files
            val baseUrl = getModelBaseUrl(fromLang, toLang)
                ?: return@withContext Result.failure(Exception("Model URL not configured for $modelKey"))

            val files = listOf(
                "encoder_model.onnx",
                "decoder_with_past_model.onnx",
                "vocab.json",
                "config.json",
                "generation_config.json",
                "special_tokens_map.json",
                "tokenizer_config.json"
            )

            for ((index, filename) in files.withIndex()) {
                val progress = index.toFloat() / files.size
                onProgress("Downloading $filename", progress)

                val result = downloadFile(
                    url = "$baseUrl/$filename",
                    destFile = File(modelDir, filename)
                )

                if (result.isFailure) {
                    // Clean up partial download
                    modelDir.deleteRecursively()
                    return@withContext result
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
     * Get base URL for model files
     * TODO: Replace with your CDN/server URLs
     */
    private fun getModelBaseUrl(fromLang: Language, toLang: Language): String? {
        val modelKey = "${fromLang.code}-${toLang.code}"

        // TODO: Host your ONNX models and update these URLs
        // Example: return "https://your-cdn.com/models/$modelKey"

        // For now, return null - you need to host the models
        return null
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

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
 * Downloads single NLLB-200-distilled multilingual model
 * This single model handles ALL language pairs (no need for multiple downloads)
 */
class NLLBModelDownloader(private val context: Context) {
    private val TAG = "NLLBModelDownloader"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val modelsDir = File(context.filesDir, "models")
    private val nllbModelDir = File(modelsDir, "nllb-200-distilled")

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        // Copy models from assets if bundled in APK
        copyModelsFromAssetsIfNeeded()
    }

    /**
     * Copy models from app assets to internal storage if they exist in assets
     * This allows bundling models with the APK instead of downloading
     */
    private fun copyModelsFromAssetsIfNeeded() {
        try {
            // Check if models are in assets
            val assetFiles = context.assets.list("models/nllb-200-distilled") ?: emptyArray()

            if (assetFiles.isEmpty()) {
                Log.d(TAG, "No models found in assets - will need to download")
                return
            }

            // Skip if already copied
            if (isNLLBModelDownloaded()) {
                Log.d(TAG, "Models already in internal storage")
                return
            }

            Log.d(TAG, "Copying ${assetFiles.size} model files from assets to internal storage...")
            nllbModelDir.mkdirs()

            // Required model files
            val requiredFiles = listOf(
                "encoder_model_quantized.onnx",
                "decoder_model_quantized.onnx",
                "decoder_with_past_model_quantized.onnx",
                "tokenizer.json"
            )

            for (fileName in requiredFiles) {
                if (assetFiles.contains(fileName)) {
                    val destFile = File(nllbModelDir, fileName)
                    context.assets.open("models/nllb-200-distilled/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "✓ Copied $fileName (${destFile.length() / 1024 / 1024}MB)")
                } else {
                    Log.w(TAG, "⚠ $fileName not found in assets")
                }
            }

            Log.d(TAG, "✓ Models copied from assets to internal storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy models from assets", e)
        }
    }

    /**
     * Download NLLB-200-distilled model from Hugging Face
     * Downloads quantized version for smaller size (~890MB total)
     */
    suspend fun downloadNLLBModel(
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isNLLBModelDownloaded()) {
                return@withContext Result.success(Unit)
            }

            nllbModelDir.mkdirs()

            // Xenova's NLLB-200-distilled-600M ONNX model
            val baseUrl = "https://huggingface.co/Xenova/nllb-200-distilled-600M/resolve/main"

            // Files to download
            val filesToDownload = listOf(
                // Quantized ONNX models (smaller size, good quality)
                // NOTE: Xenova's models don't have decoder_with_past
                // You'll need to export your own using the export_nllb_with_cache.py script
                "onnx/encoder_model_quantized.onnx" to "encoder_model_quantized.onnx",
                "onnx/decoder_model_quantized.onnx" to "decoder_model_quantized.onnx",
                // "onnx/decoder_with_past_model_quantized.onnx" to "decoder_with_past_model_quantized.onnx", // Not available on Xenova

                // Configuration files
                "config.json" to "config.json",
                "generation_config.json" to "generation_config.json",
                "tokenizer_config.json" to "tokenizer_config.json",
                "special_tokens_map.json" to "special_tokens_map.json",

                // Tokenizer files
                "tokenizer.json" to "tokenizer.json",
                "sentencepiece.bpe.model" to "sentencepiece.bpe.model"
            )

            Log.d(TAG, "⚠ NOTE: Xenova's models don't include decoder_with_past")
            Log.d(TAG, "   KV cache will not work without decoder_with_past_model_quantized.onnx")
            Log.d(TAG, "   Please export your own model using export_nllb_with_cache.py")

            Log.d(TAG, "Starting NLLB model download (~890MB)")

            for ((index, filePair) in filesToDownload.withIndex()) {
                val (remotePath, localName) = filePair
                val progress = index.toFloat() / filesToDownload.size
                val fileNum = index + 1
                val totalFiles = filesToDownload.size
                onProgress("Downloading file $fileNum of $totalFiles", progress)

                val result = downloadFile(
                    url = "$baseUrl/$remotePath",
                    destFile = File(nllbModelDir, localName)
                )

                if (result.isFailure) {
                    // Clean up partial download
                    nllbModelDir.deleteRecursively()
                    return@withContext Result.failure(
                        Exception("Failed to download $remotePath: ${result.exceptionOrNull()?.message}")
                    )
                }
            }

            onProgress("Download complete!", 1f)
            Log.d(TAG, "✓ NLLB model downloaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download NLLB model", e)
            Result.failure(e)
        }
    }

    private suspend fun downloadFile(url: String, destFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (destFile.exists()) {
                Log.d(TAG, "File already exists: ${destFile.name}")
                return@withContext Result.success(Unit)
            }

            Log.d(TAG, "Downloading: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
            val totalBytes = body.contentLength()

            FileOutputStream(destFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (totalBytes > 0) {
                            val progress = (totalBytesRead.toFloat() / totalBytes * 100).toInt()
                            if (progress % 10 == 0) {
                                Log.d(TAG, "${destFile.name}: $progress%")
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "✓ Downloaded ${destFile.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $url", e)
            destFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Check if NLLB model is fully downloaded
     * Now requires decoder_with_past for KV cache support
     */
    fun isNLLBModelDownloaded(): Boolean {
        val hasBasicFiles = nllbModelDir.exists() &&
                File(nllbModelDir, "encoder_model_quantized.onnx").exists() &&
                File(nllbModelDir, "decoder_model_quantized.onnx").exists() &&
                File(nllbModelDir, "tokenizer.json").exists()

        if (!hasBasicFiles) return false

        // Check for decoder_with_past (critical for KV cache)
        val hasDecoderWithPast = File(nllbModelDir, "decoder_with_past_model_quantized.onnx").exists()
        if (!hasDecoderWithPast) {
            Log.w(TAG, "⚠ decoder_with_past_model_quantized.onnx missing - KV cache will not work!")
        }

        return hasBasicFiles
    }

    /**
     * Delete NLLB model to free up space
     */
    fun deleteNLLBModel(): Boolean {
        return nllbModelDir.deleteRecursively()
    }

    /**
     * Get total size of downloaded NLLB model
     */
    fun getNLLBModelSize(): Long {
        if (!nllbModelDir.exists()) return 0L

        return nllbModelDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /**
     * Get NLLB model directory path
     */
    fun getNLLBModelDirectory(): File {
        return nllbModelDir
    }
}

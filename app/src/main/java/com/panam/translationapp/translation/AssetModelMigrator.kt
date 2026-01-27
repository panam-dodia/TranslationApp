package com.panam.translationapp.translation

import android.content.Context
import android.util.Log
import java.io.File

/**
 * One-time migration to move models from assets to proper model directory
 * This allows the existing en-es model in assets to work with the new on-demand system
 */
object AssetModelMigrator {
    private val TAG = "AssetModelMigrator"
    private const val MIGRATION_PREF_KEY = "assets_migrated"

    fun migrateAssetsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        if (prefs.getBoolean(MIGRATION_PREF_KEY, false)) {
            // Already migrated
            return
        }

        try {
            Log.d(TAG, "Checking for models in assets...")

            val modelsDir = File(context.filesDir, "models/en-es")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            // List of files to migrate
            val filesToMigrate = listOf(
                "encoder_model.onnx",
                "decoder_with_past_model.onnx",
                "vocab.json",
                "config.json",
                "generation_config.json",
                "special_tokens_map.json",
                "tokenizer_config.json",
                "source.spm",
                "target.spm"
            )

            var migratedCount = 0

            for (filename in filesToMigrate) {
                try {
                    // Check if file exists in assets
                    val assetStream = context.assets.open(filename)
                    val destFile = File(modelsDir, filename)

                    if (!destFile.exists()) {
                        // Copy from assets to files
                        destFile.outputStream().use { output ->
                            assetStream.use { input ->
                                input.copyTo(output)
                            }
                        }
                        migratedCount++
                        Log.d(TAG, "✓ Migrated: $filename")
                    }
                } catch (e: Exception) {
                    // File not in assets, skip
                    Log.d(TAG, "  Skipped: $filename (not in assets)")
                }
            }

            if (migratedCount > 0) {
                Log.d(TAG, "✓ Migrated $migratedCount files from assets to models/en-es/")
            }

            // Mark as migrated
            prefs.edit().putBoolean(MIGRATION_PREF_KEY, true).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
        }
    }
}

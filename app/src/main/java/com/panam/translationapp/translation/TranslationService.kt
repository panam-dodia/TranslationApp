package com.panam.translationapp.translation

/**
 * Common interface for translation services
 */
interface TranslationService {
    suspend fun translate(
        text: String,
        fromLang: Language,
        toLang: Language
    ): Result<String>

    fun isModelDownloaded(fromLang: Language, toLang: Language): Boolean

    fun cleanup()
}

package com.panam.translationapp.translation

import android.content.Context
import com.panam.translationapp.BuildConfig
import com.panam.translationapp.api.Content
import com.panam.translationapp.api.GeminiApiService
import com.panam.translationapp.api.GeminiRequest
import com.panam.translationapp.api.GenerationConfig
import com.panam.translationapp.api.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class GeminiTranslationService(private val context: Context) {

    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(GeminiApiService::class.java)

    suspend fun translate(
        text: String,
        fromLanguage: Language,
        toLanguage: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext Result.failure(Exception("Text cannot be empty"))
            }

            // Create translation prompt
            val prompt = buildTranslationPrompt(text, fromLanguage, toLanguage)

            // Create request
            val request = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = prompt))
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.3,
                    maxOutputTokens = 1024
                )
            )

            // Make API call
            val response = apiService.generateContent(apiKey, request)

            if (response.isSuccessful) {
                val geminiResponse = response.body()

                // Check for blocked content
                geminiResponse?.promptFeedback?.blockReason?.let { reason ->
                    return@withContext Result.failure(Exception("Content blocked: $reason"))
                }

                // Extract translated text
                val translatedText = geminiResponse
                    ?.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?.trim()

                if (translatedText.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Empty response from API"))
                }

                Result.success(translatedText)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Translation failed: ${e.message}", e))
        }
    }

    suspend fun chatWithAI(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (prompt.isBlank()) {
                return@withContext Result.failure(Exception("Prompt cannot be empty"))
            }

            // Create request
            val request = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = prompt))
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.7,
                    maxOutputTokens = 2048
                )
            )

            // Make API call
            val response = apiService.generateContent(apiKey, request)

            if (response.isSuccessful) {
                val geminiResponse = response.body()

                // Check for blocked content
                geminiResponse?.promptFeedback?.blockReason?.let { reason ->
                    return@withContext Result.failure(Exception("Content blocked: $reason"))
                }

                // Extract AI response
                val aiResponse = geminiResponse
                    ?.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?.trim()

                if (aiResponse.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Empty response from API"))
                }

                Result.success(aiResponse)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("AI chat failed: ${e.message}", e))
        }
    }

    private fun buildTranslationPrompt(
        text: String,
        fromLanguage: Language,
        toLanguage: Language
    ): String {
        return """
            Translate the following text from ${fromLanguage.displayName} to ${toLanguage.displayName}.

            IMPORTANT RULES:
            1. Provide ONLY the translated text, nothing else
            2. Do NOT include explanations, notes, or additional commentary
            3. Maintain the original tone and meaning
            4. Preserve any formatting or punctuation
            5. If the text is already in ${toLanguage.displayName}, return it as-is

            Text to translate:
            $text

            Translation:
        """.trimIndent()
    }

    fun cleanup() {
        // Cleanup resources if needed
    }
}

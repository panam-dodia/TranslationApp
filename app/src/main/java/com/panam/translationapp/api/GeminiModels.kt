package com.panam.translationapp.api

import com.google.gson.annotations.SerializedName

// Request models
data class GeminiRequest(
    @SerializedName("contents")
    val contents: List<Content>,
    @SerializedName("generationConfig")
    val generationConfig: GenerationConfig? = null
)

data class Content(
    @SerializedName("parts")
    val parts: List<Part>
)

data class Part(
    @SerializedName("text")
    val text: String
)

data class GenerationConfig(
    @SerializedName("temperature")
    val temperature: Double = 0.3,
    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int = 1024
)

// Response models
data class GeminiResponse(
    @SerializedName("candidates")
    val candidates: List<Candidate>?,
    @SerializedName("promptFeedback")
    val promptFeedback: PromptFeedback?
)

data class Candidate(
    @SerializedName("content")
    val content: Content?,
    @SerializedName("finishReason")
    val finishReason: String?
)

data class PromptFeedback(
    @SerializedName("blockReason")
    val blockReason: String?
)

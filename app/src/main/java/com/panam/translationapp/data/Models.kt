package com.panam.translationapp.data

import com.panam.translationapp.translation.Language
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class TranslationRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val person1Text: String,
    val person2Text: String,
    val person1Language: Language,
    val person2Language: Language,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class Session(
    val id: String = java.util.UUID.randomUUID().toString(),
    val person1Language: Language,
    val person2Language: Language,
    val translations: List<TranslationRecord> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var customName: String? = null
) {
    fun getDisplayName(): String {
        return customName ?: run {
            val formatter = DateTimeFormatter.ofPattern("MMM dd, h:mm a")
            "${person1Language.displayName}-${person2Language.displayName} - ${createdAt.format(formatter)}"
        }
    }
}

data class AskAIQuestion(
    val question: String,
    val conversationContext: List<TranslationRecord>
)

data class AskAIResponse(
    val answer: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

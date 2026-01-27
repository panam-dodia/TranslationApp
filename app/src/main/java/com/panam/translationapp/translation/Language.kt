package com.panam.translationapp.translation

enum class Language(val displayName: String, val code: String) {
    ENGLISH("English", "en"),
    SPANISH("Spanish", "es"),
    FRENCH("French", "fr"),
    GERMAN("German", "de"),
    ITALIAN("Italian", "it"),
    PORTUGUESE("Portuguese", "pt"),
    CHINESE("Chinese", "zh"),
    JAPANESE("Japanese", "ja"),
    KOREAN("Korean", "ko"),
    ARABIC("Arabic", "ar"),
    RUSSIAN("Russian", "ru"),
    HINDI("Hindi", "hi");

    companion object {
        fun fromCode(code: String): Language? {
            return entries.find { it.code == code }
        }
    }
}

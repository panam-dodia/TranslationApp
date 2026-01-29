package com.panam.translationapp.translation

enum class Language(
    val displayName: String,
    val code: String,
    val nllbCode: String
) {
    ENGLISH("English", "en", "eng_Latn"),
    SPANISH("Spanish", "es", "spa_Latn"),
    FRENCH("French", "fr", "fra_Latn"),
    GERMAN("German", "de", "deu_Latn"),
    ITALIAN("Italian", "it", "ita_Latn"),
    PORTUGUESE("Portuguese", "pt", "por_Latn"),
    CHINESE("Chinese", "zh", "zho_Hans"),
    JAPANESE("Japanese", "ja", "jpn_Jpan"),
    KOREAN("Korean", "ko", "kor_Hang"),
    ARABIC("Arabic", "ar", "arb_Arab"),
    RUSSIAN("Russian", "ru", "rus_Cyrl"),
    HINDI("Hindi", "hi", "hin_Deva");

    companion object {
        fun fromCode(code: String): Language? {
            return entries.find { it.code == code }
        }
    }
}

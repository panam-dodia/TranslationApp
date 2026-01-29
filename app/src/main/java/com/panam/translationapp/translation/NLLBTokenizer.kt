package com.panam.translationapp.translation

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Simplified NLLB-200 tokenizer for Android
 * Uses vocabulary from tokenizer.json without external dependencies
 */
class NLLBTokenizer(private val modelDirectory: File) {
    private val TAG = "NLLBTokenizer"

    private val vocab = mutableMapOf<String, Int>()
    private val reverseVocab = mutableMapOf<Int, String>()
    private val languageCodeToId = mutableMapOf<String, Int>()

    // NLLB special token IDs (from tokenizer.json)
    private val bosTokenId = 0
    private val padTokenId = 1
    private val eosTokenId = 2
    private val unkTokenId = 3

    init {
        loadVocab()
    }

    private fun loadVocab() {
        try {
            val tokenizerFile = File(modelDirectory, "tokenizer.json")
            if (!tokenizerFile.exists()) {
                throw Exception("tokenizer.json not found in ${modelDirectory.absolutePath}")
            }

            val jsonString = tokenizerFile.readText()
            val jsonObj = JSONObject(jsonString)

            // Load vocab from tokenizer.json
            val model = jsonObj.getJSONObject("model")
            val vocabObj = model.getJSONObject("vocab")

            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = vocabObj.getInt(token)
                vocab[token] = id
                reverseVocab[id] = token

                // Store language code tokens (e.g., eng_Latn, spa_Latn)
                if (token.matches(Regex("[a-z]{3}_[A-Z][a-z]+"))) {
                    languageCodeToId[token] = id
                }
            }

            Log.d(TAG, "✓ Loaded ${vocab.size} tokens, ${languageCodeToId.size} language codes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tokenizer from ${modelDirectory.absolutePath}", e)
            throw e
        }
    }

    /**
     * Encode text into token IDs
     * Simplified tokenization using word boundaries and vocab lookup
     */
    fun encode(text: String, sourceLangCode: String): IntArray {
        val tokens = mutableListOf<Int>()

        // Simple whitespace tokenization with vocab lookup
        val normalized = text.trim().lowercase()

        // Split into words and handle each
        val words = normalized.split(Regex("\\s+"))

        for (word in words) {
            // Try with SentencePiece marker
            val markedWord = "▁$word"
            val tokenId = vocab[markedWord] ?: vocab[word] ?: {
                // Character-level fallback
                word.map { vocab[it.toString()] ?: unkTokenId }
            }()

            if (tokenId is Int) {
                tokens.add(tokenId)
            } else if (tokenId is List<*>) {
                @Suppress("UNCHECKED_CAST")
                tokens.addAll(tokenId as List<Int>)
            }
        }

        // Add EOS token
        tokens.add(eosTokenId)

        // Add source language code
        val langCodeId = languageCodeToId[sourceLangCode]
            ?: throw IllegalArgumentException("Unknown language code: $sourceLangCode")
        tokens.add(langCodeId)

        Log.d(TAG, "Encoded '${text}' into ${tokens.size} tokens")
        return tokens.toIntArray()
    }

    /**
     * Decode token IDs back to text
     */
    fun decode(ids: IntArray): String {
        Log.d(TAG, "Decoding ${ids.size} token IDs")
        val tokens = mutableListOf<String>()

        for (id in ids) {
            // Skip special tokens
            if (id == eosTokenId || id == padTokenId || id == bosTokenId) continue

            val token = reverseVocab[id] ?: continue

            // Skip language code tokens
            if (token.matches(Regex("[a-z]{3}_[A-Z][a-z]+"))) continue

            tokens.add(token)
        }

        // Join and clean up
        val result = tokens.joinToString("")
            .replace("▁", " ")  // Replace SentencePiece marker with space
            .replace("</s>", "")
            .replace("<s>", "")
            .replace("<pad>", "")
            .trim()

        Log.d(TAG, "Decoded result: '$result'")
        return result
    }

    /**
     * Get the token ID for a language code (used as forced_bos_token_id)
     */
    fun getLanguageCodeId(langCode: String): Int {
        return languageCodeToId[langCode]
            ?: throw IllegalArgumentException("Unknown language code: $langCode")
    }

    fun getEosTokenId(): Int = eosTokenId
}

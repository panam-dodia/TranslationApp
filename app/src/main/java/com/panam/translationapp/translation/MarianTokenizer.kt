package com.panam.translationapp.translation

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Marian model tokenizer for OPUS-MT
 * Handles tokenization and detokenization using vocab.json
 * Loads vocab from model directory
 */
class MarianTokenizer(private val modelDirectory: File) {
    private val TAG = "MarianTokenizer"

    private val vocab = mutableMapOf<String, Int>()
    private val reverseVocab = mutableMapOf<Int, String>()

    // Special tokens
    private val padToken = "<pad>"
    private val eosToken = "</s>"
    private val unkToken = "<unk>"

    private val padTokenId = 65000
    private val eosTokenId = 0
    private val unkTokenId = 1

    init {
        loadVocab()
    }

    private fun loadVocab() {
        try {
            val vocabFile = File(modelDirectory, "vocab.json")
            if (!vocabFile.exists()) {
                throw Exception("vocab.json not found in ${modelDirectory.absolutePath}")
            }

            val jsonString = vocabFile.readText()
            val jsonObj = JSONObject(jsonString)

            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = jsonObj.getInt(token)
                vocab[token] = id
                reverseVocab[id] = token
            }

            Log.d(TAG, "✓ Loaded ${vocab.size} tokens from ${modelDirectory.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vocab from ${modelDirectory.absolutePath}", e)
            throw e
        }
    }

    /**
     * Tokenize text into token IDs
     * Uses simple word splitting and subword handling
     */
    fun encode(text: String): IntArray {
        val tokens = mutableListOf<Int>()

        // Normalize and split
        val normalized = text.trim().lowercase()
        val words = normalized.split(Regex("\\s+"))

        for (word in words) {
            // Try exact match first
            val tokenId = vocab[word]
            if (tokenId != null) {
                tokens.add(tokenId)
                continue
            }

            // Try with special marker (Marian uses ▁ for word start)
            val markedWord = "▁$word"
            val markedId = vocab[markedWord]
            if (markedId != null) {
                tokens.add(markedId)
                continue
            }

            // Character-level fallback for unknown words
            for (char in word) {
                val charToken = vocab[char.toString()] ?: unkTokenId
                tokens.add(charToken)
            }
        }

        // Add EOS token
        tokens.add(eosTokenId)

        return tokens.toIntArray()
    }

    /**
     * Decode token IDs back to text
     */
    fun decode(ids: IntArray): String {
        val tokens = mutableListOf<String>()

        for (id in ids) {
            // Skip special tokens
            if (id == eosTokenId || id == padTokenId) continue

            val token = reverseVocab[id] ?: continue
            tokens.add(token)
        }

        // Join and clean up
        return tokens.joinToString("")
            .replace("▁", " ")  // Replace word start marker with space
            .replace("</s>", "")
            .replace("<pad>", "")
            .trim()
    }

    fun getVocabSize(): Int = vocab.size
    fun getPadTokenId(): Int = padTokenId
    fun getEosTokenId(): Int = eosTokenId
    fun getDecoderStartTokenId(): Int = padTokenId  // Marian uses pad token as decoder start
}

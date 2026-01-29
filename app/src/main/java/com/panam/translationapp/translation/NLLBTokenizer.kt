package com.panam.translationapp.translation

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * NLLB-200 tokenizer with proper BPE (Byte-Pair Encoding) support
 * Implements SentencePiece BPE algorithm using merge rules from tokenizer.json
 */
class NLLBTokenizer(private val modelDirectory: File) {
    private val TAG = "NLLBTokenizer"

    private val vocab = mutableMapOf<String, Int>()
    private val reverseVocab = mutableMapOf<Int, String>()
    private val languageCodeToId = mutableMapOf<String, Int>()

    // BPE merge rules: List of pairs to merge, in priority order
    private val bpeMerges = mutableListOf<Pair<String, String>>()
    private val mergeRanks = mutableMapOf<Pair<String, String>, Int>()

    // NLLB special token IDs
    private val bosTokenId = 0
    private val padTokenId = 1
    private val eosTokenId = 2
    private val unkTokenId = 3

    init {
        loadTokenizer()
    }

    private fun loadTokenizer() {
        try {
            val tokenizerFile = File(modelDirectory, "tokenizer.json")
            if (!tokenizerFile.exists()) {
                throw Exception("tokenizer.json not found in ${modelDirectory.absolutePath}")
            }

            val jsonString = tokenizerFile.readText()
            val jsonObj = JSONObject(jsonString)

            // Load vocab
            val model = jsonObj.getJSONObject("model")
            val vocabObj = model.getJSONObject("vocab")

            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = vocabObj.getInt(token)
                vocab[token] = id
                reverseVocab[id] = token

                // Store language code tokens
                if (token.matches(Regex("[a-z]{3}_[A-Z][a-z]+"))) {
                    languageCodeToId[token] = id
                }
            }

            // Load BPE merges
            if (model.has("merges")) {
                val mergesArray = model.getJSONArray("merges")
                for (i in 0 until mergesArray.length()) {
                    val mergeStr = mergesArray.getString(i)
                    val parts = mergeStr.split(" ")
                    if (parts.size == 2) {
                        val pair = Pair(parts[0], parts[1])
                        bpeMerges.add(pair)
                        mergeRanks[pair] = i
                    }
                }
                Log.d(TAG, "✓ Loaded ${bpeMerges.size} BPE merge rules")
            } else {
                Log.w(TAG, "⚠ No BPE merges found in tokenizer.json - using fallback")
            }

            Log.d(TAG, "✓ Loaded ${vocab.size} tokens, ${languageCodeToId.size} language codes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tokenizer from ${modelDirectory.absolutePath}", e)
            throw e
        }
    }

    /**
     * Encode text into token IDs using proper BPE algorithm
     */
    fun encode(text: String, sourceLangCode: String): IntArray {
        val tokens = mutableListOf<Int>()

        // Add source language code at the BEGINNING
        val langCodeId = languageCodeToId[sourceLangCode]
            ?: throw IllegalArgumentException("Unknown language code: $sourceLangCode")
        tokens.add(langCodeId)

        // Pre-tokenize: split on whitespace
        val words = text.trim().split(Regex("\\s+"))

        for (word in words) {
            if (word.isEmpty()) continue

            // Add SentencePiece word boundary marker
            val processedWord = "▁${word.lowercase()}"

            // Apply BPE to the word
            val wordTokens = if (bpeMerges.isNotEmpty()) {
                Log.d(TAG, "Using BPE encoding for word: $word")
                bpeEncode(processedWord)
            } else {
                Log.w(TAG, "Using FALLBACK encoding for word: $word (no BPE merges)")
                fallbackEncode(processedWord)
            }

            tokens.addAll(wordTokens)
        }

        // Add EOS token
        tokens.add(eosTokenId)

        Log.d(TAG, "Encoded '$text' into ${tokens.size} tokens: ${tokens.joinToString(", ")}")
        return tokens.toIntArray()
    }

    /**
     * Proper BPE encoding using merge rules
     */
    private fun bpeEncode(word: String): List<Int> {
        Log.d(TAG, "BPE encoding word: '$word'")

        // Start with individual characters
        var symbols = word.map { it.toString() }.toMutableList()
        Log.d(TAG, "  Initial characters: ${symbols.joinToString(" | ")}")

        var mergeCount = 0
        // Apply BPE merges iteratively
        while (symbols.size > 1) {
            // Find the highest priority merge pair in current symbols
            var bestPair: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            var bestPos = -1

            for (i in 0 until symbols.size - 1) {
                val pair = Pair(symbols[i], symbols[i + 1])
                val rank = mergeRanks[pair]
                if (rank != null && rank < bestRank) {
                    bestRank = rank
                    bestPair = pair
                    bestPos = i
                }
            }

            // No more merges possible
            if (bestPair == null) {
                Log.d(TAG, "  No more merges found after $mergeCount merges")
                break
            }

            // Apply the merge
            val merged = symbols[bestPos] + symbols[bestPos + 1]
            Log.d(TAG, "  Merge #$mergeCount: '${symbols[bestPos]}' + '${symbols[bestPos + 1]}' = '$merged' (rank=$bestRank)")
            symbols[bestPos] = merged
            symbols.removeAt(bestPos + 1)
            mergeCount++
        }

        Log.d(TAG, "  Final symbols after BPE: ${symbols.joinToString(" | ")}")

        // Convert symbols to token IDs
        val tokenIds = mutableListOf<Int>()
        for (symbol in symbols) {
            val tokenId = vocab[symbol]
            if (tokenId != null) {
                tokenIds.add(tokenId)
                Log.d(TAG, "  Symbol '$symbol' -> token ID $tokenId")
            } else {
                Log.w(TAG, "  Symbol '$symbol' NOT in vocab, using fallback")
                tokenIds.addAll(fallbackEncode(symbol))
            }
        }

        return tokenIds
    }

    /**
     * Fallback encoding when BPE merges aren't available or symbol not in vocab
     * Uses longest-match-first on vocabulary
     */
    private fun fallbackEncode(word: String): List<Int> {
        val tokens = mutableListOf<Int>()
        var remaining = word

        while (remaining.isNotEmpty()) {
            var matched = false

            // Try longest match first
            for (length in remaining.length downTo 1) {
                val substring = remaining.substring(0, length)
                val tokenId = vocab[substring]

                if (tokenId != null) {
                    tokens.add(tokenId)
                    remaining = remaining.substring(length)
                    matched = true
                    break
                }
            }

            // If no match, try character-by-character
            if (!matched) {
                val char = remaining[0].toString()
                val tokenId = vocab[char]
                if (tokenId != null) {
                    tokens.add(tokenId)
                } else {
                    tokens.add(unkTokenId)
                }
                remaining = remaining.substring(1)
            }
        }

        return tokens
    }

    /**
     * Decode token IDs back to text
     */
    fun decode(ids: IntArray): String {
        Log.d(TAG, "Decoding ${ids.size} token IDs: ${ids.joinToString(", ")}")
        val tokens = mutableListOf<String>()

        for (id in ids) {
            // Skip special tokens
            if (id == eosTokenId || id == padTokenId || id == bosTokenId) {
                Log.d(TAG, "  Skipping special token ID $id")
                continue
            }

            val token = reverseVocab[id]
            if (token == null) {
                Log.w(TAG, "  Token ID $id not found in vocabulary!")
                continue
            }

            Log.d(TAG, "  Token ID $id -> '$token'")

            // Skip language code tokens
            if (token.matches(Regex("[a-z]{3}_[A-Z][a-z]+"))) {
                Log.d(TAG, "  Skipping language code token: $token")
                continue
            }

            tokens.add(token)
        }

        Log.d(TAG, "  Collected tokens: ${tokens.joinToString(" | ")}")

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
     * Get the token ID for a language code
     */
    fun getLanguageCodeId(langCode: String): Int {
        return languageCodeToId[langCode]
            ?: throw IllegalArgumentException("Unknown language code: $langCode")
    }

    fun getEosTokenId(): Int = eosTokenId
}

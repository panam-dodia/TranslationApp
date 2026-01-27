package com.panam.translationapp.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.panam.translationapp.translation.Language
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class TextToSpeechService(context: Context) {
    private val TAG = "TextToSpeechService"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                Log.d(TAG, "TextToSpeech initialized successfully")
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }

    suspend fun speak(text: String, language: Language): Result<Unit> {
        if (!isInitialized) {
            return Result.failure(Exception("TextToSpeech not initialized"))
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                val locale = Locale(language.code)
                val result = tts?.setLanguage(locale)

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    continuation.resume(Result.failure(Exception("Language not supported: ${language.displayName}")))
                    return@suspendCancellableCoroutine
                }

                val utteranceId = System.currentTimeMillis().toString()

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.success(Unit))
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception("TTS error")))
                        }
                    }
                })

                val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

                if (speakResult == TextToSpeech.ERROR) {
                    continuation.resume(Result.failure(Exception("Failed to speak")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during speech", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun cleanup() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

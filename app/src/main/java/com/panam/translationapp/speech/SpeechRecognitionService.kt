package com.panam.translationapp.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.panam.translationapp.translation.Language
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

class SpeechRecognitionService(private val context: Context) {
    private val TAG = "SpeechRecognitionService"
    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(language: Language): Flow<SpeechRecognitionResult> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechRecognitionResult.Error("Speech recognition not available"))
            close()
            return@callbackFlow
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechRecognitionResult.Ready)
            }

            override fun onBeginningOfSpeech() {
                trySend(SpeechRecognitionResult.Speaking)
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(SpeechRecognitionResult.RmsChanged(rmsdB))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                trySend(SpeechRecognitionResult.EndOfSpeech)
            }

            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                trySend(SpeechRecognitionResult.Error(errorMessage))
                close()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(SpeechRecognitionResult.Success(matches[0]))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(SpeechRecognitionResult.Partial(matches[0]))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer?.setRecognitionListener(recognitionListener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.code)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            trySend(SpeechRecognitionResult.Error(e.message ?: "Unknown error"))
            close()
        }

        awaitClose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Recognition error"
        }
    }

    fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

sealed class SpeechRecognitionResult {
    object Ready : SpeechRecognitionResult()
    object Speaking : SpeechRecognitionResult()
    object EndOfSpeech : SpeechRecognitionResult()
    data class RmsChanged(val rms: Float) : SpeechRecognitionResult()
    data class Partial(val text: String) : SpeechRecognitionResult()
    data class Success(val text: String) : SpeechRecognitionResult()
    data class Error(val message: String) : SpeechRecognitionResult()
}

package com.panam.translationapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panam.translationapp.speech.SpeechRecognitionResult
import com.panam.translationapp.speech.SpeechRecognitionService
import com.panam.translationapp.speech.TextToSpeechService
import com.panam.translationapp.translation.AssetModelMigrator
import com.panam.translationapp.translation.Language
import com.panam.translationapp.translation.ONNXTranslationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TranslationState(
    val languagesSelected: Boolean = false,
    val person1Language: Language? = null,
    val person2Language: Language? = null,
    val person1Text: String = "",
    val person2Text: String = "",
    val isListeningPerson1: Boolean = false,
    val isListeningPerson2: Boolean = false,
    val isSpeaking: Boolean = false,
    val error: String? = null,
    val person1ToPerson2ModelDownloaded: Boolean = false,
    val person2ToPerson1ModelDownloaded: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadMessage: String = "",
    val audioLevel: Float = 0f
)

class TranslationViewModel(application: Application) : AndroidViewModel(application) {
    private val translationService = ONNXTranslationService(application)
    private val speechRecognitionService = SpeechRecognitionService(application)
    private val ttsService = TextToSpeechService(application)

    private val _state = MutableStateFlow(TranslationState())
    val state: StateFlow<TranslationState> = _state.asStateFlow()

    init {
        // Migrate models from assets to proper directory (one-time)
        AssetModelMigrator.migrateAssetsIfNeeded(application)
    }

    fun setLanguages(language1: Language, language2: Language) {
        _state.update {
            it.copy(
                languagesSelected = true,
                person1Language = language1,
                person2Language = language2
            )
        }
        checkModelsDownloaded()
    }

    fun setPerson1Language(language: Language) {
        _state.update { it.copy(person1Language = language) }
        checkModelsDownloaded()
    }

    fun setPerson2Language(language: Language) {
        _state.update { it.copy(person2Language = language) }
        checkModelsDownloaded()
    }

    fun swapLanguages() {
        _state.update {
            it.copy(
                person1Language = it.person2Language,
                person2Language = it.person1Language,
                person1Text = it.person2Text,
                person2Text = it.person1Text
            )
        }
        checkModelsDownloaded()
    }

    fun startListeningPerson1() {
        val language = _state.value.person1Language ?: return
        _state.update { it.copy(isListeningPerson1 = true, error = null) }

        viewModelScope.launch {
            speechRecognitionService.startListening(language).collect { result ->
                when (result) {
                    is SpeechRecognitionResult.Success -> {
                        _state.update { it.copy(person1Text = result.text, isListeningPerson1 = false) }
                        translatePerson1ToPerson2(result.text)
                    }
                    is SpeechRecognitionResult.Partial -> {
                        _state.update { it.copy(person1Text = result.text) }
                    }
                    is SpeechRecognitionResult.Error -> {
                        _state.update { it.copy(error = result.message, isListeningPerson1 = false, audioLevel = 0f) }
                    }
                    is SpeechRecognitionResult.RmsChanged -> {
                        _state.update { it.copy(audioLevel = result.rms) }
                    }
                    is SpeechRecognitionResult.EndOfSpeech -> {
                        _state.update { it.copy(audioLevel = 0f) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun startListeningPerson2() {
        val language = _state.value.person2Language ?: return
        _state.update { it.copy(isListeningPerson2 = true, error = null) }

        viewModelScope.launch {
            speechRecognitionService.startListening(language).collect { result ->
                when (result) {
                    is SpeechRecognitionResult.Success -> {
                        _state.update { it.copy(person2Text = result.text, isListeningPerson2 = false) }
                        translatePerson2ToPerson1(result.text)
                    }
                    is SpeechRecognitionResult.Partial -> {
                        _state.update { it.copy(person2Text = result.text) }
                    }
                    is SpeechRecognitionResult.Error -> {
                        _state.update { it.copy(error = result.message, isListeningPerson2 = false, audioLevel = 0f) }
                    }
                    is SpeechRecognitionResult.RmsChanged -> {
                        _state.update { it.copy(audioLevel = result.rms) }
                    }
                    is SpeechRecognitionResult.EndOfSpeech -> {
                        _state.update { it.copy(audioLevel = 0f) }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun translatePerson1ToPerson2(text: String) {
        viewModelScope.launch {
            val lang1 = _state.value.person1Language ?: return@launch
            val lang2 = _state.value.person2Language ?: return@launch

            val result = translationService.translate(text, lang1, lang2)

            result.onSuccess { translatedText ->
                _state.update { it.copy(person2Text = translatedText) }
                speakPerson2(translatedText)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message) }
            }
        }
    }

    private fun translatePerson2ToPerson1(text: String) {
        viewModelScope.launch {
            val lang1 = _state.value.person2Language ?: return@launch
            val lang2 = _state.value.person1Language ?: return@launch

            val result = translationService.translate(text, lang1, lang2)

            result.onSuccess { translatedText ->
                _state.update { it.copy(person1Text = translatedText) }
                speakPerson1(translatedText)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message) }
            }
        }
    }

    private fun speakPerson1(text: String) {
        viewModelScope.launch {
            val language = _state.value.person1Language ?: return@launch
            _state.update { it.copy(isSpeaking = true) }
            ttsService.speak(text, language)
            _state.update { it.copy(isSpeaking = false) }
        }
    }

    private fun speakPerson2(text: String) {
        viewModelScope.launch {
            val language = _state.value.person2Language ?: return@launch
            _state.update { it.copy(isSpeaking = true) }
            ttsService.speak(text, language)
            _state.update { it.copy(isSpeaking = false) }
        }
    }

    fun stopListening() {
        speechRecognitionService.stopListening()
        _state.update { it.copy(isListeningPerson1 = false, isListeningPerson2 = false, audioLevel = 0f) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun downloadModelsIfNeeded() {
        viewModelScope.launch {
            val lang1 = _state.value.person1Language ?: return@launch
            val lang2 = _state.value.person2Language ?: return@launch

            _state.update { it.copy(isDownloadingModel = true) }

            // Download models for each language (not pairs!)
            // This way models are reused across any language combination

            // Collect unique languages that need models
            val languagesToDownload = mutableSetOf<Language>()
            if (!translationService.isLanguageDownloaded(lang1)) {
                languagesToDownload.add(lang1)
            }
            if (!translationService.isLanguageDownloaded(lang2)) {
                languagesToDownload.add(lang2)
            }

            if (languagesToDownload.isEmpty()) {
                // All models already downloaded
                _state.update { it.copy(isDownloadingModel = false) }
                checkModelsDownloaded()
                return@launch
            }

            // Download each language's models
            var currentProgress = 0
            val totalLanguages = languagesToDownload.size

            for ((index, language) in languagesToDownload.withIndex()) {
                val result = translationService.downloadLanguageModels(language) { message, progress ->
                    val overallProgress = (index.toFloat() / totalLanguages) + (progress / totalLanguages)
                    _state.update {
                        it.copy(
                            downloadMessage = message,
                            downloadProgress = overallProgress
                        )
                    }
                }

                if (result.isFailure) {
                    _state.update {
                        it.copy(
                            error = "Failed to download ${language.displayName} models: ${result.exceptionOrNull()?.message}",
                            isDownloadingModel = false
                        )
                    }
                    return@launch
                }
            }

            _state.update { it.copy(isDownloadingModel = false, downloadProgress = 0f, downloadMessage = "") }
            checkModelsDownloaded()
        }
    }

    private fun checkModelsDownloaded() {
        val lang1 = _state.value.person1Language ?: return
        val lang2 = _state.value.person2Language ?: return

        // Check if translation is possible in both directions
        // This considers both direct models and pivot through English
        val p1ToP2 = translationService.isModelDownloaded(lang1, lang2)
        val p2ToP1 = translationService.isModelDownloaded(lang2, lang1)

        _state.update {
            it.copy(
                person1ToPerson2ModelDownloaded = p1ToP2,
                person2ToPerson1ModelDownloaded = p2ToP1
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        translationService.cleanup()
        speechRecognitionService.cleanup()
        ttsService.cleanup()
    }
}

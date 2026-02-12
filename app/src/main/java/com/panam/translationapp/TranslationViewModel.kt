package com.panam.translationapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panam.translationapp.speech.SpeechRecognitionResult
import com.panam.translationapp.speech.SpeechRecognitionService
import com.panam.translationapp.speech.TextToSpeechService
import com.panam.translationapp.data.ChatMessage
import com.panam.translationapp.data.PreferencesManager
import com.panam.translationapp.data.Session
import com.panam.translationapp.data.SessionManager
import com.panam.translationapp.data.TranslationRecord
import com.panam.translationapp.translation.GeminiTranslationService
import com.panam.translationapp.translation.Language
import com.panam.translationapp.billing.BillingManager
import com.panam.translationapp.billing.SubscriptionManager
import com.panam.translationapp.billing.SubscriptionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    val isTranslating: Boolean = false,
    val error: String? = null,
    val audioLevel: Float = 0f
)

class TranslationViewModel(application: Application) : AndroidViewModel(application) {
    private val translationService = GeminiTranslationService(application)
    private val speechRecognitionService = SpeechRecognitionService(application)
    private val ttsService = TextToSpeechService(application)

    private val sessionManager = SessionManager()
    private val preferencesManager = PreferencesManager(application)

    // Subscription and Billing
    val subscriptionManager = SubscriptionManager(application)
    private var _billingManager: BillingManager? = null
    val billingManager: BillingManager?
        get() = _billingManager

    private val _state = MutableStateFlow(TranslationState())
    val state: StateFlow<TranslationState> = _state.asStateFlow()

    // Session-related state
    val sessions: StateFlow<List<Session>> = sessionManager.sessions

    // Preferences state
    val isDarkMode: StateFlow<Boolean> = preferencesManager.isDarkMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val ttsSpeed: StateFlow<Float> = preferencesManager.ttsSpeed.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1.0f
    )

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _askAIResponse = MutableStateFlow<String?>(null)
    val askAIResponse: StateFlow<String?> = _askAIResponse.asStateFlow()

    private val _isAskAILoading = MutableStateFlow(false)
    val isAskAILoading: StateFlow<Boolean> = _isAskAILoading.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    // Subscription status
    val subscriptionStatus: StateFlow<SubscriptionStatus> = subscriptionManager.getSubscriptionStatusFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubscriptionStatus.Trial(7)
    )

    init {
        // Initialize first install time on app launch
        viewModelScope.launch {
            subscriptionManager.initializeFirstInstall()
        }
    }

    fun initializeBillingManager() {
        if (_billingManager == null) {
            _billingManager = BillingManager(getApplication(), subscriptionManager)
        }
    }

    fun setLanguages(language1: Language, language2: Language) {
        _state.update {
            it.copy(
                languagesSelected = true,
                person1Language = language1,
                person2Language = language2
            )
        }
        // Auto-create a new session
        createNewSession(language1, language2)
    }

    fun setPerson1Language(language: Language) {
        _state.update { it.copy(person1Language = language) }
    }

    fun setPerson2Language(language: Language) {
        _state.update { it.copy(person2Language = language) }
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

            _state.update { it.copy(isTranslating = true, error = null) }

            val result = translationService.translate(text, lang1, lang2)

            result.onSuccess { translatedText ->
                _state.update { it.copy(person2Text = translatedText, isTranslating = false) }
                saveTranslationToSession(text, translatedText)
                speakPerson2(translatedText)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Translation failed", isTranslating = false) }
            }
        }
    }

    private fun translatePerson2ToPerson1(text: String) {
        viewModelScope.launch {
            val lang1 = _state.value.person2Language ?: return@launch
            val lang2 = _state.value.person1Language ?: return@launch

            _state.update { it.copy(isTranslating = true, error = null) }

            val result = translationService.translate(text, lang1, lang2)

            result.onSuccess { translatedText ->
                _state.update { it.copy(person1Text = translatedText, isTranslating = false) }
                saveTranslationToSession(translatedText, text)
                speakPerson1(translatedText)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Translation failed", isTranslating = false) }
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

    // Session Management
    private fun createNewSession(language1: Language, language2: Language) {
        val session = Session(
            person1Language = language1,
            person2Language = language2
        )
        sessionManager.createSession(session)
        _chatMessages.value = session.chatMessages
    }

    fun deleteSession(session: Session) {
        sessionManager.deleteSession(session.id)
    }

    fun renameSession(session: Session, newName: String) {
        sessionManager.renameSession(session.id, newName)
    }

    fun loadSession(session: Session) {
        _state.update {
            it.copy(
                languagesSelected = true,
                person1Language = session.person1Language,
                person2Language = session.person2Language,
                person1Text = session.translations.lastOrNull()?.person1Text ?: "",
                person2Text = session.translations.lastOrNull()?.person2Text ?: ""
            )
        }
        _chatMessages.value = session.chatMessages
    }

    private fun saveTranslationToSession(person1Text: String, person2Text: String) {
        val currentState = _state.value
        val translationRecord = TranslationRecord(
            person1Text = person1Text,
            person2Text = person2Text,
            person1Language = currentState.person1Language ?: return,
            person2Language = currentState.person2Language ?: return
        )
        sessionManager.addTranslation(translationRecord)
    }

    // Chat with AI
    fun sendChatMessage(message: String) {
        viewModelScope.launch {
            val currentState = _state.value
            val lang1 = currentState.person1Language ?: return@launch
            val lang2 = currentState.person2Language ?: return@launch

            // Add user message
            val userMessage = ChatMessage(text = message, isFromUser = true)
            _chatMessages.update { it + userMessage }
            sessionManager.addChatMessage(userMessage)

            _isChatLoading.value = true

            // Get AI response
            val context = """
                I'm learning ${lang1.displayName} and ${lang2.displayName}.

                IMPORTANT: Provide your response in plain text without any markdown formatting.
                Do NOT use asterisks (*), hashtags (#), or any special formatting characters.
                Write in a clear, natural conversational style.

                Question: $message
            """.trimIndent()

            val result = translationService.chatWithAI(context)

            result.onSuccess { response ->
                val aiMessage = ChatMessage(text = response, isFromUser = false)
                _chatMessages.update { it + aiMessage }
                sessionManager.addChatMessage(aiMessage)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Chat failed") }
            }

            _isChatLoading.value = false
        }
    }

    // Ask AI about translation
    fun askAI(question: String) {
        viewModelScope.launch {
            val currentSession = sessionManager.currentSession ?: return@launch

            _isAskAILoading.value = true
            _askAIResponse.value = null

            val conversationContext = currentSession.translations.takeLast(5).joinToString("\n") {
                "${it.person1Language.displayName}: ${it.person1Text}\n" +
                "${it.person2Language.displayName}: ${it.person2Text}"
            }

            val prompt = """
                Context of recent conversation:
                $conversationContext

                User question: $question

                IMPORTANT: Provide your response in plain text without any markdown formatting.
                Do NOT use asterisks (*), hashtags (#), underscores (_), or any special formatting characters.
                Write in a clear, natural conversational style.

                Please provide a helpful answer about this translation or conversation.
            """.trimIndent()

            val result = translationService.chatWithAI(prompt)

            result.onSuccess { response ->
                _askAIResponse.value = response
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "AI request failed") }
            }

            _isAskAILoading.value = false
        }
    }

    fun clearAskAIResponse() {
        _askAIResponse.value = null
    }

    // Settings Management
    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDarkMode(enabled)
        }
    }

    fun setTTSSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesManager.setTTSSpeed(speed)
            ttsService.setSpeechRate(speed)
        }
    }

    fun clearAllHistory() {
        // Get all session IDs
        val allSessions = sessionManager.sessions.value
        // Delete each session
        allSessions.forEach { session ->
            sessionManager.deleteSession(session.id)
        }
        // Clear current session
        sessionManager.clearCurrentSession()
    }

    override fun onCleared() {
        super.onCleared()
        translationService.cleanup()
        speechRecognitionService.cleanup()
        ttsService.cleanup()
        _billingManager?.endConnection()
    }
}

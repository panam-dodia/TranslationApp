package com.panam.translationapp.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private var _currentSession: Session? = null
    val currentSession: Session? get() = _currentSession

    fun createSession(session: Session) {
        _currentSession = session
        _sessions.value = listOf(session) + _sessions.value
    }

    fun updateCurrentSession(updatedSession: Session) {
        _currentSession = updatedSession
        _sessions.value = _sessions.value.map {
            if (it.id == updatedSession.id) updatedSession else it
        }
    }

    fun addTranslation(translation: TranslationRecord) {
        _currentSession?.let { session ->
            val updatedSession = session.copy(
                translations = session.translations + translation
            )
            updateCurrentSession(updatedSession)
        }
    }

    fun addChatMessage(message: ChatMessage) {
        _currentSession?.let { session ->
            val updatedSession = session.copy(
                chatMessages = session.chatMessages + message
            )
            updateCurrentSession(updatedSession)
        }
    }

    fun renameSession(sessionId: String, newName: String) {
        _sessions.value = _sessions.value.map {
            if (it.id == sessionId) it.copy(customName = newName) else it
        }
        if (_currentSession?.id == sessionId) {
            _currentSession = _currentSession?.copy(customName = newName)
        }
    }

    fun deleteSession(sessionId: String) {
        _sessions.value = _sessions.value.filter { it.id != sessionId }
        if (_currentSession?.id == sessionId) {
            _currentSession = null
        }
    }

    fun getSession(sessionId: String): Session? {
        return _sessions.value.find { it.id == sessionId }
    }

    fun clearCurrentSession() {
        _currentSession = null
    }
}

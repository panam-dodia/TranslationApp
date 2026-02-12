package com.panam.translationapp.navigation

sealed class Screen(val route: String) {
    object LanguageSelection : Screen("language_selection")
    object Translation : Screen("translation")
    object Chat : Screen("chat")
    object History : Screen("history")
    object Settings : Screen("settings")
    object Subscription : Screen("subscription")
    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun createRoute(sessionId: String) = "session_detail/$sessionId"
    }
}

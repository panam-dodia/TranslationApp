package com.panam.translationapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.panam.translationapp.navigation.Screen
import com.panam.translationapp.ui.*
import com.panam.translationapp.ui.theme.TranslationAppTheme
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TranslationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            TranslationAppTheme(darkTheme = isDarkMode) {
                MainApp(viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: TranslationViewModel) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route ?: Screen.LanguageSelection.route

    Scaffold(
        bottomBar = {
            AppBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (route == Screen.LanguageSelection.route) {
                        // Reset state and go home
                        navController.navigate(route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        AppNavigation(
            navController = navController,
            viewModel = viewModel,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: TranslationViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatLoading by viewModel.isChatLoading.collectAsState()
    val askAIResponse by viewModel.askAIResponse.collectAsState()
    val isAskAILoading by viewModel.isAskAILoading.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (state.languagesSelected) Screen.Translation.route else Screen.LanguageSelection.route,
        modifier = modifier
    ) {
        composable(Screen.LanguageSelection.route) {
            LanguageSelectionScreen(
                onLanguagesSelected = { lang1, lang2 ->
                    viewModel.setLanguages(lang1, lang2)
                    navController.navigate(Screen.Translation.route) {
                        popUpTo(Screen.LanguageSelection.route) { inclusive = true }
                    }
                },
                recentSessions = sessions.sortedByDescending { it.createdAt },
                onContinueSession = { session ->
                    viewModel.loadSession(session)
                    navController.navigate(Screen.Translation.route) {
                        popUpTo(Screen.LanguageSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Translation.route) {
            TranslationScreen(
                state = state,
                onPerson1LanguageChange = viewModel::setPerson1Language,
                onPerson2LanguageChange = viewModel::setPerson2Language,
                onSwapLanguages = viewModel::swapLanguages,
                onStartListeningPerson1 = viewModel::startListeningPerson1,
                onStartListeningPerson2 = viewModel::startListeningPerson2,
                onStopListening = viewModel::stopListening,
                onClearError = viewModel::clearError,
                onAskAI = viewModel::askAI,
                askAIResponse = askAIResponse,
                isAskAILoading = isAskAILoading
            )
        }

        composable(Screen.Chat.route) {
            ChatScreen(
                messages = chatMessages,
                onSendMessage = viewModel::sendChatMessage,
                isLoading = isChatLoading,
                onBackClick = {
                    navController.navigate(Screen.LanguageSelection.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                sessions = sessions,
                onSessionClick = { session ->
                    navController.navigate(Screen.SessionDetail.createRoute(session.id))
                },
                onSessionDelete = viewModel::deleteSession,
                onSessionRename = viewModel::renameSession,
                onContinueSession = { session ->
                    viewModel.loadSession(session)
                    navController.navigate(Screen.Translation.route) {
                        popUpTo(Screen.LanguageSelection.route) { inclusive = false }
                    }
                },
                onBackClick = {
                    navController.navigate(Screen.LanguageSelection.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.SessionDetail.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            val session = sessions.find { it.id == sessionId }

            if (session != null) {
                SessionDetailScreen(
                    session = session,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                isDarkMode = isDarkMode,
                onDarkModeToggle = viewModel::setDarkMode,
                ttsSpeed = ttsSpeed,
                onTTSSpeedChange = viewModel::setTTSSpeed,
                onClearAllHistory = viewModel::clearAllHistory,
                onBackClick = {
                    navController.navigate(Screen.LanguageSelection.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

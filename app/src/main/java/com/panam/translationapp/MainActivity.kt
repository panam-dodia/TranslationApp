package com.panam.translationapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.panam.translationapp.ui.LanguageSelectionScreen
import com.panam.translationapp.ui.TranslationScreen
import com.panam.translationapp.ui.theme.TranslationAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TranslationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TranslationAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.state.collectAsState()

                    if (!state.languagesSelected) {
                        LanguageSelectionScreen(
                            onLanguagesSelected = viewModel::setLanguages
                        )
                    } else {
                        TranslationScreen(
                            state = state,
                            onPerson1LanguageChange = viewModel::setPerson1Language,
                            onPerson2LanguageChange = viewModel::setPerson2Language,
                            onSwapLanguages = viewModel::swapLanguages,
                            onStartListeningPerson1 = viewModel::startListeningPerson1,
                            onStartListeningPerson2 = viewModel::startListeningPerson2,
                            onStopListening = viewModel::stopListening,
                            onClearError = viewModel::clearError
                        )
                    }
                }
            }
        }
    }
}
package com.panam.translationapp.ui

import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.panam.translationapp.TranslationState
import com.panam.translationapp.translation.Language

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    state: TranslationState,
    onPerson1LanguageChange: (Language) -> Unit,
    onPerson2LanguageChange: (Language) -> Unit,
    onSwapLanguages: () -> Unit,
    onStartListeningPerson1: () -> Unit,
    onStartListeningPerson2: () -> Unit,
    onStopListening: () -> Unit,
    onClearError: () -> Unit,
    onAskAI: (String) -> Unit = {},
    askAIResponse: String? = null,
    isAskAILoading: Boolean = false
) {
    val person1Language = state.person1Language ?: return
    val person2Language = state.person2Language ?: return

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var showAskAI by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!micPermissionState.status.isGranted) {
            micPermissionState.launchPermissionRequest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar - Professional & Clean
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Live Translate",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Ask AI Button - More Professional
                    FilledTonalButton(
                        onClick = { showAskAI = true },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ask AI",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Person 1 Section
                PersonSection(
                    language = person1Language,
                    text = state.person1Text,
                    isListening = state.isListeningPerson1,
                    audioLevel = state.audioLevel,
                    onLanguageChange = onPerson1LanguageChange,
                    onStartListening = {
                        if (micPermissionState.status.isGranted) {
                            onStartListeningPerson1()
                        } else {
                            micPermissionState.launchPermissionRequest()
                        }
                    },
                    onStopListening = onStopListening,
                    modifier = Modifier.weight(1f),
                    isTranslating = state.isTranslating
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Swap Button - Professional Design
                Surface(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 2.dp
                ) {
                    IconButton(
                        onClick = onSwapLanguages,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap languages",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Person 2 Section
                PersonSection(
                    language = person2Language,
                    text = state.person2Text,
                    isListening = state.isListeningPerson2,
                    audioLevel = state.audioLevel,
                    onLanguageChange = onPerson2LanguageChange,
                    onStartListening = {
                        if (micPermissionState.status.isGranted) {
                            onStartListeningPerson2()
                        } else {
                            micPermissionState.launchPermissionRequest()
                        }
                    },
                    onStopListening = onStopListening,
                    modifier = Modifier.weight(1f),
                    isBottom = true,
                    isTranslating = state.isTranslating
                )
            }
        }

        // Error Snackbar - Professional Design
        state.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    TextButton(
                        onClick = onClearError,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Dismiss", fontWeight = FontWeight.Medium)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // Ask AI Bottom Sheet
    if (showAskAI) {
        AskAIBottomSheet(
            onDismiss = { showAskAI = false },
            onAskQuestion = { question ->
                onAskAI(question)
            },
            aiResponse = askAIResponse,
            isLoading = isAskAILoading
        )
    }
}

@Composable
fun PersonSection(
    language: Language,
    text: String,
    isListening: Boolean,
    audioLevel: Float,
    onLanguageChange: (Language) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
    isBottom: Boolean = false,
    isTranslating: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = if (isBottom) Arrangement.Bottom else Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isBottom) {
            LanguageSelector(
                selectedLanguage = language,
                onLanguageChange = onLanguageChange
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Text Display Card - Professional Design
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = if (isListening) 4.dp else 1.dp,
            border = if (isListening) BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ) else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    isTranslating -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Translating...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    isListening && text.isEmpty() -> {
                        ListeningIndicator()
                    }
                    text.isNotEmpty() -> {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                lineHeight = 28.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        if (isListening) {
                            Spacer(modifier = Modifier.height(16.dp))
                            ListeningIndicator()
                        }
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap microphone to speak",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Mic Button - Professional Design
        MicButton(
            isListening = isListening,
            audioLevel = audioLevel,
            onStartListening = onStartListening,
            onStopListening = onStopListening
        )

        if (isBottom) {
            Spacer(modifier = Modifier.height(16.dp))
            LanguageSelector(
                selectedLanguage = language,
                onLanguageChange = onLanguageChange
            )
        }
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: Language,
    onLanguageChange: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = selectedLanguage.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select language",
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 300.dp)
                .heightIn(max = 400.dp)
        ) {
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (language == selectedLanguage) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (language == selectedLanguage)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onLanguageChange(language)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun MicButton(
    isListening: Boolean,
    audioLevel: Float,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(88.dp)
    ) {
        // Professional pulse effect when listening
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            )
        }

        // Main button - Professional design
        FloatingActionButton(
            onClick = {
                if (isListening) onStopListening() else onStartListening()
            },
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            containerColor = if (isListening)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = if (isListening) 6.dp else 4.dp,
                pressedElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Start",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun ListeningIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "listening")

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Listening",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )

        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 800,
                        delayMillis = index * 200,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

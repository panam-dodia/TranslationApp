package com.panam.translationapp.ui

import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.panam.translationapp.TranslationState
import com.panam.translationapp.translation.Language

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TranslationScreen(
    state: TranslationState,
    onPerson1LanguageChange: (Language) -> Unit,
    onPerson2LanguageChange: (Language) -> Unit,
    onSwapLanguages: () -> Unit,
    onStartListeningPerson1: () -> Unit,
    onStartListeningPerson2: () -> Unit,
    onStopListening: () -> Unit,
    onClearError: () -> Unit
) {
    val person1Language = state.person1Language ?: return
    val person2Language = state.person2Language ?: return

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
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
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Swap Button
            IconButton(
                onClick = onSwapLanguages,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Swap languages",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                isBottom = true
            )
        }

        // Error Snackbar
        state.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                action = {
                    TextButton(onClick = onClearError) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
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
    isBottom: Boolean = false
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
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Mic Button
        MicButton(
            isListening = isListening,
            audioLevel = audioLevel,
            onStartListening = onStartListening,
            onStopListening = onStopListening
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Text Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (text.isEmpty()) "" else text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (isBottom) {
            Spacer(modifier = Modifier.height(24.dp))
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
        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(
                text = selectedLanguage.displayName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language.displayName,
                            fontWeight = if (language == selectedLanguage) FontWeight.SemiBold else FontWeight.Normal
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
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(100.dp)
    ) {
        // Pulse rings when listening
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            )
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(pulseScale * 0.95f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            )
        }

        // Main button
        FilledIconButton(
            onClick = {
                if (isListening) onStopListening() else onStartListening()
            },
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Start",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}



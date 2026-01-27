package com.panam.translationapp.ui

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapVert
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
    onClearError: () -> Unit,
    onDownloadModels: () -> Unit
) {
    // Early return if languages not set
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
        // Show download overlay if downloading
        if (state.isDownloadingModel) {
            ModelDownloadOverlay(
                message = state.downloadMessage,
                progress = state.downloadProgress
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Person 1 Section (Top)
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

            // Center Controls
            CenterControls(
                person1Language = person1Language,
                person2Language = person2Language,
                person1ToPerson2ModelDownloaded = state.person1ToPerson2ModelDownloaded,
                person2ToPerson1ModelDownloaded = state.person2ToPerson1ModelDownloaded,
                onSwapLanguages = onSwapLanguages,
                onDownloadModels = onDownloadModels,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Person 2 Section (Bottom)
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Text Display
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (text.isEmpty()) "Tap to speak" else text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 28.sp
                    ),
                    color = if (text.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mic Button
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
        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
            Text(
                text = selectedLanguage.displayName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
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
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1f + (audioLevel / 50f).coerceIn(0f, 0.3f) else 1f,
        label = "micScale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Pulse effect when listening
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
        }

        // Main button
        FilledIconButton(
            onClick = {
                if (isListening) onStopListening() else onStartListening()
            },
            modifier = Modifier
                .size(64.dp)
                .scale(if (isListening) scale else 1f),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isListening)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isListening)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Start",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun CenterControls(
    person1Language: Language,
    person2Language: Language,
    person1ToPerson2ModelDownloaded: Boolean,
    person2ToPerson1ModelDownloaded: Boolean,
    onSwapLanguages: () -> Unit,
    onDownloadModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language display
            Text(
                text = person1Language.code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Swap button
            FilledTonalIconButton(
                onClick = onSwapLanguages,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Swap languages",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = person2Language.code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        // Show download button if models not available
        if (!person1ToPerson2ModelDownloaded || !person2ToPerson1ModelDownloaded) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDownloadModels) {
                Text(
                    text = "Download Models",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun ModelDownloadOverlay(
    message: String,
    progress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Downloading Model",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

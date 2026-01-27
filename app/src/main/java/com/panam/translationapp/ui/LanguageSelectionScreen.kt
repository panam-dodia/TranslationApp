package com.panam.translationapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.panam.translationapp.translation.Language

@Composable
fun LanguageSelectionScreen(
    onLanguagesSelected: (Language, Language) -> Unit
) {
    var selectedLanguage1 by remember { mutableStateOf<Language?>(null) }
    var selectedLanguage2 by remember { mutableStateOf<Language?>(null) }
    var currentStep by remember { mutableStateOf(1) } // 1 = select lang1, 2 = select lang2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Title
            Text(
                text = if (currentStep == 1) "Select your language" else "Select their language",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (currentStep == 1) "The language you speak" else "The language they speak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Language List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Language.entries) { language ->
                    val isSelected = if (currentStep == 1) {
                        selectedLanguage1 == language
                    } else {
                        selectedLanguage2 == language
                    }

                    val isDisabled = if (currentStep == 2) {
                        language == selectedLanguage1
                    } else {
                        false
                    }

                    LanguageItem(
                        language = language,
                        isSelected = isSelected,
                        isDisabled = isDisabled,
                        onClick = {
                            if (!isDisabled) {
                                if (currentStep == 1) {
                                    selectedLanguage1 = language
                                } else {
                                    selectedLanguage2 = language
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue Button
            if (currentStep == 1 && selectedLanguage1 != null) {
                Button(
                    onClick = { currentStep = 2 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Continue")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (currentStep == 2 && selectedLanguage2 != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentStep = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text("Back")
                    }

                    Button(
                        onClick = {
                            onLanguagesSelected(selectedLanguage1!!, selectedLanguage2!!)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Start")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    isDisabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDisabled) { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            isDisabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

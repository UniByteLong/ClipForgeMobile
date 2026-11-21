package com.clipforge.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.mobile.ai.GeminiService
import com.clipforge.mobile.settings.SettingsManager
import com.clipforge.mobile.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: SettingsManager,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSettingsUpdated: () -> Unit
) {
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var modelName by remember { mutableStateOf(settings.modelName) }
    var showKey by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val presetModels = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройки", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gemini API", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        settings.apiKey = it
                        onSettingsUpdated()
                    },
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Модель AI", fontSize = 13.sp, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetModels) { preset ->
                        FilterChip(
                            selected = modelName == preset,
                            onClick = {
                                modelName = preset
                                settings.modelName = preset
                                onSettingsUpdated()
                            },
                            label = { Text(preset, fontSize = 12.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = modelName,
                    onValueChange = {
                        modelName = it
                        settings.modelName = it
                        onSettingsUpdated()
                    },
                    label = { Text("Имя модели (можно ввести вручную)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        scope.launch {
                            isChecking = true
                            statusText = null
                            val res = GeminiService.verifyApiKey(apiKey, modelName)
                            isChecking = false
                            res.onSuccess {
                                isError = false
                                statusText = it
                            }.onFailure {
                                isError = true
                                statusText = it.message
                            }
                        }
                    },
                    enabled = !isChecking && apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Проверка...")
                    } else {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Проверить подключение")
                    }
                }

                statusText?.let { msg ->
                    Text(
                        text = msg,
                        color = if (isError) Color(0xFFEF4444) else Color(0xFF22C55E),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Тёмная тема", fontWeight = FontWeight.Medium)
                    Text("Графитовый интерфейс", fontSize = 12.sp, color = TextSecondary)
                }
                Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
            }
        }

        Text("Версия: 1.0.0-stage2.6", fontSize = 12.sp, color = TextSecondary)
    }
}

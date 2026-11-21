package com.clipforge.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.mobile.ai.BannerChatMessage
import com.clipforge.mobile.ai.GeminiService
import com.clipforge.mobile.ai.SearchMode
import com.clipforge.mobile.ai.VideoMoment
import com.clipforge.mobile.settings.SettingsManager
import com.clipforge.mobile.ui.theme.TextSecondary
import com.clipforge.mobile.utils.VideoMetadata
import com.clipforge.mobile.utils.VideoMetadataExtractor
import kotlinx.coroutines.launch

@Composable
fun CreateAndAnalyzeScreen(
    video: VideoMetadata?,
    settings: SettingsManager,
    moments: List<VideoMoment>,
    onMomentsFound: (List<VideoMoment>) -> Unit,
    onPickVideo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 0 = Файл с телефона, 1 = Ссылка YouTube
    var sourceTab by remember { mutableIntStateOf(0) }
    var youtubeUrl by remember { mutableStateOf("") }
    var youtubeTitle by remember { mutableStateOf<String?>(null) }

    var selectedMode by remember { mutableStateOf(SearchMode.INTERESTING) }
    var customPrompt by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Мини-чат для баннера (фото или видео) и стоп-кадра
    var bannerUri by remember { mutableStateOf<Uri?>(null) }
    var bannerTypeLabel by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var chatMessages by remember {
        mutableStateOf(
            listOf(
                BannerChatMessage(
                    isUser = false,
                    text = "Прикрепи фото/видео баннер и напиши условия. Например: «Заморозь кадр на 2 сек в момент фейла и покажи баннер»."
                )
            )
        )
    }

    val bannerImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            bannerUri = it
            bannerTypeLabel = "Фото-баннер"
            chatMessages = chatMessages + BannerChatMessage(false, "Фото-баннер прикреплён! Напиши условия его появления.")
        }
    }

    val bannerVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            bannerUri = it
            bannerTypeLabel = "Видео-баннер"
            chatMessages = chatMessages + BannerChatMessage(false, "Видео-баннер прикреплён! Напиши условия его вставки.")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("Создать клипы", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // Переключатель источника: Файл / YouTube
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = sourceTab == 0,
                    onClick = { sourceTab = 0 },
                    label = { Text("Файл с телефона") },
                    leadingIcon = { Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = sourceTab == 1,
                    onClick = { sourceTab = 1 },
                    label = { Text("Ссылка YouTube") },
                    leadingIcon = { Icon(Icons.Outlined.Link, null, Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Выбор видео или ввод YouTube ссылки
        item {
            if (sourceTab == 0) {
                if (video == null) {
                    OutlinedButton(
                        onClick = onPickVideo,
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null)
                            Spacer(Modifier.height(6.dp))
                            Text("Выбрать видео из галереи или файлов")
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(video.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = onPickVideo) { Text("Заменить") }
                            }
                            Text(
                                "${VideoMetadataExtractor.formatDuration(video.durationMs)} • ${video.width}x${video.height} • ${VideoMetadataExtractor.formatSize(video.sizeBytes)}",
                                color = TextSecondary, fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = youtubeUrl,
                            onValueChange = { youtubeUrl = it },
                            label = { Text("Вставь ссылку на YouTube") },
                            placeholder = { Text("[https://youtu.be/](https://youtu.be/)...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = youtubeTitle ?: "Публичные видео анализируются напрямую",
                                fontSize = 12.sp,
                                color = if (youtubeTitle != null) Color(0xFF22C55E) else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        GeminiService.fetchYouTubeTitle(youtubeUrl).onSuccess {
                                            youtubeTitle = it
                                        }.onFailure {
                                            youtubeTitle = it.message
                                        }
                                    }
                                },
                                enabled = youtubeUrl.isNotBlank()
                            ) {
                                Text("Проверить")
                            }
                        }
                    }
                }
            }
        }

        // Мини-чат для баннера (фото/видео) и стоп-кадра — ВСЕГДА ВИДЕН
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Чат: баннеры и стоп-кадр", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        if (bannerUri != null) {
                            Text(bannerTypeLabel, fontSize = 11.sp, color = Color(0xFF22C55E), fontWeight = FontWeight.Medium)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { bannerImagePicker.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Outlined.Image, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Фото-баннер", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { bannerVideoPicker.launch("video/*") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Outlined.Movie, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Видео-баннер", fontSize = 12.sp)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chatMessages.takeLast(4).forEach { msg ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (msg.isUser)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (msg.isUser) "Ты: ${msg.text}" else "AI: ${msg.text}",
                                    fontSize = 12.sp,
                                    color = if (msg.isUser) MaterialTheme.colorScheme.onSurface else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Условие для баннера / заморозки...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilledIconButton(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    chatMessages = chatMessages + BannerChatMessage(true, chatInput.trim())
                                    chatInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Send, contentDescription = "Отправить")
                        }
                    }
                }
            }
        }

        // Режим поиска
        item {
            Text("Что искать в видео?", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchMode.entries) { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        label = { Text(mode.label, fontSize = 12.sp) }
                    )
                }
            }
        }

        if (selectedMode == SearchMode.CUSTOM) {
            item {
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    label = { Text("Например: Найди 5 самых смешных моментов") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Кнопка запуска анализа
        item {
            val canAnalyze = (sourceTab == 0 && video != null) || (sourceTab == 1 && youtubeUrl.isNotBlank())
            Button(
                onClick = {
                    scope.launch {
                        isAnalyzing = true
                        errorText = null
                        progressText = "Запуск анализа..."
                        val userRules = chatMessages.filter { it.isUser }.map { it.text }
                        val res = if (sourceTab == 0 && video != null) {
                            GeminiService.analyzeVideo(
                                context = context,
                                videoUri = video.uri,
                                sizeBytes = video.sizeBytes,
                                apiKey = settings.apiKey,
                                model = settings.modelName,
                                mode = selectedMode,
                                customPrompt = customPrompt,
                                bannerRules = userRules,
                                onProgress = { progressText = it }
                            )
                        } else {
                            GeminiService.analyzeYouTubeUrl(
                                youtubeUrl = youtubeUrl,
                                apiKey = settings.apiKey,
                                model = settings.modelName,
                                mode = selectedMode,
                                customPrompt = customPrompt,
                                bannerRules = userRules,
                                onProgress = { progressText = it }
                            )
                        }
                        isAnalyzing = false
                        res.onSuccess { list ->
                            onMomentsFound(list)
                        }.onFailure { err ->
                            errorText = err.message ?: "Неизвестная ошибка"
                        }
                    }
                },
                enabled = !isAnalyzing && canAnalyze,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(progressText, fontSize = 13.sp)
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Найти моменты и точки баннера", fontWeight = FontWeight.SemiBold)
                }
            }

            errorText?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = Color(0xFFEF4444), fontSize = 13.sp)
            }
        }

        // Результаты
        if (moments.isNotEmpty()) {
            item {
                Text("Найденные моменты (${moments.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            items(moments) { moment ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${moment.startTime} — ${moment.endTime}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "★ ${moment.score}/100",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(moment.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(moment.description, fontSize = 13.sp, color = TextSecondary)
                        if (moment.editingTip.isNotBlank()) {
                            Text("✂️ Монтаж: ${moment.editingTip}", fontSize = 12.sp, color = TextSecondary)
                        }

                        moment.bannerAction?.let { banner ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (banner.freezeSeconds > 0f)
                                            "❄️ Стоп-кадр ${banner.freezeSeconds}с + Баннер в ${banner.triggerTime} (на ${banner.bannerDurationSeconds}с)"
                                        else
                                            "🖼 Баннер в ${banner.triggerTime} (на ${banner.bannerDurationSeconds}с)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (banner.reason.isNotBlank()) {
                                        Text(banner.reason, fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

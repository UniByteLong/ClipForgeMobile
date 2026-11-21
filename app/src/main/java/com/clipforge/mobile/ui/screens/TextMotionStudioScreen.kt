package com.clipforge.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.clipforge.mobile.ai.GeminiService
import com.clipforge.mobile.settings.SettingsManager
import com.clipforge.mobile.studio.TextAnimType
import com.clipforge.mobile.studio.TextOverlayItem
import com.clipforge.mobile.studio.TextPreset
import com.clipforge.mobile.studio.toComposeColor
import com.clipforge.mobile.ui.theme.TextSecondary
import com.clipforge.mobile.utils.VideoMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TextMotionStudioScreen(
    video: VideoMetadata?,
    settings: SettingsManager,
    onPickVideo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var overlays by remember {
        mutableStateOf(
            listOf(
                TextOverlayItem(
                    text = "CLIPFORGE STUDIO",
                    startMs = 0L,
                    endMs = 5000L,
                    posX = 0.5f,
                    posY = 0.72f,
                    animation = TextAnimType.FADE_IN
                )
            )
        )
    }
    var selectedId by remember { mutableStateOf(overlays.first().id) }
    val selectedItem = overlays.find { it.id == selectedId } ?: overlays.firstOrNull()

    // Music Sync state
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var bpm by remember { mutableFloatStateOf(120f) }
    var snapToBeat by remember { mutableStateOf(true) }

    // AI Text Command Chat state
    var aiCommand by remember { mutableStateOf("") }
    var aiReply by remember { mutableStateOf<String?>(null) }
    var isAiBusy by remember { mutableStateOf(false) }

    // Active tool tab: 0 = Текст/Стили, 1 = Анимация, 2 = Музыка/Биты, 3 = AI Чат
    var toolTab by remember { mutableIntStateOf(0) }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) audioUri = uri
    }

    // ExoPlayer для реального предпросмотра
    val exoPlayer = remember(video?.uri) {
        ExoPlayer.Builder(context).build().apply {
            video?.uri?.let {
                setMediaItem(MediaItem.fromUri(it))
                repeatMode = Player.REPEAT_MODE_ALL
                prepare()
                playWhenReady = true
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    var currentPosMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition
            delay(33) // ~30 fps обновление анимаций текста
        }
    }

    var previewSize by remember { mutableStateOf(IntSize(1, 1)) }

    fun updateSelected(transform: (TextOverlayItem) -> TextOverlayItem) {
        overlays = overlays.map { if (it.id == selectedId) transform(it) else it }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Text & Motion Studio", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (video == null) {
                    TextButton(onClick = onPickVideo) { Text("Выбрать видео") }
                }
            }
        }

        // Окно предпросмотра с живыми анимациями и перетаскиванием пальцем
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .onSizeChanged { previewSize = it }
            ) {
                if (video != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Выбери видео для предпросмотра (текст уже можно настраивать)", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                // Слой текстовых элементов
                overlays.forEach { item ->
                    val isVisible = video == null || (currentPosMs in item.startMs..item.endMs)
                    if (isVisible) {
                        val elapsed = (currentPosMs - item.startMs).coerceAtLeast(0L)
                        val progress = (elapsed / 600f).coerceIn(0f, 1f)

                        var animAlpha = item.opacity
                        var animScale = 1f
                        var animOffsetY = 0f
                        var displayText = item.text

                        when (item.animation) {
                            TextAnimType.FADE_IN -> animAlpha *= progress
                            TextAnimType.SLIDE_UP -> {
                                animAlpha *= progress
                                animOffsetY = (1f - progress) * 40f
                            }
                            TextAnimType.ZOOM_IN -> animScale = 0.5f + 0.5f * progress
                            TextAnimType.POP -> animScale = if (progress < 0.7f) 0.4f + progress * 1.1f else 1f
                            TextAnimType.TYPEWRITER -> {
                                val chars = ((elapsed / 70L).toInt()).coerceIn(1, item.text.length.coerceAtLeast(1))
                                displayText = item.text.take(chars)
                            }
                            TextAnimType.PULSE -> animScale = 1f + 0.06f * sin(elapsed / 150.0).toFloat()
                            TextAnimType.FLOATING -> animOffsetY = 8f * sin(elapsed / 250.0).toFloat()
                            TextAnimType.NONE -> {}
                        }

                        val xPx = (item.posX * previewSize.width).roundToInt() - 100
                        val yPx = (item.posY * previewSize.height + animOffsetY).roundToInt() - 30

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(xPx.coerceIn(0, (previewSize.width - 80).coerceAtLeast(0)), yPx.coerceIn(0, (previewSize.height - 40).coerceAtLeast(0))) }
                                .scale(animScale)
                                .alpha(animAlpha)
                                .clip(RoundedCornerShape(8.dp))
                                .background(item.bgColor.toComposeColor())
                                .pointerInput(item.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        selectedId = item.id
                                        val dx = dragAmount.x / previewSize.width.coerceAtLeast(1)
                                        val dy = dragAmount.y / previewSize.height.coerceAtLeast(1)
                                        updateSelected {
                                            it.copy(
                                                posX = (it.posX + dx).coerceIn(0.05f, 0.95f),
                                                posY = (it.posY + dy).coerceIn(0.05f, 0.95f)
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = displayText,
                                style = TextStyle(
                                    color = item.textColor.toComposeColor(),
                                    fontSize = item.fontSize.sp,
                                    fontWeight = if (item.isBold) FontWeight.Bold else FontWeight.Normal,
                                    shadow = if (item.hasGlow) Shadow(
                                        color = item.textColor.toComposeColor(),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 18f
                                    ) else null
                                )
                            )
                        }
                    }
                }
            }
        }

        // Таймлайн текстовых дорожек
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Текстовые слои (${overlays.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                TextButton(onClick = {
                    val beatStepMs = if (snapToBeat) (60000f / bpm).toLong() * 4 else 3000L
                    val lastEnd = overlays.maxOfOrNull { it.endMs } ?: 0L
                    val newItem = TextOverlayItem(
                        text = "Фраза #${overlays.size + 1}",
                        startMs = lastEnd,
                        endMs = lastEnd + beatStepMs
                    )
                    overlays = overlays + newItem
                    selectedId = newItem.id
                }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить слой")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(overlays) { item ->
                    FilterChip(
                        selected = item.id == selectedId,
                        onClick = { selectedId = item.id },
                        label = {
                            Text("${item.text.take(14)} (${item.startMs / 1000}с-${item.endMs / 1000}с)", fontSize = 11.sp)
                        }
                    )
                }
            }
        }

        // Панель вкладок инструментов
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Стили", "Анимация", "Музыка", "AI Чат").forEachIndexed { idx, title ->
                    FilterChip(
                        selected = toolTab == idx,
                        onClick = { toolTab = idx },
                        label = { Text(title, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Содержимое выбранного инструмента
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (toolTab) {
                        0 -> if (selectedItem != null) {
                            OutlinedTextField(
                                value = selectedItem.text,
                                onValueChange = { txt -> updateSelected { it.copy(text = txt) } },
                                label = { Text("Текст надписи") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Text("Пресеты стилей", fontSize = 12.sp, color = TextSecondary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(TextPreset.entries) { preset ->
                                    AssistChip(
                                        onClick = {
                                            updateSelected {
                                                it.copy(
                                                    textColor = preset.colorHex,
                                                    bgColor = preset.bgHex,
                                                    fontSize = preset.fontSize,
                                                    isBold = preset.isBold,
                                                    hasGlow = preset.hasGlow,
                                                    animation = preset.anim
                                                )
                                            }
                                        },
                                        label = { Text(preset.label, fontSize = 12.sp) }
                                    )
                                }
                            }
                            Text("Размер шрифта: ${selectedItem.fontSize.toInt()}", fontSize = 12.sp, color = TextSecondary)
                            Slider(
                                value = selectedItem.fontSize,
                                onValueChange = { sz -> updateSelected { it.copy(fontSize = sz) } },
                                valueRange = 14f..48f
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Свечение (Glow)", fontSize = 13.sp)
                                Switch(
                                    checked = selectedItem.hasGlow,
                                    onCheckedChange = { g -> updateSelected { it.copy(hasGlow = g) } }
                                )
                            }
                        }

                        1 -> if (selectedItem != null) {
                            Text("Анимация появления и движения", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(TextAnimType.entries) { anim ->
                                    FilterChip(
                                        selected = selectedItem.animation == anim,
                                        onClick = { updateSelected { it.copy(animation = anim) } },
                                        label = { Text(anim.label, fontSize = 12.sp) }
                                    )
                                }
                            }
                            Text("Длительность показа: ${(selectedItem.endMs - selectedItem.startMs) / 1000f} сек", fontSize = 12.sp, color = TextSecondary)
                            Slider(
                                value = ((selectedItem.endMs - selectedItem.startMs) / 1000f).coerceIn(1f, 15f),
                                onValueChange = { sec ->
                                    updateSelected { it.copy(endMs = it.startMs + (sec * 1000).toLong()) }
                                },
                                valueRange = 1f..15f
                            )
                        }

                        2 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (audioUri != null) "Аудиодорожка подключена" else "Музыка не выбрана",
                                    fontSize = 13.sp,
                                    color = if (audioUri != null) Color(0xFF22C55E) else TextSecondary
                                )
                                OutlinedButton(onClick = { audioPicker.launch("audio/*") }) {
                                    Text("Выбрать трек", fontSize = 12.sp)
                                }
                            }
                            Text("Темп (BPM): ${bpm.toInt()} • Бит каждые ${(60000 / bpm).toInt()} мс", fontSize = 12.sp, color = TextSecondary)
                            Slider(value = bpm, onValueChange = { bpm = it }, valueRange = 60f..180f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Привязка слоёв к битам (Snap to Beat)", fontSize = 13.sp)
                                Switch(checked = snapToBeat, onCheckedChange = { snapToBeat = it })
                            }
                        }

                        3 -> {
                            Text("AI-команды и генератор фраз (мгновенно, без загрузки видео)", fontSize = 12.sp, color = TextSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = aiCommand,
                                    onValueChange = { aiCommand = it },
                                    placeholder = { Text("Например: Сделай неон и фразу про ночь", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                FilledIconButton(
                                    enabled = !isAiBusy && aiCommand.isNotBlank(),
                                    onClick = {
                                        scope.launch {
                                            isAiBusy = true
                                            val res = GeminiService.runTextStudioCommand(
                                                apiKey = settings.apiKey,
                                                model = settings.modelName,
                                                userCommand = aiCommand,
                                                currentText = selectedItem?.text ?: ""
                                            )
                                            isAiBusy = false
                                            res.onSuccess { json ->
                                                aiReply = json.optString("reply", "Готово!")
                                                val newText = json.optString("new_text", "").takeIf { it.isNotBlank() && it != "null" }
                                                val presetName = json.optString("preset", "").takeIf { it.isNotBlank() && it != "null" }
                                                val animName = json.optString("animation", "").takeIf { it.isNotBlank() && it != "null" }
                                                val posY = if (!json.isNull("pos_y")) json.optDouble("pos_y", -1.0).toFloat() else -1f
                                                val glow = if (!json.isNull("has_glow")) json.optBoolean("has_glow") else null

                                                updateSelected { cur ->
                                                    var updated = cur
                                                    if (newText != null) updated = updated.copy(text = newText)
                                                    if (presetName != null) {
                                                        TextPreset.entries.find { it.name == presetName }?.let { p ->
                                                            updated = updated.copy(
                                                                textColor = p.colorHex, bgColor = p.bgHex,
                                                                fontSize = p.fontSize, isBold = p.isBold,
                                                                hasGlow = p.hasGlow, animation = p.anim
                                                            )
                                                        }
                                                    }
                                                    if (animName != null) {
                                                        TextAnimType.entries.find { it.name == animName }?.let { a ->
                                                            updated = updated.copy(animation = a)
                                                        }
                                                    }
                                                    if (posY in 0.05f..0.95f) updated = updated.copy(posY = posY)
                                                    if (glow != null) updated = updated.copy(hasGlow = glow)
                                                    updated
                                                }
                                                aiCommand = ""
                                            }.onFailure { err ->
                                                aiReply = err.message
                                            }
                                        }
                                    }
                                ) {
                                    if (isAiBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                    else Icon(Icons.Outlined.AutoAwesome, contentDescription = "Применить")
                                }
                            }
                            aiReply?.let { reply ->
                                Text("AI: $reply", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

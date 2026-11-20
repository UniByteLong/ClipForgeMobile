package com.clipforge.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.mobile.ui.theme.ClipForgeTheme
import com.clipforge.mobile.ui.theme.TextSecondary
import com.clipforge.mobile.utils.VideoMetadata
import com.clipforge.mobile.utils.VideoMetadataExtractor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            ClipForgeTheme(darkTheme = isDarkTheme) {
                ClipForgeApp(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Composable
fun ClipForgeApp(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedVideo by remember { mutableStateOf<VideoMetadata?>(null) }
    val context = LocalContext.current

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideo = VideoMetadataExtractor.extract(context, it)
            selectedTab = 1
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                val items = listOf(
                    "Главная" to Icons.Outlined.Home,
                    "Создать" to Icons.Outlined.AddCircleOutline,
                    "Проекты" to Icons.Outlined.FolderOpen,
                    "Настройки" to Icons.Outlined.Settings
                )
                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            when (selectedTab) {
                0 -> HomeScreen(onPickVideo = { videoPicker.launch("video/*") })
                1 -> CreateScreen(
                    video = selectedVideo,
                    onPickVideo = { videoPicker.launch("video/*") }
                )
                2 -> ProjectsPlaceholder()
                3 -> SettingsScreen(isDarkTheme, onToggleTheme)
            }
        }
    }
}

@Composable
fun HomeScreen(onPickVideo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("ClipForge", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("AI-нарезчик вертикальных клипов", color = TextSecondary, fontSize = 14.sp)

        Button(
            onClick = onPickVideo,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.VideoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Выбрать видео для нарезки", fontWeight = FontWeight.SemiBold)
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Статус AI", fontSize = 12.sp, color = TextSecondary)
                    Text("Gemini API: ожидание ключа", fontWeight = FontWeight.Medium)
                }
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun CreateScreen(video: VideoMetadata?, onPickVideo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Новый проект", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        if (video == null) {
            OutlinedButton(
                onClick = onPickVideo,
                modifier = Modifier.fillMaxWidth().height(120.dp),
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(video.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "Длительность: ${VideoMetadataExtractor.formatDuration(video.durationMs)} • ${video.width}x${video.height}",
                        color = TextSecondary, fontSize = 13.sp
                    )
                    Text(
                        "Размер: ${VideoMetadataExtractor.formatSize(video.sizeBytes)} • FPS: ${video.fps}",
                        color = TextSecondary, fontSize = 13.sp
                    )
                }
            }
            OutlinedButton(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать другое видео")
            }
        }
    }
}

@Composable
fun ProjectsPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Сохраненные проекты появятся после подключения базы Room", color = TextSecondary)
    }
}

@Composable
fun SettingsScreen(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Настройки", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Тёмная тема")
            Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
        }
    }
}

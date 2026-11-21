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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.mobile.ai.VideoMoment
import com.clipforge.mobile.settings.SettingsManager
import com.clipforge.mobile.ui.screens.CreateAndAnalyzeScreen
import com.clipforge.mobile.ui.screens.SettingsScreen
import com.clipforge.mobile.ui.screens.TextMotionStudioScreen
import com.clipforge.mobile.ui.theme.ClipForgeTheme
import com.clipforge.mobile.ui.theme.TextSecondary
import com.clipforge.mobile.utils.VideoMetadata
import com.clipforge.mobile.utils.VideoMetadataExtractor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsManager(this)
        setContent {
            var isDarkTheme by remember { mutableStateOf(settings.isDarkTheme) }
            ClipForgeTheme(darkTheme = isDarkTheme) {
                ClipForgeApp(
                    settings = settings,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        isDarkTheme = !isDarkTheme
                        settings.isDarkTheme = isDarkTheme
                    }
                )
            }
        }
    }
}

@Composable
fun ClipForgeApp(
    settings: SettingsManager,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedVideo by remember { mutableStateOf<VideoMetadata?>(null) }
    var foundMoments by remember { mutableStateOf<List<VideoMoment>>(emptyList()) }
    var hasApiKey by remember { mutableStateOf(settings.apiKey.isNotBlank()) }
    val context = LocalContext.current

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideo = VideoMetadataExtractor.extract(context, it)
            foundMoments = emptyList()
            if (selectedTab == 0) selectedTab = 1
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
                    "Студия" to Icons.Outlined.TextFields,
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
                0 -> HomeScreen(
                    hasApiKey = hasApiKey,
                    modelName = settings.modelName,
                    onPickVideo = { videoPicker.launch("video/*") },
                    onOpenStudio = { selectedTab = 2 },
                    onOpenSettings = { selectedTab = 3 }
                )
                1 -> CreateAndAnalyzeScreen(
                    video = selectedVideo,
                    settings = settings,
                    moments = foundMoments,
                    onMomentsFound = { foundMoments = it },
                    onPickVideo = { videoPicker.launch("video/*") }
                )
                2 -> TextMotionStudioScreen(
                    video = selectedVideo,
                    settings = settings,
                    onPickVideo = { videoPicker.launch("video/*") }
                )
                3 -> SettingsScreen(
                    settings = settings,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onSettingsUpdated = { hasApiKey = settings.apiKey.isNotBlank() }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    hasApiKey: Boolean,
    modelName: String,
    onPickVideo: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("ClipForge", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("AI-нарезчик и Text & Motion Studio", color = TextSecondary, fontSize = 14.sp)

        Button(
            onClick = onPickVideo,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.VideoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Выбрать видео для нарезки", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = onOpenStudio,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.TextFields, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Открыть Text & Motion Studio")
        }

        Surface(
            onClick = onOpenSettings,
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
                    Text(
                        text = if (hasApiKey) "Готов ($modelName)" else "Нажми, чтобы добавить API Key",
                        fontWeight = FontWeight.Medium,
                        color = if (hasApiKey) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

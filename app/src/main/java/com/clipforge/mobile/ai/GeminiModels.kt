package com.clipforge.mobile.ai

data class BannerAction(
    val triggerTime: String,
    val freezeSeconds: Float,
    val bannerDurationSeconds: Float,
    val reason: String
)

data class VideoMoment(
    val title: String,
    val startTime: String,
    val endTime: String,
    val description: String,
    val reason: String,
    val score: Int,
    val emotionScore: Int,
    val editingTip: String,
    val bannerAction: BannerAction? = null
)

data class BannerChatMessage(
    val isUser: Boolean,
    val text: String
)

enum class SearchMode(val label: String, val instruction: String) {
    INTERESTING("🔥 Интересные", "Найди самые интересные и захватывающие моменты с высоким потенциалом удержания."),
    FUNNY("😂 Смешные", "Найди самые смешные, комичные и нелепые моменты."),
    UNEXPECTED("😮 Неожиданные", "Найди неожиданные повороты, сюрпризы и резкие смены событий."),
    QUOTES("🎙 Цитаты", "Найди сильные, емкие цитаты и яркие высказывания."),
    STORIES("📚 Истории", "Найди законченные интересные мини-истории с началом и концовкой."),
    INFORMATIVE("🧠 Информативные", "Найди самые полезные объяснения, факты и важные выводы."),
    DEBATES("💬 Споры", "Найди эмоциональные споры, дискуссии и столкновения мнений."),
    UNIVERSAL("🎬 Универсальный", "Найди лучшие разнообразные моменты, которые понятны без просмотра всего видео."),
    CUSTOM("✍️ Свой запрос", "")
}

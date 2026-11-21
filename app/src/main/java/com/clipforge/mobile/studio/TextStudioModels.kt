package com.clipforge.mobile.studio

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class TextAnimType(val label: String) {
    NONE("Нет"),
    FADE_IN("Fade In"),
    SLIDE_UP("Slide Up"),
    ZOOM_IN("Zoom In"),
    POP("Pop"),
    TYPEWRITER("Typewriter"),
    PULSE("Pulse"),
    FLOATING("Floating")
}

enum class TextPreset(
    val label: String,
    val colorHex: Long,
    val bgHex: Long,
    val fontSize: Float,
    val isBold: Boolean,
    val hasGlow: Boolean,
    val anim: TextAnimType
) {
    MINIMAL_WHITE("Minimal White", 0xFFFFFFFF, 0x00000000, 22f, false, false, TextAnimType.FADE_IN),
    CINEMATIC("Cinematic", 0xFFF3F4F6, 0x00000000, 26f, false, true, TextAnimType.SLIDE_UP),
    BOLD_CAPTION("Bold Caption", 0xFFFFFFFF, 0xCC000000, 28f, true, false, TextAnimType.POP),
    NEON("Neon", 0xFF38BDF8, 0x00000000, 26f, true, true, TextAnimType.PULSE),
    RETRO("Retro", 0xFFFACC15, 0x991E1B4B, 24f, true, false, TextAnimType.TYPEWRITER),
    EDITORIAL("Editorial", 0xFF111827, 0xEEFFFFFF, 22f, true, false, TextAnimType.SLIDE_UP),
    LUXURY("Luxury", 0xFFFDE68A, 0x66000000, 24f, false, true, TextAnimType.FADE_IN),
    DARK_AESTHETIC("Dark Aesthetic", 0xFFC084FC, 0xAA0B0B0F, 24f, true, true, TextAnimType.FLOATING)
}

data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Новый текст",
    val startMs: Long = 0L,
    val endMs: Long = 4000L,
    val posX: Float = 0.5f, // 0..1 относительно ширины
    val posY: Float = 0.75f, // 0..1 относительно высоты
    val fontSize: Float = 24f,
    val textColor: Long = 0xFFFFFFFF,
    val bgColor: Long = 0x66000000,
    val isBold: Boolean = true,
    val hasGlow: Boolean = false,
    val opacity: Float = 1f,
    val animation: TextAnimType = TextAnimType.FADE_IN
)

fun Long.toComposeColor(): Color = Color(this.toInt())

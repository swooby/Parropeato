package com.swooby.parropeato

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.swooby.parropeato.common.R

data class AccentColorOption(val nameResId: Int, val color: Color, val analyticsName: String) {
    val argb: Int get() = color.toArgb()
}

val ACCENT_COLOR_DEFAULT_ARGB: Int = Color(0xFFBB86FC).toArgb()

val ACCENT_COLOR_OPTIONS: List<AccentColorOption> = listOf(
    AccentColorOption(R.string.accent_color_purple, Color(0xFFBB86FC), "purple"),
    AccentColorOption(R.string.accent_color_teal,   Color(0xFF03DAC6), "teal"),
    AccentColorOption(R.string.accent_color_blue,   Color(0xFF40C4FF), "blue"),
    AccentColorOption(R.string.accent_color_green,  Color(0xFF69F0AE), "green"),
    AccentColorOption(R.string.accent_color_yellow, Color(0xFFFFD740), "yellow"),
    AccentColorOption(R.string.accent_color_orange, Color(0xFFFF6D00), "orange"),
    AccentColorOption(R.string.accent_color_pink,   Color(0xFFFF4081), "pink"),
    AccentColorOption(R.string.accent_color_red,    Color(0xFFFF5252), "red"),
)

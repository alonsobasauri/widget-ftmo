package com.basauri.ftmowidget.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

object WidgetTheme {
    val Background = Color(0xFF1A1F2B)
    val Surface = Color(0xFF222837)
    val SurfaceMuted = Color(0xFF2D3445)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
    val TextMuted = Color(0x80FFFFFF)
    val Accent = Color(0xFF3D8EFF)
    val Success = Color(0xFF3FCB6C)
    val Danger = Color(0xFFFF5C5C)
    val Warning = Color(0xFFFFB547)

    val cardPadding = 12.dp
    val cardCorner = 20.dp

    fun titleStyle() = TextStyle(
        color = ColorProvider(TextMuted),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )

    fun primaryStyle() = TextStyle(
        color = ColorProvider(TextPrimary),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )

    fun secondaryStyle() = TextStyle(
        color = ColorProvider(TextSecondary),
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    )

    fun valueStyle(color: Color) = TextStyle(
        color = ColorProvider(color),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )

    fun colorForAmount(amount: Double): Color = when {
        amount > 0 -> Success
        amount < 0 -> Danger
        else -> TextPrimary
    }
}

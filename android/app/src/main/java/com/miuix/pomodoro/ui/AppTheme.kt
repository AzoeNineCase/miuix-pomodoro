package com.miuix.pomodoro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.miuix.pomodoro.data.Settings
import com.miuix.pomodoro.data.ThemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 短休息 / 长休息的功能色（与网页版一致） */
val ShortBreakColor = Color(0xFF36D167)
val LongBreakColor = Color(0xFF00BCD4)

/** 按权重混合两个颜色，用于从强调色推导出容器色 */
fun mixColor(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = 1f,
)

/**
 * 应用主题：以 MiuixTheme 为基底，把用户选择的强调色注入主色板，
 * 对应网页版的「强调色 + 深/浅色 + 跟随系统」。
 */
@Composable
fun AppTheme(
    settings: Settings,
    translucentSurface: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val accent = Color(settings.accent)

    val colors = remember(dark, accent, translucentSurface) {
        // 使用自定义背景图时，把卡片底色改为半透明，形成毛玻璃质感的层次
        val cardAlpha = if (translucentSurface) 0.62f else 1f
        val surfaceAlpha = if (translucentSurface) 0.5f else 1f
        if (dark) {
            darkColorScheme(
                primary = accent,
                primaryVariant = accent,
                primaryContainer = mixColor(accent, Color.White, 0.16f),
                onPrimaryVariant = mixColor(accent, Color.White, 0.55f),
                tertiaryContainer = mixColor(accent, Color.Black, 0.72f),
                onTertiaryContainer = accent,
                background = Color(0xFF1A1A1C),
                surface = Color(0xFF242426).copy(alpha = surfaceAlpha),
                surfaceContainer = Color(0xFF242426).copy(alpha = cardAlpha),
                surfaceContainerHigh = Color(0xFF323236).copy(alpha = cardAlpha),
                surfaceContainerHighest = Color(0xFF3A3A3F).copy(alpha = cardAlpha),
            )
        } else {
            lightColorScheme(
                primary = accent,
                primaryVariant = accent,
                primaryContainer = mixColor(accent, Color.White, 0.18f),
                onPrimaryVariant = mixColor(accent, Color.White, 0.62f),
                tertiaryContainer = mixColor(accent, Color.White, 0.9f),
                onTertiaryContainer = accent,
                background = Color(0xFFF4F5F7),
                surface = Color.White.copy(alpha = surfaceAlpha),
                surfaceContainer = Color.White.copy(alpha = cardAlpha),
                surfaceContainerHigh = Color(0xFFECEDF0).copy(alpha = cardAlpha),
                surfaceContainerHighest = Color(0xFFE4E5E8).copy(alpha = cardAlpha),
            )
        }
    }

    MiuixTheme(colors = colors, content = content)
}

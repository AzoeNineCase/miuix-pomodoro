package com.example.pomodoro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.pomodoro.ui.AppFonts

/**
 * 设计令牌：**逐条对应** `index.html` 里 `:root` / `[data-theme]` 的 CSS 变量，
 * 保证原生实现与网页版（以及内嵌同一份网页的安卓版）取色一致。
 */
@Immutable
data class AppColors(
    /** 深色系（含极光） */
    val dark: Boolean,
    /** 极光主题：页面自带渐变背景、卡片为毛玻璃 */
    val aurora: Boolean,

    val primary: Color,
    val onPrimary: Color,
    /** --miuix-soft：强调色的半透明容器（图标底、选中态） */
    val soft: Color,

    val background: Color,
    val surface: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,

    val onSurface: Color,
    /** --miuix-on-surface-variant */
    val variant: Color,
    val outline: Color,
    val divider: Color,

    val short: Color,
    val long: Color,
    val error: Color,
    val dangerSoft: Color,

    val shadow: Color,
    val shadowLg: Color,
)

/** 网页把强调色写进内联样式：primary = accent，soft = accent@14%（浅）/22%（深） */
fun accentColors(accent: Color, dark: Boolean) = accent to accent.copy(alpha = if (dark) 0.22f else 0.14f)

private val LightColors = AppColors(
    dark = false, aurora = false,
    primary = Color(0xFF3482FF), onPrimary = Color.White, soft = Color(0x1F3482FF),
    background = Color(0xFFF4F5F7), surface = Color.White, container = Color.White,
    containerHigh = Color(0xFFECEDF0), containerHighest = Color(0xFFE4E5E8),
    onSurface = Color(0xFF0F0F0F), variant = Color(0xFF959595),
    outline = Color(0xFFD9D9D9), divider = Color(0xFFE6E6E6),
    short = Color(0xFF36D167), long = Color(0xFF00BCD4),
    error = Color(0xFFE94634), dangerSoft = Color(0x1AE94634),
    shadow = Color(0x0F000000), shadowLg = Color(0x1A000000),
)

private val DarkColors = AppColors(
    dark = true, aurora = false,
    primary = Color(0xFF277AF7), onPrimary = Color.White, soft = Color(0x33277AF7),
    background = Color(0xFF1A1A1C), surface = Color(0xFF242426), container = Color(0xFF2A2A2D),
    containerHigh = Color(0xFF323236), containerHighest = Color(0xFF3A3A3F),
    onSurface = Color(0xFFE6E6E6), variant = Color(0xFF8C93B0),
    outline = Color(0xFF4A4A4E), divider = Color(0xFF3A3A3E),
    short = Color(0xFF36D167), long = Color(0xFF00BCD4),
    error = Color(0xFFF12522), dangerSoft = Color(0x29F12522),
    shadow = Color(0x59000000), shadowLg = Color(0x8C000000),
)

private val AuroraColors = AppColors(
    dark = true, aurora = true,
    primary = Color(0xFF8B7BFF), onPrimary = Color.White, soft = Color(0x388B7BFF),
    background = Color(0xFF0B0D1F), surface = Color(0x14FFFFFF), container = Color(0x12FFFFFF),
    containerHigh = Color(0x1FFFFFFF), containerHighest = Color(0x29FFFFFF),
    onSurface = Color(0xFFEEF0FF), variant = Color(0xFFAAB3E2),
    outline = Color(0x24FFFFFF), divider = Color(0x1AFFFFFF),
    short = Color(0xFF36D167), long = Color(0xFF00BCD4),
    error = Color(0xFFFF6B81), dangerSoft = Color(0x2EFF6B81),
    shadow = Color(0x52000000), shadowLg = Color(0x75000000),
)

fun appColors(theme: String, systemDark: Boolean, accent: String): AppColors {
    val base = when (theme) {
        "light" -> LightColors
        "dark" -> DarkColors
        "aurora" -> AuroraColors
        else -> if (systemDark) DarkColors else LightColors
    }
    accentColorOrNull(accent)?.let { acc ->
        val (p, soft) = accentColors(acc, base.dark)
        return base.copy(primary = p, soft = soft)
    }
    return base
}

fun accentColorOrNull(hex: String): Color? = runCatching {
    val h = hex.removePrefix("#")
    if (h.length != 6) return null
    Color(0xFF000000L or h.toLong(16))
}.getOrNull()

/** 设置页「强调色」可选项（与网页 KEY_COLORS 一致） */
val KEY_COLORS: List<Pair<String, String>> = listOf(
    "蓝" to "#3482FF",
    "绿" to "#36D167",
    "紫" to "#7C4DFF",
    "黄" to "#FFB21D",
    "橙" to "#FF5722",
    "粉" to "#E91E63",
    "青" to "#00BCD4",
)

@Immutable
data class AppTypography(
    val font: FontFamily,
    val symbols: FontFamily,
)

val LocalAppColors = staticCompositionLocalOf { LightColors }
val LocalAppType = staticCompositionLocalOf<AppTypography> { error("PomodoroTheme 未初始化") }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val font: FontFamily
        @Composable @ReadOnlyComposable get() = LocalAppType.current.font

    val symbols: FontFamily
        @Composable @ReadOnlyComposable get() = LocalAppType.current.symbols
}

@Composable
fun PomodoroTheme(theme: String, systemDark: Boolean, accent: String, content: @Composable () -> Unit) {
    val colors = appColors(theme, systemDark, accent)
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppType provides AppTypography(AppFonts.inter, AppFonts.symbols),
        content = content,
    )
}

/** 网页里用到的 font-weight 取值别名 */
object W {
    val normal = FontWeight(400)
    val medium = FontWeight(500)
    val semi = FontWeight(600)
    val bold = FontWeight(700)
    val extra = FontWeight(800)
}

package com.miuix.pomodoro.data

/** 三种计时模式，顺序与界面上的分段按钮一致 */
enum class TimerMode(val label: String) {
    Focus("专注"),
    Short("短休息"),
    Long("长休息"),
}

enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

enum class BgMode(val label: String) {
    Gradient("渐变"),
    Custom("自定义"),
}

/** 一天的专注统计 */
data class DayStat(
    val sessions: Int = 0,
    val focusMin: Int = 0,
    val best: Int = 0,
)

data class TodoItem(
    val id: Long,
    val text: String,
    val done: Boolean = false,
)

data class Settings(
    val focusMinutes: Int = 25,
    val shortMinutes: Int = 5,
    val longMinutes: Int = 15,
    val sessionsBeforeLong: Int = 4,
    val autoStart: Boolean = false,
    val sound: Boolean = true,
    val notify: Boolean = false,
    val miniTimer: Boolean = false,
    val accent: Int = DEFAULT_ACCENT,
    val themeMode: ThemeMode = ThemeMode.System,
    val bgMode: BgMode = BgMode.Gradient,
    /** 自定义背景图在应用私有目录中的文件名 */
    val bgImage: String? = null,
    /** 背景压暗程度 0f..0.7f */
    val bgDim: Float = 0.35f,
) {
    fun minutesOf(mode: TimerMode): Int = when (mode) {
        TimerMode.Focus -> focusMinutes
        TimerMode.Short -> shortMinutes
        TimerMode.Long -> longMinutes
    }

    /** 修改某个模式时长后的新设置 */
    fun withMinutes(mode: TimerMode, minutes: Int): Settings = when (mode) {
        TimerMode.Focus -> copy(focusMinutes = minutes)
        TimerMode.Short -> copy(shortMinutes = minutes)
        TimerMode.Long -> copy(longMinutes = minutes)
    }
}

const val DEFAULT_ACCENT = 0xFF3482FF.toInt()

/** 强调色候选（与网页版一致） */
val ACCENT_COLORS: List<Pair<String, Int>> = listOf(
    "蓝" to 0xFF3482FF.toInt(),
    "绿" to 0xFF36D167.toInt(),
    "紫" to 0xFF7C4DFF.toInt(),
    "黄" to 0xFFFFB21D.toInt(),
    "橙" to 0xFFFF5722.toInt(),
    "粉" to 0xFFE91E63.toInt(),
    "青" to 0xFF00BCD4.toInt(),
)

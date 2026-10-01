package com.example.pomodoro

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

internal val MIUI_Work = Color(0xFFFF4D2A)
internal val MIUI_Break = Color(0xFF34C759)
internal val MIUI_Long = Color(0xFF007AFF)
internal val MIUI_Track = Color(0xFFE8E8E8)

internal val EaseInOutSine = Easing { f -> (-(cos(PI * f).toFloat() - 1f)) / 2f }
internal val EaseOutCubic = Easing { f -> 1f - (1f - f).pow(3) }

internal val Quotes = listOf(
    "专注当下，成就未来 🎯","每一分努力都不会被辜负 💪","坚持就是胜利 🏆","深呼吸，保持专注 🧘",
    "你的潜力无限 ✨","一步一步，终将到达 🚀","今天的付出，明天的收获 🌱","保持节奏，不急不躁 🎵",
    "相信自己，你可以的 💎","专注力是最好的超能力 ⚡","时间会证明一切 ⏳","小步快跑，持续进步 🏃",
    "把不可能变成可能 🔥","心流状态，忘我投入 🌊","质量比速度更重要 🎯","休息是为了走更远的路 🌿",
    "每个番茄都是一次成长 🍅","静下心来，万事可成 🏔️","你正在变得更好 🌈","享受过程，结果自来 🎭",
)
internal val BreakTips = listOf(
    "起来走动走动，活动一下身体 🚶","远眺窗外，让眼睛放松 👀","喝杯水，补充水分 💧",
    "做几个深呼吸，放松肩膀 🫁","伸展一下手臂和脖子 🤸","整理一下桌面 🧹",
    "听听轻音乐，放松心情 🎵","闭目养神一分钟 😌","微笑一下，你做得很棒 😊",
    "和同事聊两句，社交充电 ☕",
)

enum class Mode(val label: String) { Work("专注"), Short("短休息"), Long("长休息") }
internal val Mode.color: Color get() = when (this) { Mode.Work -> MIUI_Work; Mode.Short -> MIUI_Break; Mode.Long -> MIUI_Long }

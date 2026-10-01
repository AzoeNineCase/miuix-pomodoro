package com.example.pomodoro

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val state = remember { State() }
    val isDark = isSystemInDarkTheme()
    // 截图/调试用：POMODORO_PAGE=timer|stats|todos|settings，POMODORO_THEME=light|dark|aurora|system，POMODORO_ABOUT=1
    remember {
        System.getenv("POMODORO_PAGE")?.let { id ->
            Page.entries.firstOrNull { it.id == id }?.let { state.page = it }
        }
        System.getenv("POMODORO_THEME")?.let { state.theme = it }
        if (System.getenv("POMODORO_ABOUT") == "1") state.showAbout = true
        true
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = if (state.running || state.secondsLeft < state.totalSeconds) "${state.timeText} — 番茄钟" else "番茄钟",
        state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(1100.dp, 780.dp)),
    ) { App(state, isDark) }
}

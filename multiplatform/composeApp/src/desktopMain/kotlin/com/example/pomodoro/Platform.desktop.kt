package com.example.pomodoro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

actual fun platformPlaySound() {
    try { java.awt.Toolkit.getDefaultToolkit().beep() } catch (_: Exception) {}
}

actual fun startForegroundService(title: String, text: String) { /* Desktop: no-op */ }

actual fun stopForegroundService() { /* Desktop: no-op */ }

@Composable
actual fun KeepScreenOn(enabled: Boolean) { /* Desktop no-op */ }

@Composable
actual fun BlurBackdrop(show: Boolean) {
    if (!show) return
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color.Black.copy(alpha = 0.12f),
        Color.Black.copy(alpha = 0.32f),
        Color.Black.copy(alpha = 0.42f)
    ))))
}

/**桌面端使用无操作存储 */

package com.example.pomodoro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun platformPlaySound() {
    try { java.awt.Toolkit.getDefaultToolkit().beep() } catch (_: Exception) {}
}

actual suspend fun loadImageBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val bytes = if (url.startsWith("data:", ignoreCase = true)) decodeDataUrlBytes(url) else fetchBytes(url)
        bytes?.let { ImageIO.read(ByteArrayInputStream(it))?.toComposeImageBitmap() }
    } catch (_: Exception) {
        null
    }
}

/** 简单 GET（超时 10s/15s，重定向跟随 HttpURLConnection 默认行为） */
private fun fetchBytes(url: String): ByteArray? {
    val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        instanceFollowRedirects = true
    }
    return try {
        conn.inputStream.use { it.readBytes() }
    } finally {
        conn.disconnect()
    }
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

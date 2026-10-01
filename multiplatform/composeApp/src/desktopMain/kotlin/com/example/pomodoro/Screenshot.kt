package com.example.pomodoro

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.example.pomodoro.ui.AboutPage
import com.example.pomodoro.ui.StatsPage
import com.example.pomodoro.ui.SettingsPage
import com.example.pomodoro.ui.TimerPage
import com.example.pomodoro.ui.TodosPage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * 离屏渲染各页面并导出 PNG —— 用于与网页版截图做像素级比对，无需真实窗口。
 *
 * 运行：`gradlew :composeApp:screenshots`
 * 参数（可选）：`-PshotSizes=1280x800,393x852`、`-PshotDensity=2`
 */
fun main(args: Array<String>) {
    val outDir = File(args.getOrNull(0) ?: "build/screenshots").apply { mkdirs() }
    // 宽度/高度按**像素**给出（density 倍率下）：desktop 用 2560×1600@2 = 1280×800dp，
    // 手机用 786×1704@2 = 393×852dp
    val width = args.getOrNull(1)?.toIntOrNull() ?: 2560
    val height = args.getOrNull(2)?.toIntOrNull() ?: 1600
    val density = args.getOrNull(3)?.toFloatOrNull() ?: 2f

    val cases = listOf(
        Triple("desktop-timer-dark", Page.Timer, "dark"),
        Triple("desktop-timer-light", Page.Timer, "light"),
        Triple("desktop-timer-aurora", Page.Timer, "aurora"),
        Triple("desktop-stats-dark", Page.Stats, "dark"),
        Triple("desktop-todos-light", Page.Todos, "light"),
        Triple("desktop-settings-light", Page.Settings, "light"),
        Triple("desktop-settings-dark", Page.Settings, "dark"),
    )

    cases.forEach { (name, page, theme) ->
        val state = State(NoOpStorage)
        state.page = page
        state.theme = theme
        renderToFile(File(outDir, "$name.png"), width, height, density) { App(state, theme != "light", showSplash = false) }
        println("saved $name.png")
    }
}

private fun renderToFile(file: File, width: Int, height: Int, density: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
    val scene = ImageComposeScene(width, height, Density(density)) { content() }
    try {
        // 连续推进几帧，让入场动画（alpha/位移）落定后再导出
        var t = 0L
        repeat(30) {
            scene.render(t)
            t += 16_000_000L
        }
        val image = scene.render(t)
        val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("encode failed")
        file.writeBytes(data.bytes)
    } finally {
        scene.close()
    }
}

/** 供截图脚本使用：把某个页面单独渲染成整屏内容 */
@androidx.compose.runtime.Composable
fun PagePreview(state: State, content: @androidx.compose.runtime.Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) { content() }
    }
}

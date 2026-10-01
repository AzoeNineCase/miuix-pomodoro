package com.example.pomodoro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pomodoro.ui.AboutPage
import com.example.pomodoro.ui.IconBtn
import com.example.pomodoro.ui.MiniTimerOverlay
import com.example.pomodoro.ui.SettingsPage
import com.example.pomodoro.ui.SplashScreen
import com.example.pomodoro.ui.Symbol
import com.example.pomodoro.ui.StatsPage
import com.example.pomodoro.ui.TimerPage
import com.example.pomodoro.ui.ToastBar
import com.example.pomodoro.ui.TodosPage
import kotlinx.coroutines.delay

/** Miuix NavTransitions.MiuixDefault 的近似曲线（与网页 --nav-ease 同源） */
private val NavEase = CubicBezierEasing(0.4f, 1.2f, 0.95f, 0.97f)

@Composable
fun App(state: State, isDark: Boolean = isSystemInDarkTheme(), showSplash: Boolean = true) {
    val scope = rememberCoroutineScope()
    PomodoroTheme(state.theme, isDark, state.accent) {
        val c = AppTheme.colors
        KeepScreenOn(enabled = state.running)

        LaunchedEffect(state.pendingAutoStart) { state.consumeAutoStart(scope) }
        LaunchedEffect(state.toast) {
            if (state.toast != null) {
                delay(2200)
                state.toast = null
            }
        }

        // 启动页：进度条走满 → 淡出（对应网页 #loading 的两条进度条与 0.4s 透明度过渡）
        var splashVisible by remember { mutableStateOf(showSplash) }
        val splashProgress = remember { Animatable(if (showSplash) 0f else 1f) }
        val splashAlpha = remember { Animatable(if (showSplash) 1f else 0f) }
        LaunchedEffect(Unit) {
            if (!showSplash) return@LaunchedEffect
            splashProgress.animateTo(0.72f, tween(420, easing = LinearEasing))
            splashProgress.animateTo(1f, tween(420, easing = LinearEasing))
            delay(120)
            splashAlpha.animateTo(0f, tween(400, easing = NavEase))
            splashVisible = false
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
            val compact = maxWidth < 760.dp

            if (c.aurora) AuroraBackground()

            // ---- 关于页转场（500ms；底层左移 25% + 遮罩 0.5，与网页一致）----
            val nav = remember { Animatable(0f) }
            LaunchedEffect(state.showAbout) {
                nav.animateTo(if (state.showAbout) 1f else 0f, tween(500, easing = NavEase))
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -0.25f * size.width * nav.value },
            ) {
                if (compact) {
                    Column(Modifier.fillMaxSize()) {
                        MainColumn(state, Modifier.weight(1f))
                        RailBottom(state)
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        RailLeft(state)
                        MainColumn(state, Modifier.fillMaxSize())
                    }
                }
            }

            if (nav.value > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f * nav.value)))
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = (1f - nav.value) * size.width },
                ) {
                    AboutPage(state) { state.showAbout = false }
                }
            }

            state.toast?.let { toast ->
                Box(
                    Modifier.fillMaxSize().padding(bottom = 36.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) { ToastBar(toast.text, toast.icon) }
            }

            if (state.miniVisible) MiniTimerOverlay(state)

            if (splashVisible) {
                SplashScreen(
                    appProgress = splashProgress.value,
                    fontProgress = splashProgress.value,
                    alpha = splashAlpha.value,
                )
            }
        }
    }
}

/** 极光主题的渐变背景层（radial 叠加 + 135° 线性底色，与网页 .aurora-bg 一致） */
@Composable
private fun AuroraBackground() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color(0xFF080A1C), Color(0xFF14102E), Color(0xFF0B1030)),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(w, h),
            ),
        )
        fun glow(fx: Float, fy: Float, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(w * fx, h * fy),
                    radius = radius,
                ),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(w * fx, h * fy),
            )
        }
        glow(0.18f, 0.22f, w * 0.9f, Color(0x997C4DFF))
        glow(0.82f, 0.16f, w * 0.75f, Color(0x6B00BCD4))
        glow(0.72f, 0.82f, w * 0.85f, Color(0x803482FF))
        glow(0.26f, 0.84f, w * 0.72f, Color(0x5CEC4899))
    }
}

/** 左侧导航（≥760dp）：88px 宽，条目 64px，底部品牌图标 */
@Composable
private fun RailLeft(state: State) {
    val c = AppTheme.colors
    Column(
        Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(c.surface.copy(alpha = 0.58f))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Page.entries.forEach { p -> RailItem(p, state.page == p) { state.page = p } }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.primary),
            contentAlignment = Alignment.Center,
        ) { Symbol("eco", 28.dp, c.onPrimary) }
    }
}

/** 底部导航（窄屏）：条目等宽，隐藏品牌图标 */
@Composable
private fun RailBottom(state: State) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface.copy(alpha = 0.55f))
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Page.entries.forEach { p ->
            Box(Modifier.weight(1f)) { RailItem(p, state.page == p, Modifier.fillMaxWidth()) { state.page = p } }
        }
    }
}

@Composable
private fun RailItem(page: Page, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    Column(
        modifier
            .widthIn(max = 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) c.soft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.scale(if (active) 1.14f else 1f)) {
            Symbol(page.icon, 26.dp, if (active) c.primary else c.variant)
        }
        Spacer(Modifier.height(6.dp))
        Text(page.label, color = if (active) c.primary else c.variant, fontFamily = AppTheme.font, fontSize = 12.sp, fontWeight = W.semi)
    }
}

/** 主内容列：顶栏 + 页面（最大宽度 760dp 居中，与网页 .main 一致） */
@Composable
private fun MainColumn(state: State, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().widthIn(max = 760.dp)) {
            Topbar(state)
            val anim = remember { Animatable(1f) }
            LaunchedEffect(state.page) {
                anim.snapTo(0f)
                anim.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 380f))
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 48.dp)
                    .graphicsLayer {
                        alpha = anim.value
                        translationY = (1f - anim.value) * 10.dp.toPx()
                        val s = 0.99f + 0.01f * anim.value
                        scaleX = s; scaleY = s
                    },
            ) {
                when (state.page) {
                    Page.Timer -> TimerPage(state, compact = false)
                    Page.Stats -> StatsPage(state)
                    Page.Todos -> TodosPage(state)
                    Page.Settings -> SettingsPage(state)
                }
            }
        }
    }
}

/** 顶栏：标题/副标题 + 主题切换（padding 22/28/14，标题 26sp/800，底部分隔线） */
@Composable
private fun Topbar(state: State) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.background.copy(alpha = 0.52f))
                .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(state.page.title, color = c.onSurface, fontFamily = AppTheme.font, fontSize = 26.sp, fontWeight = W.extra, letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(state.page.subtitle, color = c.variant, fontFamily = AppTheme.font, fontSize = 13.sp)
            }
            IconBtn(if (c.dark) "light_mode" else "dark_mode") { state.cycleTheme(c.dark) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
    }
}

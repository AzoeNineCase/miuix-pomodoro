package com.example.pomodoro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import kotlin.math.floor

/** Miuix NavTransitions.MiuixDefault 的近似曲线（与网页 --nav-ease 同源） */
private val NavEase = CubicBezierEasing(0.4f, 1.2f, 0.95f, 0.97f)

@Composable
fun App(state: State, isDark: Boolean = isSystemInDarkTheme(), showSplash: Boolean = true) {
    val scope = rememberCoroutineScope()
    PomodoroTheme(state.theme, isDark, state.accent, bgActive = state.bgActive) {
        val c = AppTheme.colors
        KeepScreenOn(enabled = state.running)

        // 背景图：启动与 bgUrl/bgMode 变化时（重新）加载（对应网页 applyBackground）
        LaunchedEffect(state.bgMode, state.bgUrl) { state.refreshBackgroundImage() }

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
            // 自定义背景图（对应网页 #bgImage：盖在极光层之上、内容之下）
            state.bgImage?.let { BackgroundImageLayer(it) }

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

/**
 * 极光主题背景层：对应网页 `.aurora-bg > i` —— 180% 画布、四个椭圆径向光斑 + 135° 线性底，
 * 整体按 `auroraDrift`（26s、steps(130)）x/y 漂移；外层裁切出视口。
 * 光斑参数逐项对应 CSS：`radial-gradient(58% 58% at 18% 22%, rgba(124,77,255,.6), transparent 62%)` 等。
 */
@Composable
private fun AuroraBackground() {
    val inf = rememberInfiniteTransition(label = "aurora")
    val phase by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "drift",
    )
    // steps(130, end)：离散化后按 0%→50%→100% 的两段线性往返（与 @keyframes auroraDrift 等价）
    val stepped = floor(phase.coerceIn(0f, 0.9999f) * 130f) / 130f
    val s = if (stepped < 0.5f) stepped / 0.5f else (1f - stepped) / 0.5f
    val tx = -44.4444f * s / 100f
    val ty = (-8.8889f - 26.6667f * s) / 100f

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val w = maxWidth
        val h = maxHeight
        val lw = w * 1.8f
        val lh = h * 1.8f
        // 整层是一个 180% 画布的 Canvas（requiredSize 不受父约束压缩），漂移直接作用在画布上
        Canvas(
            Modifier
                .offset(x = lw * tx, y = lh * ty)
                .requiredSize(lw, lh),
        ) {
            drawRect(
                Brush.linearGradient(
                    0f to Color(0xFF080A1C),
                    0.52f to Color(0xFF14102E),
                    1f to Color(0xFF0B1030),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            // 椭圆光斑：在 (cx,cy) 处以 rx 为半径画圆，再纵向压扁到 ry（pivot 在圆心）
            fun blob(fx: Float, fy: Float, r: Float, color: Color) {
                val cx = size.width * fx
                val cy = size.height * fy
                val rx = size.width * r
                val ry = size.height * r
                withTransform({ scale(1f, ry / rx, pivot = Offset(cx, cy)) }) {
                    drawCircle(
                        // 停靠点用同色 alpha=0（与 CSS 预乘插值等价）
                        Brush.radialGradient(0f to color, 0.62f to color.copy(alpha = 0f), center = Offset(cx, cy), radius = rx),
                        radius = rx,
                        center = Offset(cx, cy),
                    )
                }
            }
            blob(0.18f, 0.22f, 0.58f, Color(0x997C4DFF))   // rgba(124,77,255,.6)
            blob(0.82f, 0.16f, 0.48f, Color(0x6B00BCD4))   // rgba(0,188,212,.42)
            blob(0.72f, 0.82f, 0.52f, Color(0x803482FF))   // rgba(52,130,255,.5)
            blob(0.26f, 0.84f, 0.46f, Color(0x5CEC4899))   // rgba(236,72,153,.36)
        }
    }
}

/**
 * 自定义背景图层（对应网页 .bg-image 与 ::after）：
 * cover 铺满 + 0.6s 淡入 + 径向暗角叠加，保证前景文字对比度。
 */
@Composable
private fun BackgroundImageLayer(image: ImageBitmap) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(image) {
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(600, easing = NavEase))
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // 径向暗角：radial-gradient(130% 130% at 50% 38%, rgba(8,10,24,.1), rgba(8,10,24,.32))
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val brush = Brush.radialGradient(
                    0f to Color(0x1A080A18),
                    1f to Color(0x52080A18),
                    center = Offset(size.width * 0.5f, size.height * 0.38f),
                    radius = size.height * 1.3f,
                )
                onDrawBehind { drawRect(brush) }
            },
        )
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
            // color-mix(surface 58%) = alpha×0.58（极光下 surface 自身是 7% 白）
            .background(c.surface.copy(alpha = c.surface.alpha * 0.58f))
            .drawBehind {
                val sw = 1.dp.toPx()
                drawRect(c.divider, topLeft = Offset(size.width - sw, 0f), size = Size(sw, size.height))
            }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {        Page.entries.forEach { p -> RailItem(p, state.page == p, Modifier.width(64.dp)) { state.page = p } }
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
            .background(c.surface.copy(alpha = c.surface.alpha * 0.55f))
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
        Text(page.label, color = if (active) c.primary else c.variant, fontFamily = AppTheme.font, fontSize = 12.sp, lineHeight = normalLine(12), fontWeight = W.semi)
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
                Text(state.page.title, color = c.onSurface, fontFamily = AppTheme.font, fontSize = 26.sp, lineHeight = normalLine(26), fontWeight = W.extra, letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(state.page.subtitle, color = c.variant, fontFamily = AppTheme.font, fontSize = 13.sp, lineHeight = normalLine(13))
            }
            IconBtn(if (c.dark) "light_mode" else "dark_mode") { state.cycleTheme(c.dark) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
    }
}

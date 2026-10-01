package com.example.pomodoro

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

private data class ParticleData(val vx: Float, val vy: Float, val color: Color, val size: Float)
private data class AmbientDot(val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float)

private class DebounceState {
    private var lastClickTime = 0L
    private val cooldownMs = 300L
    fun canClick(): Boolean {
        val now = nowMillis()
        if (now - lastClickTime >= cooldownMs) { lastClickTime = now; return true }
        return false
    }
}

@Composable
fun App(state: State, isDark: Boolean = isSystemInDarkTheme()) {
    val scope = rememberCoroutineScope()
    val debounce = remember { DebounceState() }
    val controller = remember(isDark) { ThemeController(if (isDark) ColorSchemeMode.Dark else ColorSchemeMode.Light) }

    MiuixTheme(controller = controller) {
        KeepScreenOn(enabled = state.running && state.mode == Mode.Work)
        val colors = MiuixTheme.colorScheme
        val inf = rememberInfiniteTransition(label = "r")
        val breathScale by inf.animateFloat(1f, 1.025f, infiniteRepeatable(tween(2800, easing = EaseInOutSine), RepeatMode.Reverse), label = "bs")
        val glowAlpha by inf.animateFloat(0.12f, 0.40f, infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse), label = "ga")
        val pulseScale by inf.animateFloat(1f, 1.07f, infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse), label = "ps")
        val iconSway by inf.animateFloat(-3f, 3f, infiniteRepeatable(tween(2800, easing = EaseInOutSine), RepeatMode.Reverse), label = "is")
        val dotRotate by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart), label = "dr")

        LaunchedEffect(state.completionPulse) { if (state.completionPulse > 0f) { delay(1500); state.dismissCompletionPulse() } }
        val cpAlpha by animateFloatAsState(if (state.completionPulse > 0f) 0.6f else 0f, tween(300))
        val cpScale by animateFloatAsState(if (state.completionPulse > 0f) 1.3f else 1f, tween(1200, easing = EaseOutCubic))
        var showP by remember { mutableStateOf(false) }
        LaunchedEffect(state.finished) { if (state.finished) { showP = true; delay(1400); showP = false } }

        val bgTop by animateColorAsState(if (isDark) when (state.mode) {
            Mode.Work -> Color(0xFF2A1513); Mode.Short -> Color(0xFF132A18); Mode.Long -> Color(0xFF131E2A)
        } else when (state.mode) {
            Mode.Work -> Color(0xFFFFF5F3); Mode.Short -> Color(0xFFF0FFF4); Mode.Long -> Color(0xFFF0F5FF)
        }, tween(700))
        val bgBot by animateColorAsState(if (isDark) Color(0xFF121212) else when (state.mode) {
            Mode.Work -> Color(0xFFFFECEA); Mode.Short -> Color(0xFFE6F9EC); Mode.Long -> Color(0xFFE6EEFF)
        }, tween(700))
        val focusAlpha by animateFloatAsState(if (state.focusMode && state.running) 0f else 1f, tween(600))

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgTop, bgBot))).onPreviewKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown && !state.showCfg && !state.showStats) when (ev.key) {
                Key.Spacebar -> { if (state.running) state.pause() else state.startJob(scope); true }
                Key.R -> { state.reset(); true }
                Key.S -> { state.skip(); true }
                Key.F -> { state.focusMode = !state.focusMode; true }
                else -> false
            } else false
        }) {
            AmbientParticles(state.color, state.running)
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).systemBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                TopProgressBar(state.progress, state.color, state.sessions, state.dailyGoal)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.alpha(focusAlpha)) { Header(iconSway, state) }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.alpha(focusAlpha)) { ModeTabs(state.mode) { if (debounce.canClick()) state.switchMode(it) } }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (state.completionPulse > 0f) Box(Modifier.size(270.dp).graphicsLayer { scaleX = cpScale; scaleY = cpScale; alpha = cpAlpha })
                    TimerRing(state, breathScale, glowAlpha, dotRotate)
                    if (showP) ParticleBurst(state.color)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.alpha(focusAlpha)) { Controls(state.running, pulseScale, { if (debounce.canClick()) state.pause() }, { if (debounce.canClick()) state.startJob(scope) }, { if (debounce.canClick()) state.reset() }, { if (debounce.canClick()) state.skip() }, state.color) }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.alpha(focusAlpha)) { Dots(state.sessions % 4, state.sessionLabel, state.untilLong, state.color) }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.alpha(focusAlpha)) { TaskBar(state) }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.alpha(focusAlpha * 0.7f)) { Text("空格 开始/暂停 · R 重置 · S 跳过 · F 专注", fontSize = 9.sp, color = colors.onSurface.copy(0.16f)) }
            }
            BlurBackdrop(show = state.showCfg || state.showStats)
            AnimatedVisibility(state.showCfg, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) { SettingsSheet(state) }
            AnimatedVisibility(state.showStats, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) { StatsSheet(state) }
        }
    }
}

@Composable
private fun AmbientParticles(color: Color, running: Boolean) {
    val dots = remember { List(18) { AmbientDot(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.3f + 0.1f, Random.nextFloat() * 4f + 2f, Random.nextFloat() * 360f) } }
    val inf = rememberInfiniteTransition(label = "amb")
    val tick by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart), label = "at")
    val dotAlpha = remember { derivedStateOf { if (running) 0.15f else 0.06f } }
    Canvas(Modifier.fillMaxSize().alpha(dotAlpha.value)) {
        dots.forEach { d ->
            val phase = d.phase + tick * 360f; val rad = phase * PI / 180.0
            val px = d.x * size.width + sin(rad).toFloat() * 30f
            val py = d.y * size.height - tick * d.speed * size.height % size.height
            val y = if (py < -20f) size.height + 20f else py
            drawCircle(color.copy(0.4f), d.size.dp.toPx(), Offset(px, y))
        }
    }
}

@Composable
private fun TopProgressBar(progress: Float, color: Color, done: Int, goal: Int) {
    val colors = MiuixTheme.colorScheme
    val ap by animateFloatAsState(progress, tween(300))
    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MIUI_Track.copy(0.3f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(ap).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(color.copy(0.7f), color))))
    }
    if (done > 0) Row(Modifier.fillMaxWidth().padding(top = 2.dp), Arrangement.End, Alignment.CenterVertically) {
        val gp = (done.toFloat() / goal).coerceIn(0f, 1f)
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (gp >= 1f) MIUI_Break else color.copy(0.3f)))
        Spacer(Modifier.width(3.dp))
        Text("$done/$goal", fontSize = 8.sp, color = colors.onSurface.copy(0.25f))
    }
}

@Composable
private fun Header(sway: Float, state: State) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).graphicsLayer { rotationZ = sway }, contentAlignment = Alignment.Center) {
                Icon(imageVector = MiuixIcons.Timer, contentDescription = null, tint = MIUI_Work, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("番茄钟", fontSize = 18.sp, color = colors.onBackground)
                if (state.taskName.isNotBlank()) Text(state.taskName, fontSize = 10.sp, color = colors.onSurface.copy(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = { state.focusMode = !state.focusMode }, modifier = Modifier.size(34.dp)) {
                Icon(imageVector = MiuixIcons.Favorites, contentDescription = null, tint = if (state.focusMode) MIUI_Work else colors.onSurface.copy(0.40f), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = { state.showStats = true }, modifier = Modifier.size(34.dp)) {
                Icon(imageVector = MiuixIcons.Info, contentDescription = null, tint = colors.onSurface.copy(0.55f), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = { state.showCfg = true }, modifier = Modifier.size(34.dp)) {
                Icon(imageVector = MiuixIcons.Settings, contentDescription = null, tint = colors.onSurface.copy(0.55f), modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun ModeTabs(current: Mode, onSelect: (Mode) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val tgt by animateColorAsState(current.color, tween(500))
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Mode.entries.forEach { m ->
                val sel = m == current
                val bg by animateColorAsState(if (sel) tgt.copy(0.12f) else Color.Transparent, tween(350))
                val tc by animateColorAsState(if (sel) tgt else colors.onSurface.copy(0.45f), tween(350))
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(13.dp)).background(bg).clickable { onSelect(m) }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val rot by animateFloatAsState(if (sel) 360f else 0f, tween(500))
                        Box(Modifier.size(15.dp).graphicsLayer { rotationZ = rot }) {
                            Icon(when (m) { Mode.Work -> MiuixIcons.Play; Mode.Short -> MiuixIcons.Music; Mode.Long -> MiuixIcons.Timer }, null, tint = tc, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(m.label, fontSize = 12.sp, color = tc)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerRing(state: State, breath: Float, glow: Float, dotRot: Float) {
    val colors = MiuixTheme.colorScheme
    val sz = 244.dp
    Box(Modifier.size(sz).graphicsLayer { scaleX = breath; scaleY = breath }, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = glow * 0.45f }.clip(CircleShape).background(
            Brush.radialGradient(listOf(state.color.copy(0.25f), state.color.copy(0.08f), Color.Transparent), radius = sz.value * 0.55f)
        ))
        Canvas(Modifier.matchParentSize().alpha(0.18f)) {
            val dr = 2.5.dp.toPx(); val orbit = minOf(size.width, size.height) / 2 - 16.dp.toPx()
            rotate(dotRot) { for (i in 0 until 12) { val a = (i * 30.0) * PI / 180.0; drawCircle(Color.Gray.copy(0.5f), dr, Offset(center.x + orbit * cos(a).toFloat(), center.y + orbit * sin(a).toFloat())) } }
        }
        Canvas(Modifier.matchParentSize()) {
            val sw = 10.dp.toPx(); val arcSz = Size(size.width - sw, size.height - sw); val tl = Offset(sw / 2, sw / 2); val tickR = size.width / 2
            for (i in 0 until 60) {
                val a = (i * 6 - 90) * PI / 180.0; val inner = tickR - sw - 3.dp.toPx(); val outer = tickR - sw - 0.5.dp.toPx()
                drawLine(Color.Gray.copy(if (i % 5 == 0) 0.3f else 0.1f), Offset(tickR + inner * cos(a).toFloat(), tickR + inner * sin(a).toFloat()), Offset(tickR + outer * cos(a).toFloat(), tickR + outer * sin(a).toFloat()), strokeWidth = if (i % 5 == 0) 2.dp.toPx() else 1.dp.toPx())
            }
            drawArc(MIUI_Track, -90f, 360f, false, tl, arcSz, style = Stroke(sw, cap = StrokeCap.Round))
            if (state.progress > 0f) drawArc(brush = Brush.sweepGradient(listOf(state.color.copy(0.6f), state.color, state.color.copy(0.9f))), startAngle = -90f, sweepAngle = 360f * state.progress, useCenter = false, topLeft = tl, size = arcSz, style = Stroke(sw, cap = StrokeCap.Round))
            else drawArc(state.color.copy(0.10f), -90f, 360f, false, tl, arcSz, style = Stroke(sw, cap = StrokeCap.Round))
            if (state.progress > 0f) {
                val a = (-90.0 + 360.0 * state.progress) * PI / 180.0; val r = (size.width - sw) / 2; val cx = size.width / 2; val cy = size.height / 2
                val dx = cx + r * cos(a).toFloat(); val dy = cy + r * sin(a).toFloat()
                drawCircle(Color.White, sw * 0.9f, Offset(dx, dy)); drawCircle(state.color, sw * 0.5f, Offset(dx, dy))
            }
        }
        Box(Modifier.size(200.dp).clip(CircleShape).background(colors.surface.copy(0.97f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val tAlpha by animateFloatAsState(if (state.running) 1f else 0.72f, tween(500))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(pad2(state.mins), fontSize = 52.sp, color = colors.onBackground.copy(tAlpha), letterSpacing = 1.sp)
                    Text(":", fontSize = 38.sp, color = colors.onBackground.copy(0.35f), modifier = Modifier.padding(bottom = 4.dp))
                    Text(pad2(state.secs), fontSize = 52.sp, color = colors.onBackground.copy(tAlpha * 0.88f), letterSpacing = 1.sp)
                }
                if (state.elapsed > 0) Text("已用 ${pad2(state.eMins)}:${pad2(state.eSecs)}", fontSize = 10.sp, color = colors.onSurface.copy(0.30f))
                Spacer(Modifier.height(3.dp))
                val chipBg by animateColorAsState(state.color.copy(0.08f)); val chipFg by animateColorAsState(state.color)
                Surface(shape = RoundedCornerShape(20.dp), color = chipBg, modifier = Modifier.height(23.dp)) {
                    val t = when { state.finished -> "🎉 完成！"; state.running -> "⏱ ${state.mode.label}中..."; else -> "准备开始" }
                    Text(t, fontSize = 10.sp, color = chipFg, modifier = Modifier.padding(horizontal = 11.dp, vertical = 2.dp))
                }
                if (state.mode == Mode.Work && state.running) {
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(state.quote, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }, label = "q") { q ->
                        Text(q, fontSize = 9.sp, color = chipFg.copy(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp), textAlign = TextAlign.Center)
                    }
                }
                if (state.mode != Mode.Work && state.running) {
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(state.breakTip, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }, label = "bt") { t ->
                        Text(t, fontSize = 9.sp, color = MIUI_Break.copy(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticleBurst(color: Color) {
    val particles = remember { List(36) { val angle = Random.nextFloat() * 2 * PI.toFloat(); val speed = Random.nextFloat() * 3f + 1.5f; ParticleData(cos(angle) * speed, sin(angle) * speed, listOf(color, Color.White, Color(0xFFFFD700), color.copy(0.4f)).random(), Random.nextFloat() * 7f + 3f) } }
    val prog = remember { Animatable(0f) }; LaunchedEffect(Unit) { prog.animateTo(1f, tween(1300, easing = EaseOutCubic)) }
    Canvas(Modifier.fillMaxSize().alpha(1f - prog.value)) {
        val cx = size.width / 2; val cy = size.height / 2
        particles.forEach { p ->
            val t = prog.value; val px = cx + p.vx * t * 90f; val py = cy + p.vy * t * 90f + t * t * 160f
            drawCircle(p.color.copy(0.25f), p.size.dp.toPx() * 0.5f, Offset(cx + p.vx * t * 0.85f * 90f, cy + p.vy * t * 0.85f * 90f + (t * 0.85f) * (t * 0.85f) * 160f))
            drawCircle(p.color, p.size.dp.toPx(), Offset(px, py))
        }
    }
}

@Composable
private fun Controls(running: Boolean, pulse: Float, onPause: () -> Unit, onStart: () -> Unit, onReset: () -> Unit, onSkip: () -> Unit, color: Color) {
    val colors = MiuixTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onReset, modifier = Modifier.size(42.dp)) {
            Icon(imageVector = MiuixIcons.Reset, contentDescription = null, tint = colors.onSurface.copy(0.50f), modifier = Modifier.size(20.dp))
        }
        val mis = remember { MutableInteractionSource() }; val mp by mis.collectIsPressedAsState(); val ms by animateFloatAsState(if (mp) 0.88f else 1f, spring(0.6f, 350f)); val fs = if (running) ms * pulse else ms
        Box(Modifier.size(64.dp).graphicsLayer { scaleX = fs; scaleY = fs }.clip(CircleShape).background(Brush.radialGradient(listOf(color, color.copy(0.75f)))).clickable { if (running) onPause() else onStart() }, contentAlignment = Alignment.Center) {
            Icon(if (running) MiuixIcons.Pause else MiuixIcons.Play, if (running) "\u6682\u505C" else "\u5F00\u59CB", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onSkip, modifier = Modifier.size(42.dp)) {
            Icon(imageVector = MiuixIcons.Forward, contentDescription = null, tint = colors.onSurface.copy(0.50f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Dots(done: Int, label: String, untilLong: Int, color: Color) {
    val colors = MiuixTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { i ->
                val ok = i < done; val s by animateFloatAsState(if (ok) 1.3f else 1f, spring(0.55f, 250f))
                Box(Modifier.size(9.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(if (ok) color else colors.surfaceVariant))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, fontSize = 10.sp, color = colors.onSurface.copy(0.38f))
            Text("·", fontSize = 10.sp, color = colors.onSurface.copy(0.20f))
            Text("距长休息 $untilLong", fontSize = 10.sp, color = color.copy(0.45f))
        }
    }
}

@Composable
private fun TaskBar(state: State) {
    val colors = MiuixTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().height(42.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = MiuixIcons.Edit, null, tint = colors.onSurface.copy(0.28f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            TextField(value = state.taskName, onValueChange = { state.setTask(it) }, modifier = Modifier.weight(1f).height(42.dp), label = "输入当前任务…")
            if (state.taskName.isNotBlank()) IconButton(onClick = { state.setTask("") }, modifier = Modifier.size(16.dp)) {
                Icon(MiuixIcons.Close, null, modifier = Modifier.size(12.dp), tint = colors.onSurface.copy(0.22f))
            }
        }
    }
}

@Composable
private fun BottomSheet(show: Boolean, onDismiss: () -> Unit, heightFraction: Float = 0.72f, content: @Composable ColumnScope.() -> Unit) {
    if (!show) return
    val colors = MiuixTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.30f)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Surface(shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), color = colors.surface, modifier = Modifier.fillMaxWidth().fillMaxHeight(heightFraction).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), content = content)
        }
    }
}

@Composable
private fun SettingsSheet(state: State) {
    val colors = MiuixTheme.colorScheme
    LaunchedEffect(state.showCfg) { if (state.showCfg) { state.sliderWork = state.cfgWork.toFloat(); state.sliderShort = state.cfgShort.toFloat(); state.sliderLong = state.cfgLong.toFloat(); state.sliderGoal = state.dailyGoal.toFloat() } }
    BottomSheet(show = state.showCfg, onDismiss = { state.showCfg = false }) {
        Handle(); Text("设置", fontSize = 18.sp); Spacer(Modifier.height(12.dp))
        Column {
            SettingSlider("专注时长", "${state.cfgWork} 分钟", state.sliderWork / 60f, 1f/60f, 1f, onDrag = { state.sliderWork = (it * 60f).coerceIn(1f, 60f) }, onCommit = { state.cfgWork = state.sliderWork.toInt(); state.saveSettings() })
            SettingSlider("短休息", "${state.cfgShort} 分钟", state.sliderShort / 30f, 1f/30f, 1f, onDrag = { state.sliderShort = (it * 30f).coerceIn(1f, 30f) }, onCommit = { state.cfgShort = state.sliderShort.toInt(); state.saveSettings() })
            SettingSlider("长休息", "${state.cfgLong} 分钟", state.sliderLong / 60f, 1f/60f, 1f, onDrag = { state.sliderLong = (it * 60f).coerceIn(1f, 60f) }, onCommit = { state.cfgLong = state.sliderLong.toInt(); state.saveSettings() })
            SettingSlider("每日目标", "${state.dailyGoal} 个番茄", state.sliderGoal / 16f, 1f/16f, 1f, onDrag = { state.sliderGoal = (it * 16f).coerceIn(1f, 16f) }, onCommit = { state.dailyGoal = state.sliderGoal.toInt(); state.saveSettings() })
            Spacer(Modifier.height(8.dp))
            SettingToggle("自动开始下一个", state.autoStart) { state.autoStart = it; state.saveSettings() }
            SettingToggle("完成提示音", state.soundOn) { state.soundOn = it; state.saveSettings() }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                state.cfgWork = 25; state.cfgShort = 5; state.cfgLong = 15; state.dailyGoal = 8; state.autoStart = true; state.soundOn = true
                state.sliderWork = 25f; state.sliderShort = 5f; state.sliderLong = 15f; state.sliderGoal = 8f
                state.saveSettings()
            }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("恢复默认设置", fontSize = 12.sp, color = colors.onSurface.copy(0.50f))
            }
        }
    }
}

@Composable
private fun SettingSlider(label: String, valueText: String, fraction: Float, startFraction: Float = 0f, endFraction: Float = 1f, onDrag: (Float) -> Unit, onCommit: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = colors.onSurface)
            Text(valueText, fontSize = 13.sp, color = colors.primary)
        }
        Slider(value = fraction, onValueChange = onDrag, onValueChangeFinished = onCommit, modifier = Modifier.fillMaxWidth().height(30.dp), valueRange = startFraction..endFraction)
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = colors.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatsSheet(state: State) {
    val colors = MiuixTheme.colorScheme
    val totalMin by remember { derivedStateOf { state.history.sumOf { it.minutes } } }
    val todayStart = remember { todayStartMillis() }
    val todayCount by remember { derivedStateOf { state.history.count { it.ts >= todayStart } } }
    var showClearDialog by remember { mutableStateOf(false) }
    BottomSheet(show = state.showStats, onDismiss = { state.showStats = false }, heightFraction = 0.68f) {
        Handle(); Text("统计", fontSize = 18.sp); Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("完成番茄", "${state.sessions}", MIUI_Work, Modifier.weight(1f))
            StatCard("专注分钟", "$totalMin", MIUI_Long, Modifier.weight(1f))
            StatCard("今日记录", "$todayCount", MIUI_Break, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp)); Text("最近记录", fontSize = 13.sp); Spacer(Modifier.height(5.dp))
        if (state.history.isEmpty()) Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(MiuixIcons.Info, null, tint = colors.onSurface.copy(0.15f), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("暂无记录", fontSize = 12.sp, color = colors.onSurface.copy(0.22f))
                Text("完成一次番茄后会自动记录", fontSize = 10.sp, color = colors.onSurface.copy(0.15f))
            }
        }
        else {
            state.history.takeLast(20).reversed().forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(e.mode.color)); Spacer(Modifier.width(8.dp)); Text(e.mode.label, fontSize = 12.sp, color = colors.onSurface) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("${e.minutes}分钟", fontSize = 11.sp, color = colors.onSurface.copy(0.6f)); Text(e.timeStr, fontSize = 10.sp, color = colors.onSurface.copy(0.25f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showClearDialog = true }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("清除所有记录", fontSize = 12.sp, color = MIUI_Work.copy(0.7f))
            }
        }
    }
    if (showClearDialog) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.40f)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showClearDialog = false }, contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, modifier = Modifier.fillMaxWidth(0.8f).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("确认清除", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("清除所有记录和会话数？此操作不可撤销。", fontSize = 13.sp, color = colors.onSurface.copy(0.6f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).clickable { showClearDialog = false }, contentAlignment = Alignment.Center) { Text("取消", fontSize = 14.sp, color = colors.onSurface) }
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(MIUI_Work).clickable { state.history.clear(); state.sessions = 0; state.saveSettings(); showClearDialog = false }, contentAlignment = Alignment.Center) { Text("确认", fontSize = 14.sp, color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = color.copy(0.07f)) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, color = color)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = color.copy(0.65f))
        }
    }
}

@Composable
private fun Handle() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(32.dp, 3.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.Gray.copy(0.20f))) }
}

package com.miuix.pomodoro.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.miuix.pomodoro.PomodoroViewModel
import com.miuix.pomodoro.data.BgMode
import com.miuix.pomodoro.data.ThemeMode
import com.miuix.pomodoro.ui.screens.SettingsScreen
import com.miuix.pomodoro.ui.screens.StatsScreen
import com.miuix.pomodoro.ui.screens.TimerScreen
import com.miuix.pomodoro.ui.screens.TodosScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import kotlin.math.roundToInt

private data class NavEntry(val index: Int, val label: String, val icon: ImageVector)

@Composable
private fun navEntries(): List<NavEntry> = listOf(
    NavEntry(PomodoroViewModel.PAGE_TIMER, "计时", MiuixIcons.Timer),
    NavEntry(PomodoroViewModel.PAGE_STATS, "统计", ChartIcon),
    NavEntry(PomodoroViewModel.PAGE_TODOS, "待办", MiuixIcons.Tasks),
    NavEntry(PomodoroViewModel.PAGE_SETTINGS, "设置", MiuixIcons.Settings),
)

private val PAGE_META = listOf(
    "番茄钟" to "专注每一刻",
    "统计" to "你的专注足迹",
    "待办" to "记录你的任务",
    "设置" to "自定义你的节奏",
)

/** 应用外壳：Miuix Scaffold + 顶栏 + 底部导航（宽屏自动切换为侧边导航栏） */
@Composable
fun AppRoot(vm: PomodoroViewModel) {
    val hasBackground = vm.settings.bgMode == BgMode.Custom && vm.settings.bgImage != null
    // 冷启动加载页：只在进程首次进入时播放
    var loading by rememberSaveable { mutableStateOf(true) }

    AppTheme(settings = vm.settings, translucentSurface = hasBackground) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            val entries = navEntries()
            val meta = PAGE_META[vm.currentPage.coerceIn(0, PAGE_META.lastIndex)]
            val isDark = when (vm.settings.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }
            // 页面切换后保留各页的滚动位置
            val saveableStateHolder = rememberSaveableStateHolder()
            // 主题切换时的径向光晕（对应网页版的 .theme-flash）
            var flashTrigger by remember { mutableIntStateOf(0) }
            var lastTheme by remember { mutableStateOf(vm.settings.themeMode) }
            LaunchedEffect(vm.settings.themeMode) {
                if (vm.settings.themeMode != lastTheme) {
                    lastTheme = vm.settings.themeMode
                    flashTrigger++
                }
            }

            AppBackground(vm)

            Scaffold(
                containerColor = if (hasBackground) Color.Transparent else MiuixTheme.colorScheme.background,
                topBar = {
                    SmallTopAppBar(
                        title = meta.first,
                        subtitle = meta.second,
                        actions = {
                            IconButton(onClick = vm::cycleTheme) {
                                Icon(
                                    imageVector = if (isDark) SunIcon else MoonIcon,
                                    contentDescription = "切换主题",
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!wide) {
                        NavigationBar {
                            entries.forEach { entry ->
                                NavigationBarItem(
                                    selected = vm.currentPage == entry.index,
                                    onClick = { vm.setPage(entry.index) },
                                    icon = entry.icon,
                                    label = entry.label,
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    if (wide) {
                        NavigationRail {
                            entries.forEach { entry ->
                                NavigationRailItem(
                                    selected = vm.currentPage == entry.index,
                                    onClick = { vm.setPage(entry.index) },
                                    icon = entry.icon,
                                    label = entry.label,
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        // 页面切换动画：淡入 + 轻微上滑（对应网页版的 pageIn）
                        AnimatedContent(
                            targetState = vm.currentPage,
                            transitionSpec = {
                                (
                                    fadeIn(tween(durationMillis = 260)) +
                                        slideInVertically(tween(durationMillis = 340)) { it / 14 }
                                    ).togetherWith(fadeOut(tween(durationMillis = 160)))
                            },
                            label = "page",
                        ) { page ->
                            saveableStateHolder.SaveableStateProvider(page) {
                                when (page) {
                                    PomodoroViewModel.PAGE_STATS -> StatsScreen(vm)
                                    PomodoroViewModel.PAGE_TODOS -> TodosScreen(vm)
                                    PomodoroViewModel.PAGE_SETTINGS -> SettingsScreen(vm)
                                    else -> TimerScreen(vm)
                                }
                            }
                        }
                    }
                }
            }

            ToastOverlay(
                message = vm.toastMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (wide) 28.dp else 96.dp),
            )

            if (vm.settings.miniTimer) {
                var dragOffset by remember { mutableStateOf(Offset.Zero) }
                val endPad = 16.dp
                val bottomPad = if (wide) 24.dp else 92.dp
                val parentW = constraints.maxWidth
                val parentH = constraints.maxHeight
                MiniTimer(
                    vm = vm,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = endPad, bottom = bottomPad)
                        .offset {
                            IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                        }
                        .pointerInput(wide, parentW, parentH) {
                            detectDragGestures { change, delta ->
                                change.consume()
                                // 把偏移夹在可视范围内：拖出屏幕就再也拖不回来了
                                val endPadPx = endPad.toPx()
                                val bottomPadPx = bottomPad.toPx()
                                val minX = endPadPx + size.width - parentW
                                val maxX = endPadPx
                                val minY = bottomPadPx + size.height - parentH
                                val maxY = bottomPadPx
                                val nx = dragOffset.x + delta.x
                                val ny = dragOffset.y + delta.y
                                dragOffset = Offset(
                                    if (minX <= maxX) nx.coerceIn(minX, maxX) else maxX,
                                    if (minY <= maxY) ny.coerceIn(minY, maxY) else maxY,
                                )
                            }
                        },
                )
            }

            ThemeFlash(trigger = flashTrigger, color = Color(vm.settings.accent))

            if (loading) {
                LoadingOverlay(onFinished = { loading = false })
            }
        }
    }
}

/** 主题切换时的径向光晕（对应网页版的 .theme-flash + themeWash） */
@Composable
private fun ThemeFlash(trigger: Int, color: Color) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 750))
    }
    val p = progress.value
    if (p <= 0f || p >= 1f) return
    // 45% 处最亮，随后淡出
    val alpha = if (p < 0.45f) p / 0.45f * 0.55f else (1f - p) / 0.55f * 0.55f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(0.5f + 0.9f * p)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha * 0.75f), Color.Transparent),
                ),
            ),
    )
}

/** 自定义背景图 + 压暗层（对应网页版的 .bg-image） */
@Composable
private fun AppBackground(vm: PomodoroViewModel) {
    val settings = vm.settings
    if (settings.bgMode != BgMode.Custom) return
    val file = remember(settings.bgImage) { vm.backgroundFile() } ?: return
    // 解码放到 IO 线程并降采样：大图在组合期做全尺寸解码会卡帧甚至 OOM
    val bitmap by produceState<Bitmap?>(initialValue = null, settings.bgImage) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeDownsampled(file, 2048) }.getOrNull()
        }
    }
    val bmp = bitmap ?: return

    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = settings.bgDim)),
    )
}

/** 按宽度目标做 2 的幂次降采样解码，避免整张全尺寸位图常驻内存 */
private fun decodeDownsampled(file: File, maxWidth: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}

/** 底部居中的提示气泡（对应网页版的 toast） */
@Composable
private fun ToastOverlay(message: String?, modifier: Modifier = Modifier) {
    var lastMessage by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message != null) lastMessage = message
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { it / 3 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.onBackground)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = lastMessage,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.background,
            )
        }
    }
}

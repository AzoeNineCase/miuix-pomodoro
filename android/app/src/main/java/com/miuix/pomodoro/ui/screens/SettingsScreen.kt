package com.miuix.pomodoro.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miuix.pomodoro.PomodoroViewModel
import com.miuix.pomodoro.data.BgMode
import com.miuix.pomodoro.data.ThemeMode
import com.miuix.pomodoro.data.TimerMode
import com.miuix.pomodoro.ui.PictureInPictureIcon
import com.miuix.pomodoro.ui.components.AccentSwatches
import com.miuix.pomodoro.ui.components.SectionCard
import com.miuix.pomodoro.ui.components.SettingIcon
import com.miuix.pomodoro.ui.components.Stepper
import com.miuix.pomodoro.util.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.icon.extended.VolumeUp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页：时长 / 行为 / 主题 / 背景 */
@Composable
fun SettingsScreen(vm: PomodoroViewModel) {
    val settings = vm.settings
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 读图放到 IO 线程：相册大图在主线程 readBytes 会卡住甚至 ANR
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) vm.showToast("读取图片失败") else vm.importBackground(bytes)
        }
    }

    val requestNotify = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        vm.setNotify(granted)
        vm.showToast(if (granted) "桌面通知已开启" else "未授权通知，请在系统设置中开启")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        /* ------------------------------ 时长 ------------------------------ */
        SectionCard(title = "设置", highlight = "时长") {
            DurationRow(
                icon = MiuixIcons.Stopwatch,
                title = "专注时长",
                summary = "每次深度工作",
                value = settings.focusMinutes,
                min = 1,
                max = 90,
                onChange = { vm.setMinutes(TimerMode.Focus, it) },
            )
            DurationRow(
                icon = MiuixIcons.Alarm,
                title = "短休息",
                summary = "小憩一下",
                value = settings.shortMinutes,
                min = 1,
                max = 30,
                onChange = { vm.setMinutes(TimerMode.Short, it) },
            )
            DurationRow(
                icon = MiuixIcons.Timer,
                title = "长休息",
                summary = "深度恢复",
                value = settings.longMinutes,
                min = 1,
                max = 60,
                onChange = { vm.setMinutes(TimerMode.Long, it) },
            )
            DurationRow(
                icon = MiuixIcons.Refresh,
                title = "长休息间隔",
                summary = "每几轮进入长休息",
                value = settings.sessionsBeforeLong,
                min = 2,
                max = 8,
                onChange = { vm.setSessionsBeforeLong(it) },
            )
        }

        /* ------------------------------ 行为 ------------------------------ */
        SectionCard(title = "设置", highlight = "行为") {
            SwitchRow(
                icon = MiuixIcons.Play,
                title = "自动开始下一轮",
                summary = "结束后自动继续",
                checked = settings.autoStart,
                onCheckedChange = vm::setAutoStart,
            )
            SwitchRow(
                icon = MiuixIcons.VolumeUp,
                title = "提示音",
                summary = "结束时播放提醒",
                checked = settings.sound,
                onCheckedChange = vm::setSound,
            )
            SwitchRow(
                icon = MiuixIcons.Promotions,
                title = "桌面通知",
                summary = "结束时系统提醒",
                checked = settings.notify,
                onCheckedChange = { want ->
                    when {
                        !want -> vm.setNotify(false)
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            Notifier.hasPermission(context) -> {
                            Notifier.ensureChannel(context)
                            vm.setNotify(true)
                        }
                        else -> requestNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            SwitchRow(
                icon = PictureInPictureIcon,
                title = "悬浮迷你计时器",
                summary = "可拖动的迷你圆环",
                checked = settings.miniTimer,
                onCheckedChange = vm::setMiniTimer,
            )
        }

        /* ------------------------------ 主题 ------------------------------ */
        SectionCard(title = "风格", highlight = "主题") {
            BasicComponent(
                title = "强调色",
                summary = "选择你的主色调",
                startAction = { SettingIcon(MiuixIcons.Tune) },
            )
            Spacer(Modifier.height(14.dp))
            AccentSwatches(selected = settings.accent, onSelect = vm::setAccent)
            Spacer(Modifier.height(18.dp))
            BasicComponent(
                title = "深浅模式",
                summary = settings.themeMode.label,
                startAction = { SettingIcon(MiuixIcons.Theme) },
            )
            Spacer(Modifier.height(14.dp))
            TabRow(
                tabs = ThemeMode.entries.map { it.label },
                selectedTabIndex = settings.themeMode.ordinal,
                onTabSelected = { vm.setThemeMode(ThemeMode.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        /* ------------------------------ 背景 ------------------------------ */
        SectionCard(title = "图片", highlight = "背景") {
            TabRow(
                tabs = BgMode.entries.map { it.label },
                selectedTabIndex = settings.bgMode.ordinal,
                onTabSelected = { vm.setBgMode(BgMode.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (settings.bgMode == BgMode.Custom) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.UploadCloud,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("选择图片", style = MiuixTheme.textStyles.button)
                    }
                    Button(
                        onClick = vm::clearBackground,
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Image,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("恢复默认", style = MiuixTheme.textStyles.button)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "背景暗度（数值越大，玻璃卡片越清晰）",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                Slider(
                    value = settings.bgDim,
                    onValueChange = vm::setBgDim,
                    valueRange = 0f..0.7f,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "默认使用与 Miuix 一致的纯色背景；选择「自定义」可从相册挑一张图作为毛玻璃底图。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
    }
}

@Composable
private fun DurationRow(
    icon: ImageVector,
    title: String,
    summary: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = { SettingIcon(icon) },
        endActions = {
            Stepper(
                value = value,
                canMinus = value > min,
                canPlus = value < max,
                onMinus = { onChange((value - 1).coerceAtLeast(min)) },
                onPlus = { onChange((value + 1).coerceAtMost(max)) },
            )
        },
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = { SettingIcon(icon) },
        endActions = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

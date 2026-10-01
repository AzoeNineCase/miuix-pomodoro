package com.miuix.pomodoro.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.pomodoro.PomodoroViewModel
import com.miuix.pomodoro.data.TimerMode
import com.miuix.pomodoro.ui.LongBreakColor
import com.miuix.pomodoro.ui.ShortBreakColor
import com.miuix.pomodoro.ui.components.SectionCard
import com.miuix.pomodoro.ui.components.SessionDot
import com.miuix.pomodoro.ui.components.TimerRing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 计时页：模式选择 + 圆环计时 + 控制按钮 + 轮次圆点 */
@Composable
fun TimerScreen(vm: PomodoroViewModel) {
    val mode = vm.mode
    val modeColor = when (mode) {
        TimerMode.Focus -> Color(vm.settings.accent)
        TimerMode.Short -> ShortBreakColor
        TimerMode.Long -> LongBreakColor
    }
    val modeIcon: ImageVector = when (mode) {
        TimerMode.Focus -> MiuixIcons.Stopwatch
        TimerMode.Short -> MiuixIcons.Alarm
        TimerMode.Long -> MiuixIcons.Timer
    }
    val timeText = "%02d:%02d".format(vm.remaining / 60, vm.remaining % 60)
    val started = vm.running || vm.remaining != vm.total

    // 切换模式时圆环弹入（对应网页版的 popIn）
    val ringScale = remember { Animatable(1f) }
    LaunchedEffect(vm.mode) {
        ringScale.snapTo(0.9f)
        ringScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    // 开始 / 暂停时按钮图标弹动（对应网页版的 iconPop）
    val iconScale = remember { Animatable(1f) }
    LaunchedEffect(vm.running) {
        iconScale.snapTo(0.5f)
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.42f,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    val perCycle = vm.settings.sessionsBeforeLong
    val inCycle = vm.completedSessions % perCycle
    val doneCount = if (inCycle == 0 && vm.completedSessions > 0) perCycle else inCycle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "选择", highlight = "模式") {
            TabRow(
                tabs = TimerMode.entries.map { it.label },
                selectedTabIndex = mode.ordinal,
                onTabSelected = { vm.selectMode(TimerMode.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimerRing(
                    progress = vm.miniProgress,
                    color = modeColor,
                    running = vm.running,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .scale(ringScale.value),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeText,
                            style = MiuixTheme.textStyles.title1,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = mode.label,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = vm::reset,
                        modifier = Modifier.size(52.dp),
                        backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Reset,
                            contentDescription = "重置",
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.onSecondaryVariant,
                        )
                    }
                    Button(
                        onClick = vm::toggle,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Icon(
                            imageVector = if (vm.running) MiuixIcons.Pause else MiuixIcons.Play,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(iconScale.value),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (vm.running) "暂停" else if (started) "继续" else "开始",
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    IconButton(
                        onClick = vm::skip,
                        modifier = Modifier.size(52.dp),
                        backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Forward,
                            contentDescription = "跳过",
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.onSecondaryVariant,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(perCycle) { index ->
                        SessionDot(done = index < doneCount)
                    }
                }
            }
        }
    }
}

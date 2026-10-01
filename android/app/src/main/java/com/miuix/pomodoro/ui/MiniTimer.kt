package com.miuix.pomodoro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.pomodoro.PomodoroViewModel
import com.miuix.pomodoro.data.TimerMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 悬浮迷你计时器（对应网页版右下角的迷你圆环，可拖动） */
@Composable
fun MiniTimer(vm: PomodoroViewModel, modifier: Modifier = Modifier) {
    val modeColor = when (vm.mode) {
        TimerMode.Focus -> Color(vm.settings.accent)
        TimerMode.Short -> ShortBreakColor
        TimerMode.Long -> LongBreakColor
    }
    val trackColor = MiuixTheme.colorScheme.surfaceContainerHighest

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(40.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .border(1.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(40.dp))
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val strokeWidth = 4.dp.toPx()
            val arcSize = Size(size.minDimension - strokeWidth, size.minDimension - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = modeColor,
                startAngle = -90f,
                sweepAngle = 360f * vm.miniProgress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "%02d:%02d".format(vm.remaining / 60, vm.remaining % 60),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(10.dp))
        IconButton(
            onClick = vm::toggle,
            modifier = Modifier.size(36.dp),
            backgroundColor = MiuixTheme.colorScheme.primary,
            cornerRadius = 50.dp,
        ) {
            Icon(
                imageVector = if (vm.running) MiuixIcons.Pause else MiuixIcons.Play,
                contentDescription = "开始或暂停",
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onPrimary,
            )
        }
    }
}

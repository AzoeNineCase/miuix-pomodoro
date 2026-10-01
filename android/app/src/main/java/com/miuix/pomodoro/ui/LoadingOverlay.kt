package com.miuix.pomodoro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private const val APP_BAR_DURATION_NS = 1_150_000_000f
private const val FONT_BAR_DELAY_RATIO = 0.15f

/**
 * 冷启动加载页：对应网页版的 #loading（呼吸图标 + App / Font 双进度条）。
 * 进度由帧回调驱动，播放完淡出并通知外层移除。
 */
@Composable
fun LoadingOverlay(onFinished: () -> Unit) {
    var appProgress by remember { mutableFloatStateOf(0f) }
    var fontProgress by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - start) / APP_BAR_DURATION_NS).coerceIn(0f, 1f)
            appProgress = t
            fontProgress = ((t - FONT_BAR_DELAY_RATIO) / (1f - FONT_BAR_DELAY_RATIO)).coerceIn(0f, 1f)
            if (t >= 1f) break
        }
        visible = false
        delay(420)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(tween(durationMillis = 400)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                // 加载期间吞掉点击，避免误触到下面的按钮
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LoadingLogo()
                Text(
                    text = "番茄钟",
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                )
                LoadingRow(label = "App", progress = appProgress)
                LoadingRow(label = "Font", progress = fontProgress)
            }
        }
    }
}

/** 呼吸的圆角图标 */
@Composable
private fun LoadingLogo() {
    val transition = rememberInfiniteTransition(label = "loading-logo")
    val logoScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoScale",
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(logoScale)
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Timer,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MiuixTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** 一条进度条 + 左右状态的文字 */
@Composable
private fun LoadingRow(label: String, progress: Float) {
    Column(
        modifier = Modifier.width(200.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }
    }
}

package com.miuix.pomodoro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miuix.pomodoro.data.ACCENT_COLORS
import com.miuix.pomodoro.ui.MinusIcon
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 卡片 + 标题（对应网页版的 .card + .card-title） */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (highlight != null) {
                Text(
                    text = highlight,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }
        content()
    }
}

/** 设置项左侧的圆角色块图标 */
@Composable
fun SettingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MiuixTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** 数值步进器（− 数值 ＋） */
@Composable
fun Stepper(
    value: Int,
    canMinus: Boolean,
    canPlus: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onMinus,
            enabled = canMinus,
            modifier = Modifier.size(34.dp),
            backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
        ) {
            Icon(MinusIcon, contentDescription = "减少", tint = MiuixTheme.colorScheme.onSecondaryVariant)
        }
        Text(
            text = value.toString(),
            modifier = Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = onPlus,
            enabled = canPlus,
            modifier = Modifier.size(34.dp),
            backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
        ) {
            Icon(MiuixIcons.Add, contentDescription = "增加", tint = MiuixTheme.colorScheme.onSecondaryVariant)
        }
    }
}

/** 统计数值格：数值变化时弹一下（对应网页版的 bump） */
@Composable
fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        scale.snapTo(1f)
        scale.animateTo(1.2f, tween(durationMillis = 140))
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(
            text = value,
            modifier = Modifier.scale(scale.value),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
    }
}

/** 强调色色板 */
@Composable
fun AccentSwatches(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ACCENT_COLORS.forEach { (name, color) ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(color))
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MiuixTheme.colorScheme.onBackground else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = name,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** 一轮专注的小圆点 */
@Composable
fun SessionDot(done: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(
                if (done) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHighest,
            ),
    )
}

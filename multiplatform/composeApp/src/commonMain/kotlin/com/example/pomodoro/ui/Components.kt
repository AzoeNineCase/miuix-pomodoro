package com.example.pomodoro.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pomodoro.AppTheme
import com.example.pomodoro.AppColors
import com.example.pomodoro.W

/* ============================================================
 * 通用组件：尺寸/圆角/字重全部对应 index.html 的 CSS 规格
 * ============================================================ */

/**
 * 背景图激活时（对应网页 html[data-bg-active]）把底色按 color-mix 的百分比混透明：
 * color-mix(in srgb, token N%, transparent) 等价于 alpha *= N%；未激活时原样返回。
 */
@Composable
internal fun glassDim(color: Color, pct: Float): Color {
    val c = AppTheme.colors
    return if (c.bgActive) color.copy(alpha = color.alpha * pct) else color
}

/**
 * 毛玻璃底色：极光主题下网页用的是各自的白透明度值（而非统一的 surface-container）：
 *   .segment rgba(255,255,255,.08) / .stepper .08 / .tone-chip .08 / .btn-ghost .1 /
 *   .switch .14 / .segment button.active .16 / .ring-track .16 / .card .07
 * 原生无法做真正的 backdrop 采样（Compose 无背层模糊），但底色、描边与阴影可完全对齐；
 * 极光背景本身是低频渐变，模糊后与不模糊的视觉差异极小。
 */
@Composable
private fun glassSurface(level: Int): Color {
    val c = AppTheme.colors
    val base = if (c.aurora) {
        Color.White.copy(alpha = when (level) {
            2 -> 0.16f  // .segment button.active（极光）
            3 -> 0.14f  // .switch（极光）
            1 -> 0.10f  // .btn-ghost（极光）
            else -> 0.08f
        })
    } else {
        when (level) {
            2 -> c.surface            // .segment button.active
            3 -> c.containerHighest   // .switch 轨道
            else -> c.containerHigh   // .segment / .stepper / .tone-chip / .btn-ghost
        }
    }
    // [data-bg-active]：.segment/.stepper/.tone-chip 58%、.btn-ghost 52%、选中项 60%；
    // .switch 网页未定义规则，保持不变
    val pct = when (level) {
        1 -> 0.52f
        2 -> 0.60f
        3 -> 1f
        else -> 0.58f
    }
    return glassDim(base, pct)
}

/** 图标（Material Symbols Rounded 字形，与网页同字体同设置） */
@Composable
fun Symbol(name: String, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Text(
        text = SYMBOLS[name].orEmpty(),
        modifier = modifier,
        color = color,
        fontSize = with(density) { size.toSp() },
        fontFamily = AppTheme.symbols,
        style = TextStyle(lineHeight = with(density) { size.toSp() }),
        maxLines = 1,
    )
}

/** 卡片：bg surface-container / 1px divider 描边 / 圆角 20 / 内边距 26 / 阴影 */
@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    padding: Dp = 26.dp,
    radius: Dp = 20.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    Column(
        modifier
            .shadow(8.dp, RoundedCornerShape(radius), clip = false, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(RoundedCornerShape(radius))
            // [data-bg-active]：卡片底色 52%、描边 70%（对应网页 .card）
            .background(glassDim(c.container, 0.52f))
            .border(1.dp, glassDim(c.divider, 0.70f), RoundedCornerShape(radius))
            .padding(padding),
        content = content,
    )
}

/** 卡片标题：<strong> 部分用 onSurface，其余用 onSurfaceVariant，15sp/700 */
@Composable
fun CardTitle(strong: String, rest: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier) {
        Text(strong, color = c.onSurface, fontFamily = AppTheme.font, fontSize = 15.sp, fontWeight = W.bold, letterSpacing = 0.2.sp)
        Text(rest, color = c.variant, fontFamily = AppTheme.font, fontSize = 15.sp, fontWeight = W.bold, letterSpacing = 0.2.sp)
    }
}

/** 分段控件：容器 radius 16 + padding 4 + gap 4；选中项 bg surface + 底部 3px 主色条 */
@Composable
fun Segment(
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
    onSelect: (Int) -> Unit,
) {
    val c = AppTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(glassSurface(0))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, label ->
            val active = i == selectedIndex
            val bg by animateColorAsState(if (active) glassSurface(2) else Color.Transparent, tween(350))
            val fg by animateColorAsState(if (active) c.onSurface else c.variant, tween(350))
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                // 选中态下方的主色条（growbar：从左向右 scaleX 展开）
                if (active) {
                    val p by animateFloatAsState(1f, spring(dampingRatio = 0.6f, stiffness = 500f))
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .fillMaxWidth(if (p <= 0f) 0.001f else 1f)
                            .padding(horizontal = 12.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c.primary),
                    )
                }
                Text(label, color = fg, fontFamily = AppTheme.font, fontSize = 14.sp, fontWeight = W.semi)
            }
        }
    }
}

/** 步进器：容器 radius 12 + padding 4；按钮 34×34 radius 9；数字等宽 Inter */
@Composable
fun Stepper(value: Int, onDelta: (Int) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(glassSurface(0)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("remove") { onDelta(-1) }
        Box(Modifier.widthIn(min = 44.dp), contentAlignment = Alignment.Center) {
            Text(
                "$value",
                color = c.onSurface,
                fontFamily = AppTheme.font,
                fontSize = 15.sp,
                fontWeight = W.bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp),
            )
        }
        StepperButton("add") { onDelta(1) }
    }
}

@Composable
private fun StepperButton(icon: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, spring(dampingRatio = 0.55f, stiffness = 400f))
    Box(
        Modifier
            .size(34.dp)
            .scale(scale)
            .clip(RoundedCornerShape(9.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, 20.dp, c.onSurface) }
}

/** 开关：50×30 胶囊，圆形滑块 24，开启位移 20 */
@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    val track by animateColorAsState(if (checked) c.primary else glassSurface(3), tween(300))
    val knob by animateColorAsState(if (checked) c.onPrimary else c.onSurface, tween(300))
    Box(
        Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(track)
            .clickable { onChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(24.dp)
                .graphicsLayer { translationX = if (checked) 20.dp.toPx() else 0f }
                .shadow(2.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

/** 无文字图标按钮（顶栏/背景）：44×44 radius 14 */
@Composable
fun IconBtn(icon: String, size: Dp = 44.dp, iconSize: Dp = 22.dp, tint: Color? = null, bg: Color = Color.Transparent, onClick: () -> Unit) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f))
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, iconSize, tint ?: c.onSurface) }
}

/** 幽灵按钮（重置/跳过）：52×52 radius 18，bg container-high */
@Composable
fun GhostButton(icon: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f))
    Box(
        Modifier
            .size(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(glassSurface(1))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, 24.dp, c.onSurface) }
}

/** 主按钮（开始/暂停/添加 等）：高 60，左右内边距 34，radius 18，主色 + 主色阴影 */
@Composable
fun PrimaryButton(
    text: String,
    icon: String? = null,
    running: Boolean = false,
    height: Dp = 60.dp,
    fontSize: Int = 18,
    paddingHorizontal: Dp = 34.dp,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val bg by animateColorAsState(if (running) c.containerHigh else c.primary, tween(300))
    val fg by animateColorAsState(if (running) c.onSurface else c.onPrimary, tween(300))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f))
    Row(
        Modifier
            .height(height)
            .scale(scale)
            .then(if (running) Modifier else Modifier.shadow(10.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = c.primary.copy(alpha = 0.35f), spotColor = c.primary.copy(alpha = 0.35f)))
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = paddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Symbol(icon, if (fontSize >= 16) 26.dp else 20.dp, fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontFamily = AppTheme.font, fontSize = fontSize.sp, fontWeight = W.bold)
    }
}

/** 统计单元格：bg surface，radius 16，padding 20/18，数值 28sp/800 主色、等宽 */
@Composable
fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            // [data-bg-active]：.stat-cell 底色 52%
            .background(glassDim(c.surface, 0.52f))
            .border(1.dp, c.divider, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, color = c.primary, fontFamily = AppTheme.font, fontSize = 28.sp, fontWeight = W.extra)
        Text(label, color = c.variant, fontFamily = AppTheme.font, fontSize = 13.sp, fontWeight = W.semi)
    }
}

/** 设置行：图标徽标 + 名称/副标题 + 尾部控件；行高由内容撑开，底部分割线 */
@Composable
fun SettingRow(
    icon: String,
    name: String,
    sub: String?,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.soft),
                    contentAlignment = Alignment.Center,
                ) { Symbol(icon, 22.dp, c.primary) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(name, color = c.onSurface, fontFamily = AppTheme.font, fontSize = 15.sp, fontWeight = W.bold)
                    if (!sub.isNullOrEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(sub, color = c.variant, fontFamily = AppTheme.font, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            trailing?.invoke()
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
    }
}

/** 圆角小胶囊（音色 chip） */
@Composable
fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val bg by animateColorAsState(if (active) c.primary else glassSurface(0), tween(300))
    val fg by animateColorAsState(if (active) c.onPrimary else c.variant, tween(300))
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (active) Modifier else Modifier.border(1.dp, c.divider, RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) { Text(text, color = fg, fontFamily = AppTheme.font, fontSize = 13.sp, fontWeight = W.semi) }
}

/** 通用圆角背景容器（输入框等） */
@Composable
fun FieldBox(shape: Shape = RoundedCornerShape(14.dp), modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Box(modifier.clip(shape).background(c.containerHigh).border(1.dp, c.divider, shape), contentAlignment = Alignment.CenterStart) { content() }
}

/** Toast：底部居中的深色气泡 */
@Composable
fun ToastBar(text: String, icon: String) {
    val c = AppTheme.colors
    Row(
        Modifier
            .shadow(16.dp, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(if (c.aurora) c.onSurface else c.onSurface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(icon, 20.dp, if (c.aurora) Color(0xFF12152C) else c.surface)
        Text(text, color = if (c.aurora) Color(0xFF12152C) else c.surface, fontFamily = AppTheme.font, fontSize = 14.sp, fontWeight = W.semi)
    }
}

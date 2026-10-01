package com.miuix.pomodoro.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Miuix 图标集里缺少几个本应用需要的字形：
 * - `Remove` 实际是「移出文件夹」，不是减号；
 * - `Theme` 是主题商店的油漆桶，不适合做日/夜切换；
 * - 也没有柱状图（统计）和画中画（悬浮计时器）。
 *
 * 这里按与 Miuix 相同的 24dp 网格、同样使用纯色填充自行绘制，
 * 保证与组件视觉一致（颜色由 Icon 的 tint 决定）。
 */

private fun buildIcon(
    name: String,
    pathData: List<PathNode>,
    fillType: PathFillType = PathFillType.NonZero,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(pathData = pathData, pathFillType = fillType, fill = SolidColor(Color.Black))
}.build()

/** 用两段圆弧拼出一个圆形子路径 */
private fun circlePath(cx: Float, cy: Float, r: Float): List<PathNode> = listOf(
    PathNode.MoveTo(cx, cy - r),
    PathNode.ArcTo(r, r, 0f, true, true, cx, cy + r),
    PathNode.ArcTo(r, r, 0f, true, true, cx, cy - r),
    PathNode.Close,
)

/** 减号（配合 Miuix 的 Add 使用） */
val MinusIcon: ImageVector by lazy {
    buildIcon(
        name = "Minus",
        pathData = listOf(
            PathNode.MoveTo(5f, 11f),
            PathNode.HorizontalTo(19f),
            PathNode.VerticalTo(13f),
            PathNode.HorizontalTo(5f),
            PathNode.Close,
        ),
    )
}

/** 太阳：浅色主题 */
val SunIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LightMode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = circlePath(12f, 12f, 5.4f), fill = SolidColor(Color.Black))
        // 八条光芒：同一根短棒绕中心旋转
        val ray = listOf(
            PathNode.MoveTo(11f, 1.6f),
            PathNode.HorizontalTo(13f),
            PathNode.VerticalTo(4.6f),
            PathNode.HorizontalTo(11f),
            PathNode.Close,
        )
        repeat(8) { index ->
            group(rotate = index * 45f, pivotX = 12f, pivotY = 12f) {
                addPath(pathData = ray, fill = SolidColor(Color.Black))
            }
        }
    }.build()
}

/** 月亮：深色主题（用偶奇填充挖出月牙） */
val MoonIcon: ImageVector by lazy {
    buildIcon(
        name = "DarkMode",
        pathData = circlePath(12f, 12f, 9f) + circlePath(19f, 12f, 7f),
        fillType = PathFillType.EvenOdd,
    )
}

/** 柱状图：统计页签 */
val ChartIcon: ImageVector by lazy {
    buildIcon(
        name = "Chart",
        pathData = listOf(
            PathNode.MoveTo(4.5f, 13.5f),
            PathNode.HorizontalTo(8.5f),
            PathNode.VerticalTo(20f),
            PathNode.HorizontalTo(4.5f),
            PathNode.Close,
            PathNode.MoveTo(10f, 9f),
            PathNode.HorizontalTo(14f),
            PathNode.VerticalTo(20f),
            PathNode.HorizontalTo(10f),
            PathNode.Close,
            PathNode.MoveTo(15.5f, 4f),
            PathNode.HorizontalTo(19.5f),
            PathNode.VerticalTo(20f),
            PathNode.HorizontalTo(15.5f),
            PathNode.Close,
        ),
    )
}

/** 画中画：悬浮迷你计时器设置项 */
val PictureInPictureIcon: ImageVector by lazy {
    buildIcon(
        name = "PictureInPicture",
        pathData = listOf(
            // 外框四条边
            PathNode.MoveTo(4f, 4.8f),
            PathNode.HorizontalTo(20f),
            PathNode.VerticalTo(6.6f),
            PathNode.HorizontalTo(4f),
            PathNode.Close,
            PathNode.MoveTo(4f, 17.4f),
            PathNode.HorizontalTo(20f),
            PathNode.VerticalTo(19.2f),
            PathNode.HorizontalTo(4f),
            PathNode.Close,
            PathNode.MoveTo(4f, 6.6f),
            PathNode.HorizontalTo(5.8f),
            PathNode.VerticalTo(17.4f),
            PathNode.HorizontalTo(4f),
            PathNode.Close,
            PathNode.MoveTo(18.2f, 6.6f),
            PathNode.HorizontalTo(20f),
            PathNode.VerticalTo(17.4f),
            PathNode.HorizontalTo(18.2f),
            PathNode.Close,
            // 右下角小窗
            PathNode.MoveTo(12.2f, 11.2f),
            PathNode.HorizontalTo(17.6f),
            PathNode.VerticalTo(15.8f),
            PathNode.HorizontalTo(12.2f),
            PathNode.Close,
        ),
    )
}

package com.example.pomodoro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import pomodorotimer.composeapp.generated.resources.Res
import pomodorotimer.composeapp.generated.resources.inter_bold
import pomodorotimer.composeapp.generated.resources.inter_extrabold
import pomodorotimer.composeapp.generated.resources.inter_medium
import pomodorotimer.composeapp.generated.resources.inter_regular
import pomodorotimer.composeapp.generated.resources.inter_semibold
import pomodorotimer.composeapp.generated.resources.material_symbols_rounded
import org.jetbrains.compose.resources.Font

/**
 * 字体：与网页版 index.html 完全同一份来源。
 *
 * - [inter]：Inter 拉丁子集（400/500/600/700/800），取自 `android/app/src/main/assets/fonts/`
 *   中 WebView 版内置的同一批 woff2（`multiplatform/tools/prepare_fonts.py` 转成 TTF）。
 *   中日韩字符不在 Inter 里，由系统字体（安卓 MIUI 的 MiSans / Windows 的微软雅黑）逐字回退 ——
 *   与网页 CSS 字体栈 `"MiSans", "Inter", …, "Microsoft YaHei", sans-serif` 行为一致。
 * - [symbols]：Material Symbols Rounded（FILL 0 / wght 400 / GRAD 0 / opsz 24 静态实例、
 *   子集化到 72 个图标），字形与网页版 `material-symbols-rounded` 完全相同。
 */
object AppFonts {
    val inter: FontFamily
        @Composable get() = FontFamily(
            Font(Res.font.inter_regular, weight = FontWeight.Normal),
            Font(Res.font.inter_medium, weight = FontWeight.Medium),
            Font(Res.font.inter_semibold, weight = FontWeight.SemiBold),
            Font(Res.font.inter_bold, weight = FontWeight.Bold),
            Font(Res.font.inter_extrabold, weight = FontWeight.ExtraBold),
        )

    val symbols: FontFamily
        @Composable get() = FontFamily(Font(Res.font.material_symbols_rounded))
}

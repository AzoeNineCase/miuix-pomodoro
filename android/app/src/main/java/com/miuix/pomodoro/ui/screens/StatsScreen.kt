package com.miuix.pomodoro.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miuix.pomodoro.PomodoroViewModel
import com.miuix.pomodoro.data.PomodoroRepository
import com.miuix.pomodoro.ui.components.SectionCard
import com.miuix.pomodoro.ui.components.StatCell
import com.miuix.pomodoro.ui.components.WeekChart
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 统计页：今日 / 累计 / 本周分布 */
@Composable
fun StatsScreen(vm: PomodoroViewModel) {
    val context = LocalContext.current
    val today = vm.today
    val (totalSessions, totalMinutes, activeDays) = vm.totals

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            // openOutputStream 返回 null 时不能装作写成功，否则用户只看到「已导出」而文件不存在
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("openOutputStream returned null")
            stream.use { it.write(vm.exportJson().toByteArray()) }
        }.onSuccess {
            vm.showToast("已导出数据")
        }.onFailure {
            vm.showToast("导出失败")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "专注", highlight = "今日") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCell("${today.sessions}", "完成番茄", Modifier.weight(1f))
                StatCell("${today.focusMin}", "专注分钟", Modifier.weight(1f))
                StatCell("${today.best}", "最长连续", Modifier.weight(1f))
            }
        }

        SectionCard(title = "专注", highlight = "累计") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCell("$totalSessions", "累计番茄", Modifier.weight(1f))
                StatCell("%.1f".format(totalMinutes / 60f), "累计小时", Modifier.weight(1f))
                StatCell("$activeDays", "专注天数", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { exportLauncher.launch("pomodoro-${PomodoroRepository.todayKeyStatic()}.json") },
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(
                    imageVector = MiuixIcons.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("导出数据", style = MiuixTheme.textStyles.button)
            }
        }

        SectionCard(title = "分布", highlight = "本周") {
            WeekChart(data = vm.weekSessions)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "完成一个番茄后，数据会自动记入今天",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

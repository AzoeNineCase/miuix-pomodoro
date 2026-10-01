package com.example.pomodoro

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/* ---------- 跨平台时间工具（kotlinx-datetime，替代 JVM 专属的 SimpleDateFormat/Calendar） ---------- */

internal fun pad2(n: Int): String = if (n < 10) "0$n" else "$n"

internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

internal fun formatHm(ts: Long): String {
    val dt = Instant.fromEpochMilliseconds(ts).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${pad2(dt.hour)}:${pad2(dt.minute)}"
}

internal fun todayIsoDate(): String =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

internal fun todayStartMillis(): Long {
    val tz = TimeZone.currentSystemDefault()
    val date = Clock.System.now().toLocalDateTime(tz).date
    return date.atStartOfDayIn(tz).toEpochMilliseconds()
}

@Immutable
data class HistoryEntry(val mode: Mode, val minutes: Int, val ts: Long = nowMillis()) {
    val timeStr: String get() = formatHm(ts)
}

/**持久化接口，平台层实现具体存储 */
interface SettingsStorage {
    fun saveInt(key: String, value: Int)
    fun loadInt(key: String, default: Int): Int
    fun saveBoolean(key: String, value: Boolean)
    fun loadBoolean(key: String, default: Boolean): Boolean
    fun saveString(key: String, value: String)
    fun loadString(key: String, default: String): String
}

/**空实现，用于桌面端 */
object NoOpStorage : SettingsStorage {
    override fun saveInt(key: String, value: Int) {}
    override fun loadInt(key: String, default: Int): Int = default
    override fun saveBoolean(key: String, value: Boolean) {}
    override fun loadBoolean(key: String, default: Boolean): Boolean = default
    override fun saveString(key: String, value: String) {}
    override fun loadString(key: String, default: String): String = default
}

@Stable
class State(private val storage: SettingsStorage = NoOpStorage) {
    var mode by mutableStateOf(Mode.Work); private set
    var remain by mutableIntStateOf(25 * 60); private set
    var total by mutableIntStateOf(25 * 60); private set
    var running by mutableStateOf(false); private set
    var finished by mutableStateOf(false); private set

    var cfgWork by mutableIntStateOf(storage.loadInt("cfgWork", 25))
    var cfgShort by mutableIntStateOf(storage.loadInt("cfgShort", 5))
    var cfgLong by mutableIntStateOf(storage.loadInt("cfgLong", 15))
    var autoStart by mutableStateOf(storage.loadBoolean("autoStart", true))
    var soundOn by mutableStateOf(storage.loadBoolean("soundOn", true))
    var dailyGoal by mutableIntStateOf(storage.loadInt("dailyGoal", 8))
    var focusMode by mutableStateOf(false)

    var sessions by mutableIntStateOf(storage.loadInt("sessions", 0))
    var sessionN by mutableIntStateOf((storage.loadInt("sessions", 0) % 4) + 1)
    var taskName by mutableStateOf(storage.loadString("taskName", ""))
    val lastResetDate = mutableStateOf(storage.loadString("lastResetDate", ""))
    val history = mutableStateListOf<HistoryEntry>().apply {
        addAll(
            storage.loadString("history", "").split("|").filter { it.isNotBlank() }.mapNotNull { entry ->
                try {
                    val parts = entry.split(",")
                    if (parts.size == 3) {
                        val modeOrdinal = parts[0].toIntOrNull() ?: return@mapNotNull null
                        val minutes = parts[1].toIntOrNull() ?: return@mapNotNull null
                        val ts = parts[2].toLongOrNull() ?: return@mapNotNull null
                        val mode = Mode.entries.getOrElse(modeOrdinal) { Mode.Work }
                        HistoryEntry(mode, minutes, ts)
                    } else null
                } catch (_: Exception) { null }
            }
        )
    }

    var showCfg by mutableStateOf(false)
    var showStats by mutableStateOf(false)

    var completionPulse by mutableFloatStateOf(0f); private set
    var quote by mutableStateOf(Quotes.random())
    var breakTip by mutableStateOf(BreakTips.random())

    var sliderWork by mutableFloatStateOf(25f)
    var sliderShort by mutableFloatStateOf(5f)
    var sliderLong by mutableFloatStateOf(15f)
    var sliderGoal by mutableFloatStateOf(8f)

    var onTimerFinished: (() -> Unit)? = null

    private var timerJob: Job? = null

    val progress by derivedStateOf { if (total > 0) (total - remain).toFloat() / total else 0f }
    val mins by derivedStateOf { remain / 60 }
    val secs by derivedStateOf { remain % 60 }
    val elapsed by derivedStateOf { total - remain }
    val eMins by derivedStateOf { elapsed / 60 }
    val eSecs by derivedStateOf { elapsed % 60 }
    val color by derivedStateOf { mode.color }
    val sessionLabel by derivedStateOf { "第 $sessionN / 4 个番茄" }
    val untilLong by derivedStateOf { 4 - (sessions % 4) }
    val displayTitle by derivedStateOf { "${pad2(mins)}:${pad2(secs)} — 番茄钟" }

    private fun secsOf(m: Mode) = when (m) { Mode.Work -> cfgWork; Mode.Short -> cfgShort; Mode.Long -> cfgLong } * 60

    fun saveSettings() {
        storage.saveInt("cfgWork", cfgWork)
        storage.saveInt("cfgShort", cfgShort)
        storage.saveInt("cfgLong", cfgLong)
        storage.saveInt("dailyGoal", dailyGoal)
        storage.saveBoolean("autoStart", autoStart)
        storage.saveBoolean("soundOn", soundOn)
        storage.saveInt("sessions", sessions)
        storage.saveString("lastResetDate", lastResetDate.value)
        storage.saveString("taskName", taskName)
        storage.saveString("history", history.takeLast(200).joinToString("|") { "${it.mode.ordinal},${it.minutes},${it.ts}" })
    }

    fun startJob(scope: CoroutineScope) { timerJob?.cancel(); timerJob = scope.launch { timerLoop() } }

    private suspend fun timerLoop() {
        running = true; finished = false; var lastMs = nowMillis()
        while (running && remain > 0) {
            delay(50); if (!running) break; val now = nowMillis(); val dt = now - lastMs
            if (dt >= 1000) {
                val ticks = (dt / 1000).toInt(); lastMs += ticks * 1000L; remain = (remain - ticks).coerceAtLeast(0); if (!running) break
                if (mode == Mode.Work && remain > 0 && remain % 180 == 0) quote = Quotes.random()
                if (mode != Mode.Work && remain > 0 && remain % 120 == 0) breakTip = BreakTips.random()
                if (remain <= 0) {
                    running = false; finished = true; completionPulse = 1f
                    if (soundOn) platformPlaySound(); onComplete()
                }
            }
        }
    }

    fun pause() { running = false; timerJob?.cancel(); timerJob = null }
    fun reset() { running = false; remain = secsOf(mode); total = remain; finished = false; completionPulse = 0f }
    fun skip() { running = false; completionPulse = 1f; if (soundOn) platformPlaySound(); onComplete() }
    fun switchMode(m: Mode) { running = false; mode = m; remain = secsOf(m); total = remain; finished = false; completionPulse = 0f; quote = Quotes.random(); breakTip = BreakTips.random() }
    fun setTask(s: String) { taskName = s }
    fun dismissCompletionPulse() { completionPulse = 0f }
    fun autoSave() { saveSettings() }

    fun checkDailyReset() {
        val today = todayIsoDate()
        if (lastResetDate.value != today) {
            sessions = 0
            sessionN = 1
            lastResetDate.value = today
            saveSettings()
        }
    }

    private fun onComplete() {
        val dur = when (mode) { Mode.Work -> cfgWork; Mode.Short -> cfgShort; Mode.Long -> cfgLong }
        history.add(HistoryEntry(mode, dur))
        if (history.size > 200) history.removeRange(0, history.size - 200)
        if (mode == Mode.Work) { sessions++; sessionN = (sessions % 4) + 1 }
        onTimerFinished?.invoke()
        if (autoStart) autoSwitch()
    }

    private fun autoSwitch() {
        mode = when (mode) { Mode.Work -> if (sessions % 4 == 0) Mode.Long else Mode.Short; else -> Mode.Work }
        remain = secsOf(mode); total = remain
    }
}

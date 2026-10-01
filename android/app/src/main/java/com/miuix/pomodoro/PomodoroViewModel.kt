package com.miuix.pomodoro

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miuix.pomodoro.data.BgMode
import com.miuix.pomodoro.data.DayStat
import com.miuix.pomodoro.data.PomodoroRepository
import com.miuix.pomodoro.data.Settings
import com.miuix.pomodoro.data.ThemeMode
import com.miuix.pomodoro.data.TimerMode
import com.miuix.pomodoro.data.TodoItem
import com.miuix.pomodoro.util.Chime
import com.miuix.pomodoro.util.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

/**
 * 应用的唯一状态源：计时、统计、待办、设置。
 *
 * 计时使用「结束时间戳 + 定时刷新」的方式，而不是累加计时，
 * 因此即使界面卡顿或系统延迟，剩余时间也不会走偏。
 */
class PomodoroViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PomodoroRepository(app)

    /* ------------------------------ 状态 ------------------------------ */

    var settings by mutableStateOf(repo.loadSettings())
        private set

    var mode by mutableStateOf(TimerMode.Focus)
        private set

    var remaining by mutableIntStateOf(settings.focusMinutes * 60)
        private set

    var total by mutableIntStateOf(settings.focusMinutes * 60)
        private set

    var running by mutableStateOf(false)
        private set

    /** 当前长休息周期内已完成的专注轮数 */
    var completedSessions by mutableIntStateOf(repo.loadCycle())
        private set

    var allStats by mutableStateOf(repo.loadStats())
        private set

    var todos by mutableStateOf<List<TodoItem>>(repo.loadTodos())
        private set

    /** 短暂显示的提示（对应网页版的 toast） */
    var toastMessage by mutableStateOf<String?>(null)
        private set

    var currentPage by mutableIntStateOf(0)
        private set

    private var dayKey = repo.todayKey()
    private var dayStats = allStats[dayKey] ?: DayStat()
    private var job: Job? = null
    private var endAt = 0L
    private var toastJob: Job? = null

    val today: DayStat get() = dayStats

    /** 累计统计：番茄总数、总分钟、有记录的天数 */
    val totals: Triple<Int, Int, Int>
        get() {
            var sessions = 0
            var minutes = 0
            var days = 0
            allStats.values.forEach {
                sessions += it.sessions
                minutes += it.focusMin
                if (it.sessions > 0) days++
            }
            return Triple(sessions, minutes, days)
        }

    /** 当前周期内连续完成的专注轮数（1..sessionsBeforeLong） */
    private val streak: Int
        get() {
            val n = settings.sessionsBeforeLong
            val inCycle = completedSessions % n
            return if (inCycle == 0) n else inCycle
        }

    val weekSessions: List<Pair<String, Int>> get() = repo.lastWeekSessions()

    val miniProgress: Float get() = if (total > 0) remaining.toFloat() / total else 0f

    /* ------------------------------ 生命周期 ------------------------------ */

    override fun onCleared() {
        job?.cancel()
        Chime.release()
        super.onCleared()
    }

    /* ------------------------------ 计时 ------------------------------ */

    fun setPage(index: Int) {
        currentPage = index
        if (index == PAGE_STATS) ensureToday()
    }

    fun selectMode(target: TimerMode) {
        // 运行中切换模式：先停下，避免「时长被重置但仍在倒计时」的状态错乱
        if (running) pause()
        mode = target
        total = settings.minutesOf(target) * 60
        remaining = total
    }

    fun toggle() = if (running) pause() else start()

    fun start() {
        if (running) return
        if (remaining <= 0) selectMode(mode)
        running = true
        endAt = SystemClock.elapsedRealtime() + remaining * 1000L
        if (settings.sound) Chime.start(viewModelScope)
        job = viewModelScope.launch {
            while (running) {
                val msLeft = endAt - SystemClock.elapsedRealtime()
                val left = ceil(msLeft / 1000.0).toInt()
                if (left <= 0) {
                    remaining = 0
                    running = false
                    job = null
                    onFinished(skipped = false, autoContinue = true)
                    return@launch
                }
                remaining = left
                delay(150)
            }
        }
    }

    fun pause() {
        if (!running) return
        running = false
        job?.cancel()
        job = null
        // 按绝对结束时间对齐剩余秒数：tick 每 150ms 才刷新一次，
        // 不重算的话每次暂停/继续都会悄悄把不足一秒的时间还给用户，反复暂停会累积
        remaining = ceil((endAt - SystemClock.elapsedRealtime()) / 1000.0)
            .toInt()
            .coerceAtLeast(0)
    }

    fun reset() {
        pause()
        selectMode(mode)
        showToast("已重置")
    }

    fun skip() {
        pause()
        onFinished(skipped = true, autoContinue = false)
    }

    private fun onFinished(skipped: Boolean, autoContinue: Boolean) {
        ensureToday()
        val finished = mode
        if (finished == TimerMode.Focus && !skipped) {
            completedSessions++
            repo.saveCycle(completedSessions)
            val updated = dayStats.copy(
                sessions = dayStats.sessions + 1,
                focusMin = dayStats.focusMin + settings.focusMinutes,
                best = maxOf(dayStats.best, streak),
            )
            dayStats = updated
            allStats[dayKey] = updated
            repo.saveStats(allStats)
            allStats = repo.loadStats()
        }

        if (!skipped) {
            if (settings.sound) Chime.finish(viewModelScope, finished == TimerMode.Focus)
            if (settings.notify) {
                Notifier.notify(
                    getApplication(),
                    "🍅 番茄钟",
                    if (finished == TimerMode.Focus) "专注完成！休息一下吧 ☕" else "休息结束，准备开始新的专注 🌟",
                )
            }
        }
        showToast(if (skipped) "已跳过" else "完成${finished.label}")

        // 只在完成过至少一轮、且正好到达间隔倍数时才进入长休息
        val next = if (finished == TimerMode.Focus) {
            if (completedSessions > 0 && completedSessions % settings.sessionsBeforeLong == 0) {
                TimerMode.Long
            } else {
                TimerMode.Short
            }
        } else {
            TimerMode.Focus
        }
        selectMode(next)
        if (settings.autoStart && autoContinue && !skipped) start()
    }

    /** 页面长时间开着跨过午夜时，把统计切到新的一天 */
    fun ensureToday() {
        val k = repo.todayKey()
        if (k == dayKey) return
        dayKey = k
        dayStats = DayStat()
        completedSessions = 0
        repo.saveCycle(0)
    }

    /* ------------------------------ 设置 ------------------------------ */

    private fun editSettings(block: (Settings) -> Settings) {
        settings = block(settings)
        repo.saveSettings(settings)
    }

    fun setMinutes(target: TimerMode, minutes: Int) {
        editSettings { it.withMinutes(target, minutes) }
        // 改的是当前模式的时长时，同步刷新计时器
        if (target == mode) selectMode(mode)
    }

    fun setAutoStart(value: Boolean) = editSettings { it.copy(autoStart = value) }

    fun setSound(value: Boolean) = editSettings { it.copy(sound = value) }

    fun setNotify(value: Boolean) = editSettings { it.copy(notify = value) }

    fun setMiniTimer(value: Boolean) = editSettings { it.copy(miniTimer = value) }

    fun setAccent(color: Int) = editSettings { it.copy(accent = color) }

    fun setThemeMode(value: ThemeMode) = editSettings { it.copy(themeMode = value) }

    fun setBgMode(value: BgMode) = editSettings { it.copy(bgMode = value) }

    fun setBgDim(value: Float) = editSettings { it.copy(bgDim = value) }

    fun setSessionsBeforeLong(value: Int) = editSettings { it.copy(sessionsBeforeLong = value) }

    /** 顶栏按钮：跟随系统 → 浅色 → 深色 → 跟随系统 */
    fun cycleTheme() {
        val next = when (settings.themeMode) {
            ThemeMode.System -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.System
        }
        editSettings { it.copy(themeMode = next) }
        showToast("主题：${next.label}")
    }

    /** 导出为 JSON 文本（配合系统「创建文档」写入到用户选择的位置） */
    fun exportJson(): String {
        val root = org.json.JSONObject()
        root.put("app", "番茄钟 · Miuix")
        root.put("exportedAt", System.currentTimeMillis())
        val statsObj = org.json.JSONObject()
        allStats.forEach { (key, value) ->
            statsObj.put(
                key,
                org.json.JSONObject()
                    .put("sessions", value.sessions)
                    .put("focusMin", value.focusMin)
                    .put("best", value.best),
            )
        }
        root.put("stats", statsObj)
        root.put(
            "settings",
            org.json.JSONObject()
                .put("focusMinutes", settings.focusMinutes)
                .put("shortMinutes", settings.shortMinutes)
                .put("longMinutes", settings.longMinutes)
                .put("sessionsBeforeLong", settings.sessionsBeforeLong),
        )
        return root.toString(2)
    }

    /* ------------------------- 背景图（应用私有目录） ------------------------- */

    fun backgroundFile(): File? {
        val name = settings.bgImage ?: return null
        val f = File(getApplication<Application>().filesDir, name)
        return if (f.exists()) f else null
    }

    /** 把选中的图片复制到应用私有目录并写入设置（写入放到 IO 线程，避免大图卡 UI） */
    fun importBackground(bytes: ByteArray?) {
        if (bytes == null) return
        val app = getApplication<Application>()
        val old = settings.bgImage
        // 每次换图都用新文件名：文件名不变时 AppBackground 的 remember key 也不变，
        // 位图不会重新解码，界面会一直显示旧图（换图看起来“没生效”）
        val name = "bg_custom_${System.currentTimeMillis()}.jpg"
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    File(app.filesDir, name).writeBytes(bytes)
                    // 旧文件不再被引用，顺手删掉，避免私有目录里越攒越多
                    if (old != null && old != name) {
                        runCatching { File(app.filesDir, old).delete() }
                    }
                }.isSuccess
            }
            if (ok) {
                editSettings { it.copy(bgImage = name, bgMode = BgMode.Custom) }
                showToast("背景已更新")
            } else {
                showToast("图片保存失败")
            }
        }
    }

    fun clearBackground() {
        val app = getApplication<Application>()
        settings.bgImage?.let { runCatching { File(app.filesDir, it).delete() } }
        editSettings { it.copy(bgImage = null, bgMode = BgMode.Gradient) }
        showToast("已恢复默认背景")
    }

    /* ------------------------------ 待办 ------------------------------ */

    fun addTodo(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        todos = todos + TodoItem(id = System.currentTimeMillis(), text = trimmed)
        repo.saveTodos(todos)
        showToast("已添加待办")
    }

    fun toggleTodo(id: Long) {
        todos = todos.map { if (it.id == id) it.copy(done = !it.done) else it }
        repo.saveTodos(todos)
    }

    fun deleteTodo(id: Long) {
        todos = todos.filterNot { it.id == id }
        repo.saveTodos(todos)
        showToast("已删除待办")
    }

    /* ------------------------------ 提示 ------------------------------ */

    fun showToast(message: String) {
        toastMessage = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2200)
            toastMessage = null
        }
    }

    fun consumeToast() {
        toastJob?.cancel()
        toastMessage = null
    }

    companion object {
        const val PAGE_TIMER = 0
        const val PAGE_STATS = 1
        const val PAGE_TODOS = 2
        const val PAGE_SETTINGS = 3
    }
}

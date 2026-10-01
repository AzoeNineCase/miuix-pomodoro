package com.miuix.pomodoro

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.ceil

/**
 * 计时器前台服务：让计时在息屏/锁屏后依然准确走完，并在通知栏常驻倒计时。
 *
 * 为什么必须放在原生而不是网页里：Android 会在息屏后节流甚至冻结 WebView 的
 * `setInterval`，网页自己是靠不住的。所以这里：
 *
 * - 持有一个 PARTIAL_WAKE_LOCK，保证息屏后 CPU 仍能按时唤醒；
 * - 通知栏用系统自带的 chronometer 渲染倒计时，**不需要每秒刷新通知**；
 * - 用 Handler 到点判定结束并发完成通知（此时网页就算被冻结也不影响）；
 * - 通知栏按钮不直接改状态，而是通过 [TimerBus] 把指令转给网页，由网页执行后
 *   再把最新状态同步回来（网页是唯一的事实来源）；网页失联时通知栏仍能自行
 *   暂停/继续，不会卡死。
 *
 * 网页侧的对应实现见 index.html 的 `nativeTimer()` / `window.__miuixTimerCommand`。
 */
class PomodoroService : Service() {

    companion object {
        const val ACTION_SYNC = "com.miuix.pomodoro.action.SYNC"
        const val ACTION_TOGGLE = "com.miuix.pomodoro.action.TOGGLE"
        const val ACTION_STOP = "com.miuix.pomodoro.action.STOP"
        const val ACTION_SKIP = "com.miuix.pomodoro.action.SKIP"

        private const val EXTRA_STATE = "state"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_REMAINING = "remaining"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_NOTIFY = "notify"
        private const val EXTRA_COLOR = "color"
        private const val EXTRA_END_TONE = "endTone"

        // ⚠️ 通知通道的重要性 / 提示音一旦创建就不能再改，所以改动这两项时必须连带
        //    升版本号，否则老用户设备上还是旧配置（旧通道顺手删掉）。
        private const val CH_ONGOING = "pomodoro.timer.v2"
        private const val CH_DONE = "pomodoro.done.v2"
        private val LEGACY_CHANNELS = listOf("pomodoro.timer", "pomodoro.done")
        private const val ID_ONGOING = 1001
        private const val ID_DONE = 1002

        @Volatile private var alive = false

        /** 网页把计时状态同步过来；state 取 "running" / "paused" / "idle" */
        fun sync(
            context: Context,
            state: String,
            label: String,
            remaining: Int,
            total: Int,
            sound: Boolean,
            notify: Boolean,
            color: String,
            endTone: String,
        ) {
            val intent = Intent(context, PomodoroService::class.java)
                .setAction(ACTION_SYNC)
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_REMAINING, remaining)
                .putExtra(EXTRA_TOTAL, total)
                .putExtra(EXTRA_SOUND, sound)
                .putExtra(EXTRA_NOTIFY, notify)
                .putExtra(EXTRA_COLOR, color)
                .putExtra(EXTRA_END_TONE, endTone)
            if (state == "idle") {
                // 没有会话时不必把服务拉起来
                if (alive) runCatching { context.startService(intent) }
                return
            }
            // 服务若已在运行，用普通 startService 即可；否则需要前台启动。
            // 后台启动前台服务在 Android 12+ 有限制，失败就退回 startService。
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { runCatching { context.startService(intent) } }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var nm: NotificationManagerCompat? = null

    private var label = "专注"
    private var remaining = 0
    private var total = 0
    private var sound = true
    private var notifyOnDone = true
    private var endTone = "chime"
    private var color = Color.parseColor("#3482FF")
    private var running = false

    /** 结束时对应的 SystemClock.elapsedRealtime()，用单调时钟不受系统时间改动影响 */
    private var endAt = 0L

    private var stopToken = 0
    private var inForeground = false
    private var warned = false
    private val finishTask = Runnable { onFinished() }

    /** 每秒刷新一次通知正文里的剩余时间 */
    private val refreshTask = object : Runnable {
        override fun run() {
            if (!running) return
            pushOngoing(foreground = false)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        alive = true
        nm = NotificationManagerCompat.from(this)
        createChannels()
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        releaseWake()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC -> {
                label = intent.getStringExtra(EXTRA_LABEL) ?: label
                total = intent.getIntExtra(EXTRA_TOTAL, total).coerceAtLeast(1)
                remaining = intent.getIntExtra(EXTRA_REMAINING, remaining).coerceAtLeast(0)
                sound = intent.getBooleanExtra(EXTRA_SOUND, sound)
                notifyOnDone = intent.getBooleanExtra(EXTRA_NOTIFY, notifyOnDone)
                parseColor(intent.getStringExtra(EXTRA_COLOR))?.let { color = it }
                intent.getStringExtra(EXTRA_END_TONE)?.let { endTone = it }
                when (intent.getStringExtra(EXTRA_STATE)) {
                    "running" -> {
                        cancelStop()
                        warned = false
                        running = true
                        endAt = SystemClock.elapsedRealtime() + remaining * 1000L
                        acquireWake()
                        scheduleFinish()
                        pushOngoing(foreground = true)
                        startRefresh()
                    }
                    "paused" -> {
                        cancelStop()
                        running = false
                        stopRefresh()
                        handler.removeCallbacks(finishTask)
                        releaseWake()
                        pushOngoing(foreground = true)
                    }
                    else -> scheduleStop()
                }
            }

            ACTION_TOGGLE -> {
                if (running) {
                    remaining = leftSeconds()
                    running = false
                    stopRefresh()
                    handler.removeCallbacks(finishTask)
                    releaseWake()
                } else {
                    running = true
                    endAt = SystemClock.elapsedRealtime() + remaining * 1000L
                    acquireWake()
                    scheduleFinish()
                    startRefresh()
                }
                pushOngoing(foreground = true)
                TimerBus.toPage("toggle")
            }

            ACTION_STOP -> {
                TimerBus.toPage("stop")
                stopSession()
            }

            ACTION_SKIP -> {
                // 网页会结算这一轮并回传 idle；网页不在就自己收尾
                if (!TimerBus.toPage("skip")) stopSession()
                else handler.postDelayed({ if (running) stopSession() }, 2500)
            }
        }
        return START_NOT_STICKY
    }

    private fun startRefresh() {
        handler.removeCallbacks(refreshTask)
        handler.postDelayed(refreshTask, 1000L)
    }

    private fun stopRefresh() {
        handler.removeCallbacks(refreshTask)
    }

    /** 按单调时钟算剩余秒数，和网页的 Math.ceil 口径保持一致 */
    private fun leftSeconds(): Int =
        if (!running) remaining
        else ceil((endAt - SystemClock.elapsedRealtime()) / 1000.0).toInt().coerceAtLeast(0)

    private fun scheduleFinish() {
        handler.removeCallbacks(finishTask)
        val delay = endAt - SystemClock.elapsedRealtime()
        handler.postDelayed(finishTask, delay.coerceAtLeast(0L))
    }

    /**
     * 网页一连串动作（如 handleComplete → setMode → autoStart）会先给 idle 再给
     * running，立刻停服务会让通知栏闪一下，所以延后一点、期间被取消就不停了。
     */
    private fun scheduleStop() {
        val token = ++stopToken
        handler.postDelayed({ if (token == stopToken) stopSession() }, 700)
    }

    private fun cancelStop() {
        stopToken++
    }

    private fun stopSession() {
        running = false
        stopRefresh()
        handler.removeCallbacks(finishTask)
        releaseWake()
        inForeground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onFinished() {
        running = false
        stopRefresh()
        releaseWake()
        // 提示音由原生播（网页锁屏时放不出声）、且用用户选的音色；
        // 通知通道里不设声音，避免系统再叠一声
        if (sound) Tones.play(this, endTone, "end")
        // 「桌面通知」关掉且「提示音」也关掉时就不打扰用户，只收起常驻通知
        if (notifyOnDone || sound) {
            runCatching { nm?.notify(ID_DONE, buildDone()) }
                .onFailure { report("完成通知发送失败：" + it.javaClass.simpleName) }
        }
        inForeground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        TimerBus.toPage("finished")
    }

    /**
     * 首帧必须走 startForeground（拿到前台服务地位），之后的每秒刷新走 notify 更轻。
     * 任何一步失败都要回报到页面 —— 否则用户只会看到「通知没出现」而无从排查。
     */
    private fun pushOngoing(foreground: Boolean) {
        val n = runCatching { buildOngoing() }.getOrElse {
            report("构建通知失败：" + it.javaClass.simpleName + " " + (it.message ?: ""))
            return
        }
        try {
            if (foreground && !inForeground) {
                @Suppress("InlinedApi")
                ServiceCompat.startForeground(
                    this,
                    ID_ONGOING,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
                inForeground = true
            } else {
                nm?.notify(ID_ONGOING, n)
            }
            if (!hasNotifyPermission()) {
                report("通知被系统屏蔽了：请到 系统设置→应用→(本应用)→通知 里打开")
            }
        } catch (t: Throwable) {
            report("显示通知失败：" + t.javaClass.simpleName + " " + (t.message ?: ""))
        }
    }

    private fun hasNotifyPermission(): Boolean =
        nm?.areNotificationsEnabled() == true &&
            (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    /**
     * 把失败原因回吐给网页显示（只报一次，避免刷屏）。
     * 通知没出现是用户最难自行排查的问题，所以这里要把原因说清楚。
     */
    private fun report(msg: String) {
        if (warned) return
        warned = true
        TimerBus.toPage("warn " + msg.replace(Regex("[\\r\\n'\"\\\\]"), " ").take(140))
    }

    private fun buildOngoing(): Notification {
        val left = leftSeconds()
        val passed = (total - left).coerceIn(0, total)
        // 剩余时间放在正文里，不依赖系统 chronometer：
        // 部分 ROM（含 MIUI 系）的自定义通知布局不渲染倒计时，正文文本则一定显示
        val b = NotificationCompat.Builder(this, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentIntent(openAppIntent(0))
            .setColor(color)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(running) // 进行中不可划掉；暂停后允许划掉
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(total, passed, false)
            .addAction(0, if (running) "暂停" else "继续", serviceIntent(1, ACTION_TOGGLE))
            .addAction(0, "停止", serviceIntent(2, ACTION_STOP))

        if (running) {
            b.setContentTitle("$label · 剩余 ${mmss(left)}")
            b.setContentText("预计 ${clockOf(System.currentTimeMillis() + left * 1000L)} 结束")
        } else {
            b.setContentTitle("$label · 已暂停")
            b.setContentText("剩余 ${mmss(left)}，点「继续」接着计时")
        }
        return b.build()
    }

    private fun buildDone(): Notification =
        NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("$label 结束")
            .setContentText(
                if (label.contains("专注")) "专注完成！休息一下吧 ☕"
                else "休息结束，准备开始新的专注 🌟",
            )
            .setContentIntent(openAppIntent(3))
            .setColor(color)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(!sound)
            .build()

    private fun openAppIntent(code: Int): PendingIntent = PendingIntent.getActivity(
        this,
        code,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceIntent(code: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            code,
            Intent(this, PomodoroService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannels() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        LEGACY_CHANNELS.forEach { runCatching { mgr.deleteNotificationChannel(it) } }
        mgr.createNotificationChannel(
            // 刻意用 DEFAULT 而不是 LOW：MIUI 等 ROM 会把低重要性通知折叠进
            // 「不重要通知」，用户就看不到倒计时了。用 silent 通道做到同样不打扰。
            NotificationChannel(CH_ONGOING, "计时进行中", NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    description = "常驻显示当前番茄钟的剩余时间"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CH_DONE, "计时结束提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "一轮专注 / 休息结束时的提醒"
                // 声音由 Tones 播（用用户在设置里选的音色），通道保持无声只负责震动
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            },
        )
    }

    private fun acquireWake() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pomodoro:timer").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseWake() {
        wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        wakeLock = null
    }
}

/** 服务 → 网页 的唯一通道：把通知栏按钮转成指令交给网页执行 */
object TimerBus {

    @Volatile private var sink: ((String) -> Unit)? = null

    fun attach(f: (String) -> Unit) {
        sink = f
    }

    fun detach() {
        sink = null
    }

    /** @return 网页当前是否在线（不在线时调用方需要自己兜底） */
    fun toPage(cmd: String): Boolean {
        val f = sink ?: return false
        Handler(Looper.getMainLooper()).post { f(cmd) }
        return true
    }
}

private fun parseColor(hex: String?): Int? =
    if (hex.isNullOrBlank()) null
    else runCatching { Color.parseColor(hex.trim()) }.getOrNull()

private fun mmss(sec: Int): String =
    String.format("%02d:%02d", sec / 60, sec % 60)

/** 把一个 epoch 毫秒格式化成 HH:mm */
private fun clockOf(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

package com.miuix.pomodoro

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File

/**
 * 提示音预设。
 *
 * ⚠️ id 列表必须与 index.html 里的 `TONES` 一一对应：
 * 网页版用 WebAudio 合成同样的音色，安卓端用 [R.raw] 里的 WAV（由
 * `android/tools/gen_tones.py` 生成），两边名字与性格保持一致。
 *
 * 用 SoundPool 而不是 MediaPlayer：这些都是 1~2 秒的短音，SoundPool 延迟更低，
 * 且可以预加载一次后反复播放。
 */
object Tones {

    /** 可选音色；"none" 表示静音，不在这里 */
    val IDS = listOf("chime", "bell", "marimba", "wood", "beep")

    private val RAW = listOf(
        R.raw.tone_chime,
        R.raw.tone_bell,
        R.raw.tone_marimba,
        R.raw.tone_wood,
        R.raw.tone_beep,
    )

    private var pool: SoundPool? = null
    private val soundIds = HashMap<Int, Int>() // resId -> soundId
    private var customPlayer: MediaPlayer? = null
    private var customSlot = ""
    private val handler = Handler(Looper.getMainLooper())
    private var fadeTask: Runnable? = null

    /** 自定义音频最长播这么久：上传的很可能是一整首歌，当作提示音就太长了 */
    private const val CUSTOM_MAX_MS = 10_000L
    private const val CUSTOM_FADE_MS = 400L

    fun resId(tone: String): Int? = when (tone) {
        "chime" -> R.raw.tone_chime
        "bell" -> R.raw.tone_bell
        "marimba" -> R.raw.tone_marimba
        "wood" -> R.raw.tone_wood
        "beep" -> R.raw.tone_beep
        else -> null
    }

    /** 提前把音频解码进内存，避免第一次播放时有延迟；应用启动时调一次即可 */
    @Synchronized
    fun preload(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // 跟随系统的「通知」音量，静音模式下不打扰（与网页版行为一致）
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        RAW.forEach { soundIds[it] = p.load(app, it, 1) }
        pool = p
    }

    /**
     * 播放指定音色。slot 取 "start" / "end"，自定义音色是按槽位分开存的。
     * tone 为 "none" 或未知值时静默返回。
     */
    fun play(context: Context, tone: String, slot: String = "start") {
        if (tone == CUSTOM) {
            playCustom(context, slot)
            return
        }
        val res = resId(tone) ?: return
        preload(context)
        val sid = soundIds[res] ?: return
        runCatching { pool?.play(sid, 1f, 1f, 1, 0, 1f) }
    }

    /** 自定义音色：从应用私有目录读，用 MediaPlayer（不把整首解码进内存，长音频也不怕） */
    private fun playCustom(context: Context, slot: String) {
        val f = customFile(context, slot) ?: return
        runCatching {
            // 同一槽位再次播放先停上一次的
            cancelFade()
            if (customSlot == slot) customPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
            customPlayer = null
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(f.absolutePath)
            mp.prepare()
            mp.setOnCompletionListener { p ->
                cancelFade()
                runCatching { p.release() }
            }
            mp.start()
            customPlayer = mp
            customSlot = slot
            // 比内置音长得多，到点自动渐弱停下，否则一首歌会一直放
            val task = Runnable { fadeOutAndStop(mp) }
            fadeTask = task
            handler.postDelayed(task, CUSTOM_MAX_MS - CUSTOM_FADE_MS)
        }
    }

    private fun cancelFade() {
        fadeTask?.let { handler.removeCallbacks(it) }
        fadeTask = null
    }

    /** 渐弱再停，避免"啪"地截断 */
    private fun fadeOutAndStop(mp: MediaPlayer) {
        var elapsed = 0L
        val step = 60L
        val task = object : Runnable {
            override fun run() {
                elapsed += step
                val v = (1f - elapsed.toFloat() / CUSTOM_FADE_MS).coerceIn(0f, 1f)
                val ok = runCatching { mp.setVolume(v, v) }.isSuccess
                if (ok && v > 0f) {
                    handler.postDelayed(this, step)
                } else {
                    runCatching { mp.stop() }
                    runCatching { mp.release() }
                    if (customPlayer === mp) customPlayer = null
                    fadeTask = null
                }
            }
        }
        handler.post(task)
    }

    // ---------- 用户上传的自定义提示音 ----------

    const val CUSTOM = "custom"

    private fun slotDir(context: Context): File =
        File(context.filesDir, "tones").apply { mkdirs() }

    /** 某个槽位已上传的文件（名形如 start.mp3 / end.wav） */
    fun customFile(context: Context, slot: String): File? =
        slotDir(context).listFiles()?.firstOrNull { it.name.startsWith("$slot.") }

    /** 把用户选的音频复制进私有目录（覆盖同槽位的旧文件）；返回文件名，失败返回 null */
    fun saveCustom(context: Context, slot: String, uri: Uri): String? {
        val dir = slotDir(context)
        dir.listFiles()?.filter { it.name.startsWith("$slot.") }?.forEach { it.delete() }
        val ext = (displayName(context, uri)?.substringAfterLast('.', "mp3") ?: "mp3")
            .lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { "mp3" }
        val target = File(dir, "$slot.$ext")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return null
            target.name
        }.getOrNull()
    }

    /** 取用户选中文件的显示名（用于取扩展名 + 在界面上显示） */
    fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
}

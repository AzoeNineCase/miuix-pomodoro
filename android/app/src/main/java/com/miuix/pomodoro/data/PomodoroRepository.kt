package com.miuix.pomodoro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * 基于 SharedPreferences 的本地存储。
 * 对应网页版里的 localStorage：设置、按天统计、待办、长休息周期进度。
 */
class PomodoroRepository(context: Context) {

    private val prefs = context.getSharedPreferences("pomodoro.miuix", Context.MODE_PRIVATE)

    /* ------------------------------ 设置 ------------------------------ */

    fun loadSettings(): Settings = Settings(
        focusMinutes = prefs.getInt("focusMinutes", 25),
        shortMinutes = prefs.getInt("shortMinutes", 5),
        longMinutes = prefs.getInt("longMinutes", 15),
        sessionsBeforeLong = prefs.getInt("sessionsBeforeLong", 4),
        autoStart = prefs.getBoolean("autoStart", false),
        sound = prefs.getBoolean("sound", true),
        notify = prefs.getBoolean("notify", false),
        miniTimer = prefs.getBoolean("miniTimer", false),
        accent = prefs.getInt("accent", DEFAULT_ACCENT),
        themeMode = ThemeMode.entries.getOrElse(prefs.getInt("themeMode", 0)) { ThemeMode.System },
        bgMode = BgMode.entries.getOrElse(prefs.getInt("bgMode", 0)) { BgMode.Gradient },
        bgImage = prefs.getString("bgImage", null),
        bgDim = prefs.getFloat("bgDim", 0.35f),
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putInt("focusMinutes", s.focusMinutes)
            .putInt("shortMinutes", s.shortMinutes)
            .putInt("longMinutes", s.longMinutes)
            .putInt("sessionsBeforeLong", s.sessionsBeforeLong)
            .putBoolean("autoStart", s.autoStart)
            .putBoolean("sound", s.sound)
            .putBoolean("notify", s.notify)
            .putBoolean("miniTimer", s.miniTimer)
            .putInt("accent", s.accent)
            .putInt("themeMode", s.themeMode.ordinal)
            .putInt("bgMode", s.bgMode.ordinal)
            .putString("bgImage", s.bgImage)
            .putFloat("bgDim", s.bgDim)
            .apply()
    }

    /* ------------------------------ 统计 ------------------------------ */

    fun todayKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun keyOf(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    fun loadStats(): MutableMap<String, DayStat> {
        val out = mutableMapOf<String, DayStat>()
        val raw = prefs.getString("stats", null) ?: return out
        runCatching {
            val root = JSONObject(raw)
            root.keys().forEach { key ->
                val o = root.optJSONObject(key) ?: return@forEach
                out[key] = DayStat(
                    sessions = o.optInt("sessions", 0),
                    focusMin = o.optInt("focusMin", 0),
                    best = o.optInt("best", 0),
                )
            }
        }
        return out
    }

    fun saveStats(stats: Map<String, DayStat>) {
        val root = JSONObject()
        stats.forEach { (key, value) ->
            root.put(
                key,
                JSONObject()
                    .put("sessions", value.sessions)
                    .put("focusMin", value.focusMin)
                    .put("best", value.best),
            )
        }
        prefs.edit().putString("stats", root.toString()).apply()
    }

    /** 最近 7 天（含今天）的番茄数，用于周分布图 */
    fun lastWeekSessions(): List<Pair<String, Int>> {
        val stats = loadStats()
        val labels = listOf("日", "一", "二", "三", "四", "五", "六")
        val cal = Calendar.getInstance()
        val out = mutableListOf<Pair<String, Int>>()
        for (i in 6 downTo 0) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            out += labels[c.get(Calendar.DAY_OF_WEEK) - 1] to (stats[keyOf(c.timeInMillis)]?.sessions ?: 0)
        }
        return out
    }

    /* --------------------------- 长休息周期 --------------------------- */

    fun loadCycle(): Int = prefs.getInt("cycle", 0).coerceAtLeast(0)

    fun saveCycle(value: Int) = prefs.edit().putInt("cycle", value).apply()

    /* ------------------------------ 待办 ------------------------------ */

    fun loadTodos(): MutableList<TodoItem> {
        val raw = prefs.getString("todos", null) ?: return mutableListOf()
        val out = mutableListOf<TodoItem>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out += TodoItem(
                    id = o.optLong("id", i.toLong()),
                    text = o.optString("text"),
                    done = o.optBoolean("done", false),
                )
            }
        }
        return out
    }

    fun saveTodos(list: List<TodoItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("text", item.text)
                    .put("done", item.done),
            )
        }
        prefs.edit().putString("todos", arr.toString()).apply()
    }

    companion object {
        /** 便于在界面层生成导出文件名 */
        fun todayKeyStatic(): String {
            val c = Calendar.getInstance()
            return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
        }
    }
}

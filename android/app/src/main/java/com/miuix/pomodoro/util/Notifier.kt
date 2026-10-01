package com.miuix.pomodoro.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.miuix.pomodoro.MainActivity
import com.miuix.pomodoro.R

object Notifier {

    // ⚠️ 通道的重要性 / 提示音创建后就不能改：调整这两项必须连带升 id 版本（旧通道顺手删掉）
    private const val CHANNEL_ID = "pomodoro.reminder.v2"
    private val LEGACY_CHANNELS = listOf("pomodoro.reminder")
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        LEGACY_CHANNELS.forEach { runCatching { manager.deleteNotificationChannel(it) } }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "番茄钟提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "专注或休息结束时提醒你"
            // 声音由 Chime 播（与网页版/前台服务一致），通道保持无声只负责震动；
            // 否则会叠成两声，而且「提示音」开关关不掉通道自带的系统提示音
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notify(context: Context, title: String, text: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

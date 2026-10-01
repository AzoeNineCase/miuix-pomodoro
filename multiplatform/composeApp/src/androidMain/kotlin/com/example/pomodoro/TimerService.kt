package com.example.pomodoro

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "番茄钟"
                val text = intent.getStringExtra(EXTRA_TEXT) ?: "计时中..."
                startForeground(NOTIFICATION_ID, createNotification(title, text))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.pomodoro.START_TIMER"
        const val ACTION_STOP = "com.example.pomodoro.STOP_TIMER"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val NOTIFICATION_ID = 1002

        fun start(context: Context, title: String, text: String) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION") context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}

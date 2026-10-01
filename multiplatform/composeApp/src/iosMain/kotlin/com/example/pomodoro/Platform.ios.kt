package com.example.pomodoro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication

/** iOS 设置存储：NSUserDefaults（对应 Android 的 SharedPreferences） */
class IosSettingsStorage : SettingsStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun saveInt(key: String, value: Int) { defaults.setInteger(value.toLong(), key) }
    override fun loadInt(key: String, default: Int): Int =
        if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key).toInt()

    override fun saveBoolean(key: String, value: Boolean) { defaults.setBool(value, key) }
    override fun loadBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    override fun saveString(key: String, value: String) { defaults.setObject(value, key) }
    override fun loadString(key: String, default: String): String =
        defaults.stringForKey(key) ?: default
}

@OptIn(ExperimentalForeignApi::class)
actual fun platformPlaySound() {
    // 1104 = Tock：系统短提示音，跟随静音键
    AudioServicesPlaySystemSound(1104u)
}

actual fun startForegroundService(title: String, text: String) { /* iOS 无前台服务概念 */ }

actual fun stopForegroundService() { /* iOS: no-op */ }

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    LaunchedEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }
}

@Composable
actual fun BlurBackdrop(show: Boolean) {
    if (!show) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
}

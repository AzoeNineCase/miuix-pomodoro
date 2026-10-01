package com.example.pomodoro

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** iOS 入口：由 iosApp 的 SwiftUI 壳调用（导出为 MainViewControllerKt.MainViewController()） */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val storage = remember { IosSettingsStorage() }
    val state = remember { State(storage).also { it.checkDailyReset() } }
    App(state, isSystemInDarkTheme())
}

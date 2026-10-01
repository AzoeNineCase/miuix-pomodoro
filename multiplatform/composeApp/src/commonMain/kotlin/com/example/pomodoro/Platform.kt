package com.example.pomodoro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect fun platformPlaySound()

@Composable
expect fun KeepScreenOn(enabled: Boolean)

@Composable
expect fun BlurBackdrop(show: Boolean)

expect fun startForegroundService(title: String, text: String)
expect fun stopForegroundService()

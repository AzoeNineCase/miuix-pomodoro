package com.miuix.pomodoro.util

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 用系统提示音合成简单和弦，替代网页版的 WebAudio 提示音 */
object Chime {

    private var tone: ToneGenerator? = null

    private fun generator(): ToneGenerator? {
        tone?.let { return it }
        return runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80).also { tone = it }
        }.getOrNull()
    }


    /** 开始的上升双音 */
    fun start(scope: CoroutineScope) {
        val g = generator() ?: return
        scope.launch {
            // delay 放在 runCatching 外面：协程被取消时不能吞掉 CancellationException，
            // 否则会越过取消点继续响第二声
            runCatching { g.startTone(ToneGenerator.TONE_PROP_ACK, 120) }
            delay(130)
            runCatching { g.startTone(ToneGenerator.TONE_PROP_BEEP, 140) }
        }
    }

    /** 结束提示：专注完成用上行音，休息结束用下行音 */
    fun finish(scope: CoroutineScope, isFocus: Boolean) {
        val g = generator() ?: return
        scope.launch {
            val sequence = if (isFocus) {
                listOf(ToneGenerator.TONE_PROP_BEEP to 140, ToneGenerator.TONE_PROP_ACK to 140, ToneGenerator.TONE_PROP_BEEP2 to 180)
            } else {
                listOf(ToneGenerator.TONE_PROP_ACK to 140, ToneGenerator.TONE_PROP_BEEP to 180)
            }
            sequence.forEachIndexed { index, (sound, duration) ->
                runCatching { g.startTone(sound, duration) }
                if (index != sequence.lastIndex) delay(duration.toLong() + 40)
            }
        }
    }

    fun release() {
        runCatching { tone?.release() }
        tone = null
    }
}

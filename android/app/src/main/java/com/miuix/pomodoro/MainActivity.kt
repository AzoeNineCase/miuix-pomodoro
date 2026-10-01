package com.miuix.pomodoro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.miuix.pomodoro.ui.AppRoot

/**
 * 两套实现，二选一：
 *
 * - `true` ：直接运行仓库根目录的 index.html（构建时同步进 assets），
 *             界面、字体、毛玻璃、动画曲线与网页版 **完全一致**。
 * - `false`：原生 Miuix(Compose) 实现，风格接近网页版，但组件度量/字体/模糊
 *             与网页 CSS 存在固有差异，无法做到像素级一致。
 */
private const val RUN_WEB_VERSION = true

class MainActivity : ComponentActivity() {

    private val viewModel: PomodoroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 提示音预加载：避免第一次响的时候有解码延迟
        Tones.preload(this)
        // 网页里可能弹出全屏浮层（如「关于」）：这种时候系统返回键应当先关浮层，
        // 而不是直接把 Activity 结束掉。开关状态由网页通过 MiuixBridge.setOverlay() 告知。
        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                UiBackGate.closeOverlay?.invoke()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        UiBackGate.bind { enabled -> backCallback.isEnabled = enabled }
        setContent {
            if (RUN_WEB_VERSION) {
                WebAppScreen()
            } else {
                AppRoot(viewModel)
            }
        }
    }
}

/**
 * 系统返回键的让位门闸。
 *
 * 网页里会出现全屏浮层（目前是「关于」页）。这时系统返回键应该先关掉浮层，
 * 而不是结束 Activity；网页侧用 `MiuixBridge.setOverlay(true/false)` 声明状态，
 * [WebAppScreen] 注入 [closeOverlay] 来真正关掉它。
 */
object UiBackGate {

    @Volatile private var overlayOpen = false
    @Volatile private var applyEnabled: ((Boolean) -> Unit)? = null

    /** 由 WebAppScreen 注入：关掉网页里的全屏浮层 */
    @Volatile var closeOverlay: (() -> Unit)? = null

    /** MainActivity 注册返回键回调后调用，把当前状态同步过去 */
    fun bind(enable: (Boolean) -> Unit) {
        applyEnabled = enable
        enable(overlayOpen)
    }

    /** 必须在主线程调用 */
    fun setOverlayOpen(open: Boolean) {
        overlayOpen = open
        applyEnabled?.invoke(open)
    }
}

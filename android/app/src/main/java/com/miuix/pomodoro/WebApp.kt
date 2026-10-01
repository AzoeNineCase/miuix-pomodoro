package com.miuix.pomodoro

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.io.File
import java.io.IOException

/**
 * 用应用内的本地来源承载 assets 里的 index.html。
 * 相比 file:// ，它给页面一个正常的 https 源，localStorage（设置/统计/待办）才会稳定持久化。
 */
private const val APP_ORIGIN = "https://appassets.androidplatform.net/"
private const val PAGE_URL = APP_ORIGIN + "index.html"

/**
 * 直接运行网页版：与 index.html 在浏览器里的表现完全一致
 * （同一份 HTML/CSS/JS，同样的字体、毛玻璃、动画曲线）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebAppScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    // 页面自己的主题：可能和系统不同（系统浅色 + 页面里选了深色/极光）
    val systemDark = isSystemInDarkTheme()
    var pageDark by remember { mutableStateOf(systemDark) }
    val backdrop = if (pageDark) Color(0xFF1A1A1C) else Color(0xFFF4F5F7)

    // 沉浸式：窗口铺满整屏，系统栏图标颜色跟随「页面」主题而不是系统主题
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !pageDark
        controller.isAppearanceLightNavigationBars = !pageDark
    }

    // 状态栏 / 手势区高度（dp 数值等于 CSS px），通过 --safe-* 交给页面自己留白
    val topCss = with(density) { WindowInsets.systemBars.getTop(this).toDp().value }
    val bottomCss = with(density) { WindowInsets.systemBars.getBottom(this).toDp().value }

    // 供 WebViewClient 在页面加载完成时读取当前值
    val insetsState = remember { mutableStateOf(0f to 0f) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(topCss, bottomCss) {
        insetsState.value = topCss to bottomCss
        webViewRef.value?.evaluateJavascript(injectedScript(topCss, bottomCss), null)
    }

    /** 网页把导出的 JSON 交给原生保存 */
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    // 网页里的「上传本地图片」用的是 <input type="file">，需要原生把选择结果回传
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri == null) null else arrayOf(uri))
        fileCallback = null
    }

    // 通知权限（Android 13+）：网页的「桌面通知」开关与开始计时都会走到这里
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        webViewRef.value?.evaluateJavascript(
            "window.__miuixNotifyPermission&&window.__miuixNotifyPermission($granted)",
            null,
        )
    }
    val askNotification: () -> Unit = {
        val need = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (need) {
            // 系统只会弹两次，之后 launch 会直接失败回调，不会骚扰用户；
            // 这很关键：拿不到权限时前台服务通知根本不会显示在通知栏。
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // 权限已经有时什么都不做：这里**不能**回吐 __miuixNotifyPermission(true)，
        // 网页端会把它当成用户意愿把「桌面通知」开关重新点亮，
        // 结果每次开始计时都会覆盖用户手动关掉的设置。
    }

    // 自定义提示音：网页点「上传」→ 系统选择音频 → 复制进私有目录 → 回报给网页
    val pendingToneSlot = remember { mutableStateOf("") }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val slot = pendingToneSlot.value
        pendingToneSlot.value = ""
        if (slot.isEmpty()) return@rememberLauncherForActivityResult
        val name = if (uri == null) null else Tones.saveCustom(context, slot, uri)
        val msg = when {
            uri == null -> ""
            name == null -> "读取音频失败"
            else -> Tones.displayName(context, uri) ?: name
        }
        webViewRef.value?.evaluateJavascript(
            "window.__miuixToneUploaded&&window.__miuixToneUploaded(" +
                jsString(slot) + "," + (name != null) + "," + jsString(msg) + ")",
            null,
        )
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(backdrop.toArgb())
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    // 字体走本地：网页版的图标字体来自 fonts.googleapis.com，
                    // 该域名在部分网络下不可达，一旦失败所有图标会退化成文字。
                    // 这里用的是与 Google 完全相同的字体文件（静态实例，页面只用一组轴值）。
                    if (url.startsWith("https://fonts.googleapis.com/")) {
                        return openAsset(context, "fonts/fonts.css")
                    }
                    if (url.startsWith("https://fonts.gstatic.com/")) {
                        return openAsset(context, "fonts/" + url.substringAfterLast('/'))
                    }
                    if (!url.startsWith(APP_ORIGIN)) return null
                    val assetPath = url.removePrefix(APP_ORIGIN)
                        .substringBefore('?')
                        .substringBefore('#')
                    return openAsset(context, assetPath)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 页面首帧后再注入一次，保证 --safe-* 一定生效
                    val (top, bottom) = insetsState.value
                    view?.evaluateJavascript(injectedScript(top, bottom), null)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    filePicker.launch("image/*")
                    return true
                }
            }

            // 网页版「导出数据」是 Blob + a.download，WebView 读不到 blob，
            // 这里把 blob 内容取回来交给原生写进「下载」目录，功能与网页版一致
            setDownloadListener { url, _, _, _, _ ->
                if (url.startsWith("blob:")) {
                    evaluateJavascript(
                        "(async()=>{const r=await fetch('" + url + "');const t=await r.text();" +
                            "MiuixBridge.saveText('pomodoro-export.json',t);})()",
                        null,
                    )
                }
            }

            addJavascriptInterface(
                SaveBridge(
                    context,
                    onPageTheme = { dark -> pageDark = dark },
                    onRequestNotification = askNotification,
                    onPickTone = { slot ->
                        pendingToneSlot.value = slot
                        audioPicker.launch("audio/*")
                    },
                    onTimerState = { state, label, remaining, total, sound, notify, color, tone ->
                        PomodoroService.sync(
                            context, state, label, remaining, total, sound, notify, color, tone,
                        )
                        // 开始计时时确认通知权限：拿不到的话常驻通知栏不会显示
                        if (state == "running") askNotification()
                    },
                ),
                "MiuixBridge",
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                textZoom = 100 // 不跟随系统字号，保证与网页版排版一致
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = true
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            webViewRef.value = this
            loadUrl(PAGE_URL)
        }
    }

    // 通知栏上的按钮（继续/暂停、停止、跳过）→ 网页；同时接管系统返回键对浮层的处理
    DisposableEffect(Unit) {
        TimerBus.attach { cmd ->
            webViewRef.value?.evaluateJavascript(
                "window.__miuixTimerCommand&&window.__miuixTimerCommand('$cmd')",
                null,
            )
        }
        UiBackGate.closeOverlay = {
            webViewRef.value?.evaluateJavascript(
                "window.__miuixCloseOverlay&&window.__miuixCloseOverlay()",
                null,
            )
        }
        onDispose {
            TimerBus.detach()
            UiBackGate.closeOverlay = null
            UiBackGate.setOverlayOpen(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(backdrop)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
}

/**
 * 注入给页面的脚本，一次注入做两件事：
 *
 * 1. 安全区变量：网页版按 env(safe-area-inset-*) 留白，但 WebView 里的 env()
 *    只反映屏幕挖孔、不含状态栏高度，所以由原生把真实高度写进 --safe-top / --safe-bottom；
 *    同时监听 <html data-theme> 回报原生，让系统栏图标跟随页面主题。
 * 2. 触摸设备的粘滞 hover：手指点过之后浏览器会把 :hover 一直留在那个元素上
 *    （按钮看着像一直被选中）。这里在「轻点」结束后让浏览器重算一次悬停目标，
 *    外观即恢复到未悬停状态；滚动、拖拽、长按不做处理，避免干扰手势。
 */
private fun injectedScript(top: Float, bottom: Float): String =
    "(function(){var e=document.documentElement;" +
        "e.style.setProperty('--safe-top','" + top + "px');" +
        "e.style.setProperty('--safe-bottom','" + bottom + "px');" +
        "if(window.__miuixHooks)return;window.__miuixHooks=1;" +
        // 主题变化回报原生
        "var r=function(){try{MiuixBridge.onThemeChanged(e.getAttribute('data-theme')||'light');}catch(err){}};" +
        "new MutationObserver(r).observe(e,{attributes:true,attributeFilter:['data-theme']});r();" +
        // 轻点后清除粘滞 hover
        "var sx=0,sy=0,st=0,moved=0;" +
        "document.addEventListener('touchstart',function(ev){var t=ev.changedTouches[0];" +
        "if(!t)return;sx=t.clientX;sy=t.clientY;st=Date.now();moved=0;},true);" +
        "document.addEventListener('touchmove',function(ev){var t=ev.changedTouches[0];" +
        "if(!t)return;if(Math.abs(t.clientX-sx)+Math.abs(t.clientY-sy)>12)moved=1;},true);" +
        "document.addEventListener('touchend',function(){" +
        "if(moved||Date.now()-st>600)return;" +
        "setTimeout(function(){var b=document.body;if(!b)return;" +
        "b.style.pointerEvents='none';void b.offsetHeight;b.style.pointerEvents='';},0);},true);" +
        "})()"

/**
 * 网页 ↔ 原生 的桥，注入为网页里的 window.MiuixBridge。
 *
 * - `onThemeChanged`：网页切主题 → 同步系统栏图标颜色；
 * - `saveText`：网页导出 JSON → 原生写进「下载」目录；
 * - `requestNotification`：网页的「桌面通知」开关 → 申请通知权限；
 * - `playSound`：网页让原生播提示音（锁屏后网页自己发不出声）；
 * - `timerState`：网页把计时状态交给前台服务（锁屏后继续计时 + 常驻通知栏）。
 */
class SaveBridge(
    private val context: Context,
    private val onPageTheme: (Boolean) -> Unit = {},
    private val onRequestNotification: (() -> Unit)? = null,
    private val onPickTone: ((String) -> Unit)? = null,
    private val onTimerState: (
        (String, String, Int, Int, Boolean, Boolean, String, String) -> Unit
    )? = null,
) {
    /** 页面切换主题时回调（data-theme = light / dark / aurora），用于同步系统栏图标颜色 */
    @JavascriptInterface
    fun onThemeChanged(theme: String) {
        val dark = theme == "dark" || theme == "aurora"
        Handler(Looper.getMainLooper()).post { onPageTheme(dark) }
    }

    /**
     * 网页的「桌面通知」开关：申请通知权限。
     * 权限弹窗是异步的，结果之后由原生回吐给 `window.__miuixNotifyPermission`。
     */
    @JavascriptInterface
    fun requestNotification(): Boolean {
        val ask = onRequestNotification ?: return true
        Handler(Looper.getMainLooper()).post { ask() }
        return true
    }

    /** 网页侧的「试听 / 开始提示音」：声音必须由原生播，锁屏后网页放不出声 */
    @JavascriptInterface
    fun playSound(tone: String, slot: String) {
        Handler(Looper.getMainLooper()).post { Tones.play(context, tone, slot) }
    }

    /** 网页点「上传」：弹系统的音频选择器（结果由 __miuixToneUploaded 回传） */
    @JavascriptInterface
    fun pickCustomTone(slot: String) {
        val pick = onPickTone ?: return
        Handler(Looper.getMainLooper()).post { pick(slot) }
    }

    /** 「关于」页要显示的真实版本号 */
    @JavascriptInterface
    fun appVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        "v${info.versionName} ($code)"
    }.getOrDefault("")

    /**
     * 网页告知是否有全屏浮层打开。
     * 有浮层时系统返回键应该先关浮层，而不是直接退出应用。
     */
    @JavascriptInterface
    fun setOverlay(open: Boolean) {
        Handler(Looper.getMainLooper()).post { UiBackGate.setOverlayOpen(open) }
    }

    /**
     * 「关于」页的外部链接：用系统浏览器打开。
     * 网页里点链接若直接在 WebView 内导航，用户会迷失在网站里回不来，
     * 所以统一交给原生起 ACTION_VIEW（只允许 http/https，避免任意 intent）。
     */
    @JavascriptInterface
    fun openUrl(url: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        Handler(Looper.getMainLooper()).post {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /**
     * 网页把计时状态同步过来，state 取 running / paused / idle。
     * JavascriptInterface 的方法跑在 WebView 的私有线程上，所以统一抛回主线程处理。
     */
    @JavascriptInterface
    fun timerState(
        state: String,
        label: String,
        remaining: Int,
        total: Int,
        sound: Boolean,
        notify: Boolean,
        color: String,
        endTone: String,
    ) {
        val push = onTimerState ?: return
        Handler(Looper.getMainLooper()).post {
            push(state, label, remaining, total, sound, notify, color, endTone)
        }
    }

    @JavascriptInterface
    fun saveText(name: String, text: String) {
        val ok = runCatching { write(name, text) }.getOrDefault(false)
        if (!ok) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun write(name: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return true
        }
        // Android 10 以下写到应用专属目录，免去存储权限
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
        File(dir, name).writeText(text)
        return true
    }
}

/** 把任意字符串安全地嵌进 JS 单引号字符串里（供 evaluateJavascript 用） */
private fun jsString(s: String): String =
    "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ") + "'"

/** 从 assets 读取资源，按扩展名给出正确的 MIME（字体是二进制，encoding 必须为 null） */
private fun openAsset(context: Context, path: String): WebResourceResponse? = try {
    val mime = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".js") -> "application/javascript"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".woff") -> "font/woff"
        else -> "application/octet-stream"
    }
    val encoding = if (mime.startsWith("text/") || mime.contains("javascript")) "utf-8" else null
    WebResourceResponse(mime, encoding, context.assets.open(path))
} catch (e: IOException) {
    null
}

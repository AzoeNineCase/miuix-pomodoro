package com.example.pomodoro

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/* ============================================================
 * 背景图加载 —— 对应网页 #bgImage / applyBackground()：
 *   · http(s):// 链接与 data:image/…;base64 data URL 均可；
 *   · 预加载成功后才显示（失败移除 data-bg-active 并提示加载失败）；
 *   · 下载 + 解码因平台而异，由三端各自实现 actual。
 * ============================================================ */

/** 下载（或解析 data URL）并解码图片；失败返回 null。三端各自实现 */
expect suspend fun loadImageBitmap(url: String): ImageBitmap?

/** 与网页 bgApplyBtn 的校验一致：仅接受 http(s):// 与 data:image/ 前缀 */
internal fun isSupportedImageUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true) ||
        url.startsWith("data:image/", ignoreCase = true)

/** data:image/…;base64,… → 字节；非 data 图片 URL / 载荷非法时返回 null */
@OptIn(ExperimentalEncodingApi::class)
internal fun decodeDataUrlBytes(url: String): ByteArray? {
    if (!url.startsWith("data:image/", ignoreCase = true)) return null
    val comma = url.indexOf(',')
    if (comma < 0) return null
    if (!url.substring(0, comma).contains(";base64", ignoreCase = true)) return null
    return try {
        Base64.decode(url.substring(comma + 1))
    } catch (_: Exception) {
        null
    }
}

# 番茄钟 · Android 版

同一个网页版番茄钟（`../index.html`）的 Android 壳。工程里有两套可切换的实现：

| 开关                                 | 实现                                                              | 与网页版的一致性                                                 |
| ------------------------------------ | ----------------------------------------------------------------- | ---------------------------------------------------------------- |
| `RUN_WEB_VERSION = true`（**默认**） | `WebApp.kt`：把 `index.html` 原样装进 App 里运行                  | **完全一致** —— 同一份 HTML/CSS/JS，同样的字体、毛玻璃、弹簧曲线 |
| `RUN_WEB_VERSION = false`            | `ui/`：用 **Miuix**（小米 HyperOS 风格的 Compose 组件库）原生重写 | 风格接近，但做不到像素级一致                                     |

切换位置：`app/src/main/java/com/miuix/pomodoro/MainActivity.kt`。

> 为什么原生版做不到"一模一样"：网页版的卡片内边距（26px）、圆角（20px）、
> `backdrop-filter` 真实背景模糊、MiSans/Inter/Material Symbols 字体、
> `cubic-bezier(0.34, 1.56, 0.64, 1)` 弹簧曲线等，在 Miuix 组件里都没有等价物；
> 逐条对齐也只能接近。要严格一致，只能让 App 直接运行这份网页。

## 网页版模式（默认）

- `../index.html` 由 Gradle 任务 `syncWebAssets` 在构建时同步到 `app/src/main/assets/index.html`，
  **不存在第二份副本**；改完网页重新构建即可生效。
- 页面经 `https://appassets.androidplatform.net/index.html` 加载（`shouldInterceptRequest` 从 assets 读取），
  给它一个正常的 https 源，`localStorage`（设置 / 统计 / 待办）才能稳定持久化。
- 必要的原生桥接都在 `WebApp.kt` 里，**不改动网页本身**：
  - `<input type="file">` → `onShowFileChooser`，让「上传本地图片」可用；
  - Blob + `a.download` 导出 → `setDownloadListener` + `fetch(blob)` → 写入系统「下载」目录；
  - `INTERNET` 权限：Google Fonts 的 Inter / Material Symbols，以及背景图 API；
  - `textZoom = 100`：不跟随系统字号，保持与网页版排版一致。
  - 沉浸式：窗口铺满整屏，原生把状态栏 / 手势区高度写进页面的 `--safe-top` / `--safe-bottom`
    （WebView 的 `env(safe-area-inset-*)` 只反映屏幕挖孔、不含状态栏高度，直接铺满会被状态栏压住标题）；
    并通过 MutationObserver 监听页面 `<html data-theme>`，让状态栏图标跟随**页面**主题而不是系统主题。
- 桌面通知：走**原生**实现——网页通过桥申请通知权限（13+）、由前台服务发通知；网页自带的 Web Notification
  降级提示只会在纯浏览器环境（没有 `MiuixBridge`）出现。

## 原生能力（默认的网页版模式同样具备）

App 并不是“纯壳”——下面这些能力由原生实现，通过 `MiuixBridge` 与网页协作：

- **锁屏继续计时**：`PomodoroService`（前台服务 + `PARTIAL_WAKE_LOCK`）用单调时钟自己走完这一轮；
  通知栏剩余时间直接写在正文里，不依赖系统 chronometer 的兼容性。
- **通知栏按钮**：暂停 / 停止 / 跳过 会把指令回传网页执行（网页是唯一事实来源）；网页失联时服务自行收尾。
- **提示音**：内置 5 种（SoundPool 预加载）+ 用户上传的音频（MediaPlayer，限时 10 秒渐弱，
  开始/结束分槽位存在 `filesDir/tones/`）；结束音由服务播放，息屏也能响。
- **文件与链接**：本地背景图 / 自定义提示音选择（`GetContent`）、导出 JSON 写入「下载」、
  关于页外链改走系统浏览器（`openUrl`，避免在 WebView 内导航后回不来）。
- **沉浸式**：系统栏高度注入 `--safe-top` / `--safe-bottom`，状态栏图标跟随页面主题。

## 技术栈（原生 Miuix 实现）

| 项                              | 版本 / 说明                                                                                               |
| ------------------------------- | --------------------------------------------------------------------------------------------------------- |
| UI 框架                         | [Miuix](https://github.com/compose-miuix-ui/miuix) `top.yukonga.miuix.kmp:miuix-ui:0.9.3` + `miuix-icons` |
| Compose                         | androidx.compose **1.11.2**（与 Miuix 0.9.3 在 Android 侧的映射版本一致）                                 |
| Kotlin                          | 2.4.0（与 Miuix 编译版本一致，否则元数据不兼容）                                                          |
| AGP / Gradle                    | 9.4.0 / 9.6.1（AGP 9 使用**内置 Kotlin**，不再需要 `org.jetbrains.kotlin.android` 插件）                  |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26                                                                                              |
| 数据存储                        | SharedPreferences（键值 + JSON，对应网页版的 localStorage）                                               |

> ⚠️ 版本必须成套使用：Miuix 0.9.3 由 Kotlin 2.4.0 编译，用更低版本的 Kotlin 编译会报元数据不兼容；
> 而 AGP 9 内置 Kotlin 的默认版本低于 2.4.0，因此根构建脚本里显式把 KGP 提到 2.4.0。

## 构建与安装

```bash
cd android
./gradlew assembleDebug            # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug             # 连接设备后直接安装
```

`./gradlew` 会自动下载并使用 Gradle 9.6.1（已缓存的机器上直接秒开）；
用 Android Studio 直接 `Open` 本 `android` 目录即可，IDE 同样走 wrapper。

`local.properties` 里的 `sdk.dir` 是本机路径，换机器需要改成自己的 SDK 路径（或删除该文件由 IDE 生成）。

## 界面结构

| 页面 | 对应网页版       | 主要内容                                                                                  |
| ---- | ---------------- | ----------------------------------------------------------------------------------------- |
| 计时 | `#page-timer`    | 模式分段按钮（`TabRow`）、圆环倒计时（`Canvas` + 呼吸光晕）、重置/开始暂停/跳过、轮次圆点 |
| 统计 | `#page-stats`    | 今日与累计数据格（`StatCell`）、本周柱状图（`WeekChart`）、导出 JSON（系统「创建文档」）  |
| 待办 | `#page-todos`    | 输入框（`TextField`）+ 列表（`BasicComponent` + `Checkbox`）、勾选/删除                   |
| 设置 | `#page-settings` | 时长步进器、行为开关（`Switch`）、强调色色板、深浅模式、背景图片（相册选图 + 暗度滑杆）   |

外壳用 Miuix `Scaffold`：手机是 `NavigationBar`（底部导航），宽度 ≥ 600dp 自动切成 `NavigationRail`（侧边导航），
与网页版「窄屏底部栏 / 宽屏左侧栏」的响应式策略一致。

## 目录结构

```
app/src/main/java/com/miuix/pomodoro/
├── MainActivity.kt              入口，edge-to-edge
├── PomodoroViewModel.kt         唯一状态源：计时/统计/待办/设置
├── data/
│   ├── Models.kt                数据模型与强调色候选
│   └── PomodoroRepository.kt    SharedPreferences + JSON 持久化
├── ui/
│   ├── AppTheme.kt              基于 MiuixTheme 注入强调色/深浅色/毛玻璃底色
│   ├── AppRoot.kt               Scaffold + 顶栏 + 导航 + 背景层 + 页面切换动效 + Toast
│   ├── AppIcons.kt              自绘图标（Miuix 图标集缺失的减号/日月/柱状图/画中画）
│   ├── LoadingOverlay.kt        冷启动加载页（呼吸图标 + App/Font 双进度条）
│   ├── MiniTimer.kt             可拖动的悬浮迷你计时器
│   ├── components/              SectionCard / Stepper / StatCell / TimerRing / WeekChart
│   └── screens/                 四个页面
└── util/
    ├── Chime.kt                 提示音（ToneGenerator 合成，替代 WebAudio）
    └── Notifier.kt              结束提醒的桌面通知
```

## 动效

网页版的动效在原生版里逐条对应实现：

| 网页版                         | 原生版实现                                                                                    |
| ------------------------------ | --------------------------------------------------------------------------------------------- |
| `#loading` 双进度条 + 呼吸图标 | `LoadingOverlay`：帧回调驱动进度，结束后淡出；加载期间吞掉点击                                |
| `pageIn` 页面淡入上滑          | `AnimatedContent` + `fadeIn`/`slideInVertically`，配合 `SaveableStateHolder` 保留各页滚动位置 |
| `popIn` 圆环弹入               | 切换模式时圆环用 `spring` 缩放回弹                                                            |
| `iconPop` 开始/暂停图标        | 图标每次切换从 0.5 弹到 1                                                                     |
| `bump` 统计数值跳动            | `StatCell` 数值变化时缩放回弹（首次显示不触发）                                               |
| `barGrow` 柱状分布生长         | 每周柱子按 index 错落延迟生长                                                                 |
| `themeWash` 主题切换光晕       | `ThemeFlash`：径向渐变放大 + 淡出                                                             |

## 与网页版的实现差异

- **计时精度**：网页版用 `setInterval` 逐秒累减，本版改为「结束时间戳 + 150ms 轮询」，界面卡顿或系统延迟都不会走偏。
- **提示音**：网页版用 WebAudio 合成和弦，本版用系统 `ToneGenerator` 播放对应音调。
- **背景毛玻璃**：网页版用 `backdrop-filter` 只模糊卡片背后；Compose 里改为「整图 + 压暗层 + 半透明卡片底色」
  （`AppTheme(translucentSurface = true)` 会把卡片色降到 62% 不透明度），观感接近且不依赖新 API。
- **导出数据**：网页版用 `Blob` 直接下载，本版走系统文件选择器（SAF）写入 JSON。

## 后续可做

- 前台服务 + 常驻通知：让计时在后台/锁屏时也能可靠提醒（目前进程被系统回收后计时会停止）。
- 桌面小组件、快捷方式。
- 用 `miuix-blur` 模块做真正的逐卡片背景模糊。

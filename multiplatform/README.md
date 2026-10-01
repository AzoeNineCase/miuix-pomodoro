# PomodoroTimer —— Compose Multiplatform 版

同一款番茄钟（Miuix 风格）的原生多平台实现：**Android / Windows / Linux / macOS / iOS** 共用一套 Kotlin + Compose UI（`composeApp/src/commonMain`），平台差异收敛在 `expect/actual` 与各平台入口里。

> 产物与 [miuix 官方预览包](https://github.com/compose-miuix-ui/miuix/releases) 对齐：android apk / windows exe / linux x64+arm64 / darwin dmg / ios 未签名 ipa。

## 结构

```
composeApp/
├── src/commonMain/   共用：App.kt（全部 UI 与动画）、State.kt（计时/统计/存储接口）、Theme.kt
├── src/androidMain/  MainActivity + SharedPreferences 存储 + 前台服务/通知
├── src/desktopMain/  main.kt（窗口入口）+ 桌面平台实现
└── src/iosMain/      MainViewController + NSUserDefaults 存储 + UIKit/AudioToolbox 实现
iosApp/               iOS 的 SwiftUI 壳（XcodeGen 生成 xcodeproj）
```

## 各平台构建

| 平台    | 命令                                                            | 产物                                                                |
| ------- | --------------------------------------------------------------- | ------------------------------------------------------------------- |
| Android | `./gradlew :composeApp:assembleDebug`                           | `composeApp/build/outputs/apk/debug/composeApp-debug.apk`           |
| Windows | `gradlew.bat :composeApp:createReleaseDistributable`            | `composeApp/build/compose/binaries/main-release/app/PomodoroTimer/` |
| Linux   | `./gradlew :composeApp:createReleaseDistributable`              | 同上（`bin/PomodoroTimer`）                                         |
| macOS   | `./gradlew :composeApp:packageReleaseDmg`                       | `…/main-release/dmg/*.dmg`                                          |
| iOS     | macOS + Xcode：`cd iosApp && xcodegen generate` 后 `xcodebuild` | 未签名 `.app`（再打成 `.ipa`）                                      |

iOS 打包（CI 同款流程）：

```bash
cd iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release \
  -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
mkdir -p Payload && cp -R build/Build/Products/Release-iphoneos/iosApp.app Payload/
zip -r PomodoroTimer.ipa Payload
```

## 云端六平台构建

[`.github/workflows/build-multiplatform.yml`](../.github/workflows/build-multiplatform.yml)：

- **手动**：Actions → _Build Multiplatform_ → Run workflow → 从 Artifacts 下载
- **打标签**：`git tag v1.0.0 && git push origin v1.0.0` → 自动创建 Release 并附带全部产物

| Job         | Runner                            | 产物                                         |
| ----------- | --------------------------------- | -------------------------------------------- |
| android     | ubuntu-latest                     | `PomodoroTimer-android-universal-<ver>.apk`  |
| windows     | windows-latest                    | `PomodoroTimer-windows-x64-exe-<ver>.zip`    |
| linux-x64   | ubuntu-latest                     | `PomodoroTimer-linux-x64-bin-<ver>.zip`      |
| linux-arm64 | ubuntu-24.04-arm                  | `PomodoroTimer-linux-arm64-bin-<ver>.zip`    |
| macos       | macos-14                          | `PomodoroTimer-darwin-arm64-dmg-<ver>.dmg`   |
| ios         | macos-14（xcodegen + xcodebuild） | `PomodoroTimer-ios-arm64-unsigned-<ver>.ipa` |

## 版本组合（必须成套）

| 组件                         | 版本                                                   |
| ---------------------------- | ------------------------------------------------------ |
| Kotlin                       | 2.4.10                                                 |
| Compose Multiplatform        | 1.12.0                                                 |
| Miuix（miuix / miuix-icons） | 0.8.8                                                  |
| AGP / Gradle                 | 9.1.0 / 9.3.1                                          |
| kotlinx-datetime             | 0.7.1（与 material3 1.9.0 的传递依赖一致，**勿降级**） |
| JDK                          | 21（`jvmToolchain(21)`）                               |
| compileSdk / minSdk          | 37 / 26                                                |

## 已知说明

- iOS / macOS 产物**未签名**：iOS 需自行重签（如 Sideloadly / AltStore），macOS 需右键“打开”绕过 Gatekeeper。
- Windows / Linux 产物是 jpackage 的 app-image（自带运行时），解压即用；未做代码签名。
- Android APK 为 debug 签名（预览用途）。
- `org.gradle.java.home` 已移除：本机构建请保证 `JAVA_HOME` 指向 JDK 21。

## 与网页版的一致性验证（原生 UI 对齐）

目标：六平台原生版与 `index.html`（以及内嵌同一份网页的安卓版）观感一致。为此建了一套**可复现的像素比对闭环**：

```bash
# 1) 原生侧：离屏渲染各页面（ImageComposeScene，无需窗口），默认 1280×800dp @2 → 2560×1600
./gradlew :composeApp:screenshots

# 2) 网页侧：headless Edge + CDP 截图（复用 android 内置字体走本地 HTTP，渲染与安卓实机一致）
node tools/webshot.js timer dark  "%TEMP%\shot\web-timer-dark.png"
node tools/webshot.js stats dark  "%TEMP%\shot\web-stats-dark.png"

# 3) 逐像素比对：平均差 / 超阈像素占比 / 条带定位 + 热力图与并排图
python tools/compare_shots.py composeApp/build/screenshots "%TEMP%\shot" "%TEMP%\diff"
python tools/measure.py <原生图> <网页图>      # 关键几何与取色
```

**实测结果**（平均差为 0–255 尺度，越小越接近）：

| 页面 | 平均差 | 最大差 | >32 像素占比 |
| --- | --- | --- | --- |
| 计时页 · 深色 | 1.49 | 204 | 1.11% |
| 计时页 · 浅色 | 1.84 | 240 | 0.87% |
| 统计页 · 深色 | 1.76 | 204 | 1.37% |
| 设置页 · 浅色 | 1.95 | 240 | 1.17% |
| 待办页 · 浅色 | 1.25 | 240 | 0.68% |

背景/卡片等大面积色块在抽样点**逐像素相同**，残差主要来自文字光栅化（Skia vs Chromium）与进度环发光。

**已知不适用等价物**：`backdrop-filter`（背层模糊）在 Compose 无对应能力；极光背景是低频渐变，模糊与否视觉差异极小，底色/描边/阴影已完全对齐。

**被墙时的推送通道**：`github.com:443` 不可达而 `api.github.com` 正常时，用 `node tools/gh-push.js main` 经 Git Data API 推送（内部自检「远端 tree == 本地 tree」）。

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

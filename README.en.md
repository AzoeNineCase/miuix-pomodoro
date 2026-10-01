# Pomodoro · Miuix

English | [简体中文](README.md)

A Pomodoro timer dressed in the MIUI / HyperOS (**Miuix**) design language — frosted glass, springy animations and MIUI-style overscroll stretch.

> 🌐 **Live demo**: <https://simlalsy.github.io/miuix-pomodoro/> (hosted on GitHub Pages)

- **Web** — a single, dependency-free `index.html`: open it and it just works.
- **Android** — a WebView shell that runs the very same page pixel-for-pixel, plus a native foreground service so the timer keeps counting with the screen off, a persistent notification, native tones and more.
- **Multiplatform** — a native **Compose Multiplatform** port in `multiplatform/` (same Miuix UI), with CI producing preview packages for **Android / Windows / Linux (x64 + arm64) / macOS / iOS**.

|                Timer · Dark                |              Statistics              |                    To-dos                     |
| :----------------------------------------: | :----------------------------------: | :-------------------------------------------: |
| ![Timer](docs/screenshots/timer-dark.png)  | ![Stats](docs/screenshots/stats.png) |     ![Todos](docs/screenshots/todos.png)      |
|                **Settings**                |              **About**               |              **Mobile · Aurora**              |
| ![Settings](docs/screenshots/settings.png) | ![About](docs/screenshots/about.png) | ![Aurora](docs/screenshots/mobile-aurora.png) |

<p align="center">
  <img src="docs/screenshots/mobile-stats-light.png" width="320" alt="Mobile · light theme">
</p>

## Features

- **Timer** — focus / short break / long break, adjustable durations, long-break interval, auto-start next session, skip & reset, per-cycle progress dots.
- **Themes** — system / light / dark / aurora, 7 accent colors, frosted-glass cards, page transitions and spring curves; MIUI-style overscroll stretch at scroll edges.
- **Lock-screen timing (Android)** — a foreground service keeps the countdown alive with the screen off; the notification shows the remaining time and offers pause / stop / skip, whose actions round-trip to the page.
- **Tones** — 5 built-in tones (chime / bell / marimba / wood / beep) plus your own audio uploads, choosable separately for "start" and "end". Custom audio is capped at 10 s with a gentle fade-out.
- **Statistics** — today / all-time / weekly distribution, one-tap JSON export.
- **To-dos** — a lightweight list with check & delete.
- **Backgrounds** — gradient, image URL, local upload, or random images from public APIs (auto-refreshes every 5 min).
- **About page** — version, author GitHub and every open-source component used, one tap away.

## Quick start

### Web

```bash
# No build step, no dependencies — just open it:
start index.html          # Windows
open index.html           # macOS
```

The first load fetches the _Inter_ and _Material Symbols_ fonts from Google Fonts; without network the page still works but icons fall back to ligature text. The Android app bundles both fonts locally and needs no network for them.

### Android

```bash
cd android
./gradlew assembleDebug     # output: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # install onto a connected device
```

- Requirements: JDK 17+, Android SDK (compileSdk 37). Point `local.properties` at your SDK, or delete the file and let the IDE generate it.
- The whole app is the web page running inside a WebView (`WebApp.kt`). An alternative 100% native [Miuix](https://github.com/compose-miuix-ui/miuix) Compose implementation ships in the same project and is one switch away — see [`android/README.md`](android/README.md).
- `android/tools/gen_tones.py` regenerates the built-in tone WAVs (pure stdlib; edit the parameters and re-run).

## Compose Multiplatform edition (native, all platforms)

`multiplatform/` is the same Pomodoro timer rewritten with **Compose Multiplatform + [Miuix](https://github.com/compose-miuix-ui/miuix)** — 100% native components, no WebView. CI produces the same set of preview packages as the [official Miuix previews](https://github.com/compose-miuix-ui/miuix/releases):

| Platform              | Artifact                                    | Notes                               |
| --------------------- | ------------------------------------------- | ----------------------------------- |
| Android               | `PomodoroTimer-android-universal-*.apk`     | debug-signed, installable as-is     |
| Windows x64           | `PomodoroTimer-windows-x64-exe-*.zip`       | portable, runtime bundled           |
| Linux x64 / arm64     | `PomodoroTimer-linux-{x64,arm64}-bin-*.zip` | run `bin/PomodoroTimer` after unzip |
| macOS (Apple Silicon) | `PomodoroTimer-darwin-arm64-dmg-*.dmg`      | unsigned, right-click to open       |
| iOS                   | `PomodoroTimer-ios-arm64-unsigned-*.ipa`    | unsigned, re-sign to install        |

👉 **Downloads**: [Releases](https://github.com/Simlalsy/miuix-pomodoro/releases) (published automatically on `v*` tags; or trigger a manual run under [Actions](https://github.com/Simlalsy/miuix-pomodoro/actions) and grab the artifacts).

```bash
cd multiplatform
./gradlew :composeApp:assembleDebug                  # Android APK
./gradlew :composeApp:createReleaseDistributable     # desktop app-image for the current OS
./gradlew :composeApp:packageReleaseDmg              # DMG (on macOS)
```

iOS needs macOS: `cd iosApp && xcodegen generate`, then build with Xcode or `xcodebuild` (see [`multiplatform/README.md`](multiplatform/README.md)).

## How it works

- **Single source of truth** — the Gradle task `syncWebAssets` copies `../index.html` into `app/src/main/assets/` at build time; there is no second copy to maintain.
- **Stable origin** — the page is served over `https://appassets.androidplatform.net/` via `shouldInterceptRequest`, so `localStorage` (settings / stats / todos) persists reliably.
- **Native bridges** (`WebApp.kt`, injected as `window.MiuixBridge`) — file chooser, JSON export to the Downloads folder, tone uploads, external links opened in the system browser, and timer-state sync to the foreground service.
- **Immersive & themed** — system bar insets are written into the page as `--safe-top` / `--safe-bottom`; a `MutationObserver` mirrors the page theme onto the status-bar icons.

## Repository layout

```
index.html                  web app — HTML + CSS + JS in one file
docs/screenshots/           screenshots used in this README
android/                    Android shell (Kotlin + WebView + foreground service)
├── app/src/main/java/com/miuix/pomodoro/
│   ├── WebApp.kt           WebView host + JS bridges
│   ├── PomodoroService.kt  foreground service: lock-screen timing, notification
│   ├── Tones.kt            SoundPool / MediaPlayer tone playback
│   └── ui/                 alternative native Miuix (Compose) implementation
└── tools/gen_tones.py      generates res/raw/tone_*.wav
multiplatform/              Compose Multiplatform edition (Android/Desktop/iOS)
├── composeApp/src/commonMain/    shared UI & state (Miuix)
├── composeApp/src/iosMain/       iOS platform bindings
└── iosApp/                        SwiftUI shell for iOS (XcodeGen)
.github/workflows/build-multiplatform.yml  6-platform cloud build
```

## Open source components

| Component                                                           | Used for                     | License     |
| ------------------------------------------------------------------- | ---------------------------- | ----------- |
| [Miuix for Compose](https://github.com/compose-miuix-ui/miuix)      | design reference & native UI | Apache-2.0  |
| [Material Symbols](https://github.com/google/material-design-icons) | icon font                    | Apache-2.0  |
| [Inter](https://github.com/rsms/inter)                              | numerals & Latin text        | SIL OFL 1.1 |
| [Jetpack Compose](https://github.com/androidx/androidx)             | Android UI toolkit           | Apache-2.0  |
| [Kotlin](https://github.com/JetBrains/kotlin)                       | language                     | Apache-2.0  |
| [Gradle](https://github.com/gradle/gradle)                          | build system                 | Apache-2.0  |

## License

[Apache License 2.0](LICENSE) © 2026 [Simlalsy](https://github.com/Simlalsy)

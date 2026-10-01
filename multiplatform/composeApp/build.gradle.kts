plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlin.get()
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")

    androidTarget()

    // iOS：产出静态 framework 供 iosApp(Xcode) 链接；全平台构建由 CI 在 macOS 上完成
    iosArm64 {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(libs.miuix.ui)
                implementation(libs.miuix.icons)
                implementation(libs.kotlinx.datetime)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation(compose.desktop.currentOs)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity)
                implementation(compose.ui)
            }
        }
    }
}

android {
    namespace = "com.example.pomodoro"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.pomodoro"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

compose.desktop {
    application {
        mainClass = "com.example.pomodoro.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "PomodoroTimer"
            packageVersion = "1.0.0"
            description = "Pomodoro Timer — Miuix style"
            vendor = "Simlalsy"
            windows {
                menuGroup = "PomodoroTimer"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
            macOS {
                bundleID = "com.example.pomodoro"
            }
            linux {
                packageName = "pomodorotimer"
            }
        }
    }
}

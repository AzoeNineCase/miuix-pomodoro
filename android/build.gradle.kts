buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 使用内置 Kotlin，这里把 KGP 提升到 Miuix 所需的版本
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}

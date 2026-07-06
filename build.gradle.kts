// Root build script.
//
// AGP 9 ships with built-in Kotlin support (its own Kotlin Gradle Plugin, default
// KGP 2.2.10). We want the pinned latest-stable Kotlin instead, so we put a higher
// KGP on the buildscript classpath here — Gradle then resolves the built-in Kotlin
// up to that version for every module. This is the documented AGP 9 upgrade path.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

// Spotless runs ktlint over all Kotlin source and the Gradle Kotlin DSL scripts.
// `./gradlew spotlessApply` fixes; `spotlessCheck` (wired into `check`) verifies.
// third_party/ is never formatted — it holds vendored, unmodified sources.
spotless {
    kotlin {
        target("app/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint()
    }
}

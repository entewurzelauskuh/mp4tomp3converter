import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android — AGP 9 has built-in Kotlin (see root build script).
    alias(libs.plugins.kotlin.compose)
}

// Spec ABIs. Override for faster local native builds, e.g. `-Pabi=arm64-v8a`.
val abiOverride: List<String>? =
    providers.gradleProperty("abi").orNull?.split(",")?.map { it.trim() }

android {
    namespace = "io.github.entewurzelauskuh.mp4tomp3"
    compileSdk = 36

    // Only the r30-beta1 NDK is installed on this build host. The spec preferred an LTS NDK,
    // but this is what is available; pin it explicitly so builds are reproducible.
    ndkVersion = "30.0.14904198"

    defaultConfig {
        applicationId = "io.github.entewurzelauskuh.mp4tomp3"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Spec ABIs are arm64-v8a, armeabi-v7a, x86_64. The emulators on this Apple-silicon
            // host are arm64-v8a, so all local testing uses that ABI; the others ship for real
            // devices. Narrow with `-Pabi=...` for faster local native builds.
            abiFilters += abiOverride ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    buildTypes {
        release {
            // Release is debug-signed for now (see docs/RELEASING.md). Minification
            // stays off until the JNI/native surface (Phase 2) is in and keep-rules exist.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9 built-in Kotlin is configured via the nested `kotlin { }` block.
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

import com.android.build.api.artifact.SingleArtifact
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
            // R8 shrink + resource shrink (keeps the APK well under the 15 MB soft budget, N4);
            // JNI keep rules live in proguard-rules.pro. Debug-signed for now so the release
            // APK installs and converts without a keystore (see docs/RELEASING.md).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
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

    lint {
        // Fail the build on lint problems in our code (spec §9.5). Rather than a baseline (which
        // pins non-portable absolute paths and breaks fresh clones), disable the pure "newer
        // version available" upgrade nags — this project deliberately pins versions — and the
        // backup-rules nag. Genuine code warnings still fail the build.
        //
        // OldTargetApi is disabled for the same "pin deliberately" reason AND for portability:
        // it fires whenever the machine has a newer platform installed than targetSdk (36, the
        // latest *stable*). CI runners ship preview platforms, so leaving it on makes lint pass
        // locally but fail on CI. targetSdk tracks the latest stable by intent, not previews.
        warningsAsErrors = true
        checkReleaseBuilds = true
        disable +=
            setOf(
                "GradleDependency",
                "NewerVersionAvailable",
                "AndroidGradlePluginVersion",
                "DataExtractionRules",
                "OldTargetApi",
            )
    }
}

// Static privacy check (spec N1/§9.5): the merged app manifest must never declare INTERNET.
// Runs over the real MERGED_MANIFEST artifact (not the test APK).
androidComponents {
    onVariants { variant ->
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val assertNoInternet = tasks.register("assertNo${variantName}Internet") {
            inputs.file(mergedManifest)
            doLast {
                val text = mergedManifest.get().asFile.readText()
                // Match a real <uses-permission> element (tolerant of attribute spacing and
                // quote style), not the word "INTERNET" appearing in a manifest comment.
                val declared = Regex(
                    """<uses-permission[^>]*android:name\s*=\s*["']android\.permission\.INTERNET["']""",
                ).containsMatchIn(text)
                require(!declared) {
                    "The merged ${variant.name} manifest declares android.permission.INTERNET. " +
                        "This app must never request INTERNET (spec N1)."
                }
            }
        }
        // Wire into BOTH `check` and the variant's assembly, so the documented release gate
        // (which assembles but does not run `check`) still enforces the no-INTERNET invariant.
        // `assemble<Variant>` isn't registered yet during onVariants, so match it lazily.
        tasks.named("check").configure { dependsOn(assertNoInternet) }
        tasks.matching { it.name == "assemble$variantName" }.configureEach {
            dependsOn(assertNoInternet)
        }
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
    implementation(libs.androidx.compose.material.icons.core)

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

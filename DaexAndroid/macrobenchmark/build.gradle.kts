plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

// A `com.android.test` module, not `com.android.application` - this compiles instrumented tests
// that run against the real :app APK (built with its own `benchmark` build type, see
// app/build.gradle.kts) rather than an APK of its own. Requires a real, unlocked device or
// emulator with USB debugging (`:macrobenchmark:connectedBenchmarkAndroidTest`); this environment
// has no adb/device access, so this module can be reviewed for correctness but not run here.
android {
    namespace = "com.daex.android.macrobenchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 28 // androidx.benchmark.macro's StartupTimingMetric requires API 28+.
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }

    targetProjectPath = ":app"

    // Macrobenchmark measures against a real, non-debuggable build for representative numbers.
    // Declaring a same-named "benchmark" build type here is what makes AGP pair this module's
    // instrumented tests against :app's own "benchmark" build type (release-like, but
    // debug-signed so it doesn't need a release keystore) instead of the default debug/release
    // pairing - build-type names are matched by name across the two modules, not configured here.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
}

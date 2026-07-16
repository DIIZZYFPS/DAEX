import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("io.objectbox")
    id("io.gitlab.arturbosch.detekt")
    id("io.github.takahirom.roborazzi")
}

// release-please bumps this value directly (see .github/release-please-config.json's
// "generic" extra-files entry for app/build.gradle.kts); versionCode is derived from it
// below so the two can never drift out of sync again.
val appVersionName = "0.5.0" // x-release-please-version

fun versionCodeFromName(name: String): Int {
    val parts = name.split(".").map { it.toIntOrNull() ?: 0 }
    return parts.getOrElse(0) { 0 } * 10000 + parts.getOrElse(1) { 0 } * 100 + parts.getOrElse(2) { 0 }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.daex.android"
    compileSdk = 36
    ndkVersion = "30.0.14904198"

    defaultConfig {
        applicationId = "com.daex.android"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeFromName(appVersionName)
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Release-like (non-debuggable) but debug-signed, so :macrobenchmark can install and
        // measure it on any device without a release keystore. Never shipped.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        // Fails the build on any NEW lint issue; the 49 pre-existing warnings (dependency
        // version lag, a few Compose perf hints, etc. - none are errors) are frozen in
        // lint-baseline.xml rather than blocking this pass. Regenerate the baseline
        // (./gradlew :app:updateLintBaseline) deliberately, not to silence a new real issue.
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
    }
    testOptions {
        unitTests {
            // Safety net, not the primary testing strategy: any android.* call incidentally
            // exercised by code under test without being mocked returns a default value
            // instead of throwing "Stub!".
            isReturnDefaultValues = true
            // Robolectric-backed Compose UI tests need real resource resolution.
            isIncludeAndroidResources = true
        }
    }
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    
    // UI Foundation & Material
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    
    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Core and Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Markdown
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.25.0")

    // LiteRT-LM Inference Engine
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")
    
    // Sherpa-ONNX offline speech engine
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.1")
    
    // MediaPipe Tasks Text for LiteRT embedding inference
    implementation("com.google.mediapipe:tasks-text:0.10.14")
    
    // Kotlin reflection (needed for LiteRT tool reflection)
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")

    // ObjectBox Kotlin Extensions
    implementation("io.objectbox:objectbox-kotlin:4.0.0")

    // Required by the :macrobenchmark module's default CompilationMode (installs a baseline
    // profile before measuring) on API 34+; also speeds up real cold starts in production.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // PDF Parsing
    implementation("com.itextpdf:itext7-core:7.2.5")

    // SQLite Bundled Driver for FTS5 support
    implementation("androidx.sqlite:sqlite:2.5.1")
    implementation("androidx.sqlite:sqlite-bundled:2.5.1")

    // Debug tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Desktop-native ObjectBox binaries so integration tests can run a real BoxStore on the
    // JVM (no emulator) - linux for the ubuntu-latest CI runner, windows for local dev.
    testImplementation("io.objectbox:objectbox-linux:4.0.0")
    testImplementation("io.objectbox:objectbox-windows:4.0.0")

    // Compose UI + screenshot tests, Robolectric-backed - JVM only, no emulator.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.58.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.58.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.58.0")

    // Instrumented tests (app/src/androidTest) - real device/emulator only, no JVM fallback.
    // Verified running on a real device via `:app:connectedDebugAndroidTest`.
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Compose's ui-test-junit4 pulls in Espresso transitively for idle-sync; pinned explicitly
    // since the older transitive version reflects into the since-removed
    // InputManager.getInstance() and throws NoSuchMethodException on newer Android versions.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

// Roborazzi's default file-path strategy resolves captureRoboImage(...) filenames relative to
// the module's working directory, not a configurable outputDir - so each test passes an explicit
// "src/test/screenshots/..." path instead of relying on global output-dir config here. Baseline
// images are committed there; `recordRoborazziDebug` (re)generates them, `compareRoborazziDebug`/
// `verifyRoborazziDebug` diff against them and fail on an unintended visual regression.

// androidx.sqlite:sqlite-bundled (a main `implementation` dep, for RAG/message FTS5) resolves to
// its Android variant everywhere by default - fine for the real app, but that variant expects its
// native library packaged into an APK's jniLibs, not present on a plain JVM unit test's
// java.library.path. Substitute the -jvm variant (ships a desktop-native binary instead) only for
// the *UnitTest* classpaths (AGP names these "debugUnitTest.../releaseUnitTest...", not
// "test..." - "androidTest" configs are untouched since those run on a real device/emulator
// where the Android variant is correct), so integration tests can open a real FTS5 connection
// without one.
configurations.matching { it.name.contains("UnitTest") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.sqlite:sqlite-bundled"))
            .using(module("androidx.sqlite:sqlite-bundled-jvm:2.5.1"))
    }
}

kotlin {
    jvmToolchain(21)
}

detekt {
    // Google's Android style ruleset on top of detekt's own defaults, rather than a fully
    // custom detekt.yml - this is an existing codebase, not a greenfield one.
    buildUponDefaultConfig = true
    baseline = file("config/detekt/baseline.xml")
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
    }
}

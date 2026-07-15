import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("io.objectbox")
}

// release-please bumps this value directly (see .github/release-please-config.json's
// "generic" extra-files entry for app/build.gradle.kts); versionCode is derived from it
// below so the two can never drift out of sync again.
val appVersionName = "0.4.0" // x-release-please-version

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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions {
        unitTests {
            // Safety net, not the primary testing strategy: any android.* call incidentally
            // exercised by code under test without being mocked returns a default value
            // instead of throwing "Stub!".
            isReturnDefaultValues = true
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
}

kotlin {
    jvmToolchain(21)
}

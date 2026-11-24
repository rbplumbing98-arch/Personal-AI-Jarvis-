plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jarvis.assistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jarvis.assistant"
        manifestPlaceholders["appLabel"] = "J.A.R.V.I.S."
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("JARVIS_VERSION_CODE")?.toIntOrNull() ?: 4
        versionName = "2.2.${System.getenv("JARVIS_VERSION_CODE") ?: "4"}"
    }
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("JARVIS_KEYSTORE_PATH") ?: "missing-release-key.jks")
            storePassword = System.getenv("JARVIS_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("JARVIS_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("JARVIS_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".test"
            manifestPlaceholders["appLabel"] = "J.A.R.V.I.S. Test"
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.media:media:1.7.0")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("dev.ffmpegkit-maintained:llama-android:0.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "biz.pixelperfectstudios.personaspeak.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "biz.pixelperfectstudios.personaspeak"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The legacy `:keyboard` stub was replaced by the vendored AnySoftKeyboard
    // tree (ADR-0004). Wiring the app to ASK's :ime:app is the graft PR's
    // concern; this PR is ingestion only, so the dependency is dropped for now.
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}

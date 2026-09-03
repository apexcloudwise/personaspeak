plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "biz.pixelperfectstudios.personaspeak.ime"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The graph's public surface exposes personaspeak-ui types
    // (RewritePanelViewModel, BundledPersonaRepository), so consumers of this
    // module need them on their compile classpath.
    api(project(":personaspeak-ui"))
    implementation(project(":personaspeak-data"))
    implementation(project(":personaspeak-providers"))
    implementation(project(":core-personas"))
    implementation(project(":core-providers"))
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.savedstate)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    // ImeViewTreeOwnersTest drives a real ComposeView through the owner
    // installation path.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui)

    // ADR-0003: the capture -> transform -> replace path needs a
    // real-InputConnection test, not just unit tests against fakes.
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

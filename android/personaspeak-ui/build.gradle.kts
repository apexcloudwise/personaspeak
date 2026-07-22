plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "biz.pixelperfectstudios.personaspeak.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets.getByName("main").assets.srcDir("../../personas")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-personas"))
    implementation(project(":core-providers"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.core)
}

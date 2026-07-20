plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.snakeyaml)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

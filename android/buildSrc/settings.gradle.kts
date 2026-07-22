// Root buildSrc for the unified PersonaSpeak graph. Sources live in the
// vendored ASK snapshot (../keyboard/buildSrc); nothing is copied.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "buildSrc"

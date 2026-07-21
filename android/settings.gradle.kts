pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_PROJECT so the included ASK build under android/keyboard/ can
    // declare its own repositories (it does, in its root build.gradle's
    // allprojects block). The root build still uses the repos declared here.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "personaboard"

include(":core-personas")
include(":core-providers")
include(":app")

// AnySoftKeyboard is vendored at android/keyboard/ (see
// android/keyboard/UPSTREAM.md, ADR-0004). It keeps its own settings/build
// files unchanged and enters our build as a composite (included) build.
// The graft PR will substitute a PersonaSpeak-facing module for the legacy
// `:keyboard` slot; this PR is ingestion only.
includeBuild("keyboard")

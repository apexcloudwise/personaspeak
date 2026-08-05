plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    // Required on the root classpath by the vendored ASK build scripts, which
    // apply these by id inside keyboard/gradle/*.gradle and keyboard/ime/app.
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.navigation.safeargs) apply false
    alias(libs.plugins.errorprone) apply false
}

// Single source root for the pinned ASK snapshot. Vendored ASK build scripts
// resolve their own config/gradle/addons support files through this extra
// instead of Gradle's rootDir, which now points at the unified root.
extra["askSourceRoot"] = file("keyboard")

// Marks this as the unified PersonaSpeak root build, which ships exactly one
// APK from ASK's :ime:app. Upstream's apk_module.gradle reads this to skip
// registering its copy<Variant>Apk / copy<Variant>Aab convenience tasks: those
// copy the built artifact into android/outputs/, producing a second file that
// is indistinguishable from a shippable APK. A nested or standalone upstream
// build never sets this and keeps upstream's behaviour unchanged.
extra["personaSpeakUnifiedBuild"] = true

// ASK-wide ext values (SDK levels, build tools, Robolectric, CI flags) that
// upstream's root build applied to every project.
apply(from = "keyboard/gradle/root_all_projects_ext.gradle")

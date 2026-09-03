// PersonaSpeak prediction-engine spike (issue #124 segment 1).
//
// A THIRD Gradle root, deliberately outside both host roots: the unified
// ASK root (../keyboard, driven by ../settings.gradle.kts) and the
// vendored FlorisBoard root (../florisboard). Hosts consume the engine
// as an artifact via composite build (includeBuild) — see README.md.
rootProject.name = "personaspeak-engine-root"

include(":engine")

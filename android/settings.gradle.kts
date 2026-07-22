pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "personaboard"

include(":core-personas")
include(":core-providers")
include(":keyboard-stub")
include(":personaspeak-ui")
include(":app")

// Restricted ASK closure: every logical path maps explicitly onto the vendored
// snapshot under keyboard/. The set must stay identical to
// scripts/expected-ask-projects.txt (minus the three first-party libraries).
fun askProject(path: String, directory: String) {
    include(path)
    project(path).projectDir = file("keyboard/$directory")
}

askProject(":api", "api")
askProject(":junit-sharding", "junit-sharding")
askProject(":addons", "addons")
askProject(":addons:base", "addons/base")
askProject(":addons:languages", "addons/languages")
askProject(":addons:languages:english", "addons/languages/english")
askProject(":addons:languages:english:pack", "addons/languages/english/pack")
askProject(":ime", "ime")
askProject(":ime:base", "ime/base")
askProject(":ime:base-rx", "ime/base-rx")
askProject(":ime:base-test", "ime/base-test")
askProject(":ime:prefs", "ime/prefs")
askProject(":ime:notification", "ime/notification")
askProject(":ime:remote", "ime/remote")
askProject(":ime:fileprovider", "ime/fileprovider")
askProject(":ime:addons", "ime/addons")
askProject(":ime:dictionaries", "ime/dictionaries")
askProject(":ime:dictionaries:jnidictionaryv1", "ime/dictionaries/jnidictionaryv1")
askProject(":ime:dictionaries:jnidictionaryv2", "ime/dictionaries/jnidictionaryv2")
askProject(":ime:nextword", "ime/nextword")
askProject(":ime:pixel", "ime/pixel")
askProject(":ime:overlay", "ime/overlay")
askProject(":ime:gesturetyping", "ime/gesturetyping")
askProject(":ime:voiceime", "ime/voiceime")
askProject(":ime:releaseinfo", "ime/releaseinfo")
askProject(":ime:chewbacca", "ime/chewbacca")
askProject(":ime:permissions", "ime/permissions")
askProject(":ime:app", "ime/app")

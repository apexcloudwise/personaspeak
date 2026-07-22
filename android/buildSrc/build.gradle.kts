// Exposes the vendored ASK build logic (MakeDictionaryPlugin, version
// generators, dictionary/word-list tasks, Groovy helpers) to every project in
// the unified root build without a nested Gradle invocation and without
// copying sources: the source sets point directly into the pinned snapshot at
// keyboard/buildSrc. Dependency and plugin versions mirror
// keyboard/buildSrc/build.gradle.
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    groovy
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    maven { url = uri("https://plugins.gradle.org/m2/") }
}

val askBuildSrc = rootDir.parentFile.resolve("keyboard/buildSrc")

sourceSets {
    main {
        java.srcDir(askBuildSrc.resolve("src/main/java"))
        groovy.srcDir(askBuildSrc.resolve("src/main/groovy"))
        kotlin.srcDir(askBuildSrc.resolve("src/main/kotlin"))
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.22.1")
    implementation(gradleApi())
    implementation(localGroovy())

    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.10")
}

gradlePlugin {
    plugins {
        create("makeDictionaryPlugin") {
            id = "make-dictionary"
            implementationClass = "MakeDictionaryPlugin"
        }
        create("manualVersionPlugin") {
            id = "ask.net.evendanan.autoversion"
            implementationClass = "net.evendanan.versiongenerator.VersionGeneratorPlugin"
        }
        create("simpleVersionPlugin") {
            id = "ask.net.evendanan.autoversion.simple"
            implementationClass = "net.evendanan.versiongenerator.SimpleVersionGeneratorPlugin"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

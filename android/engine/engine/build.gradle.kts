plugins {
    kotlin("jvm") version "2.2.0"
}

// Pure JVM by design and by module law: this module is the host-neutral
// prediction engine (issue #124). An `android.*` import here is a build
// failure waiting for a host — keep it out.
group = "com.personaspeak.engine"
version = "0.1.0-spike"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

plugins {
    kotlin("jvm") version "2.2.0"
    application
}

application {
    mainClass.set("com.personaspeak.engine.consumer.ConsumerProofKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.personaspeak.engine:engine")
}

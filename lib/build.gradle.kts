plugins {
    alias(libs.plugins.kotlin.jvm)
    id("java-library")
    id("BuildPlugin")
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.17.0"
}

dependencies {
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

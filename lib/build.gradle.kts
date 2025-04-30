plugins {
    alias(libs.plugins.kotlin.jvm)
    id("java-library")
    id("BuildPlugin")
}

dependencies {
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

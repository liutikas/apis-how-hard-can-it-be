plugins {
    alias(libs.plugins.kotlin.jvm)
    id("java-library")
    id("BuildPlugin")
}

dependencies {
    implementation(project(":lib"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

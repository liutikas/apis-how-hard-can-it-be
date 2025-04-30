plugins {
    alias(libs.plugins.kotlin.jvm)
    id("java-gradle-plugin")
}

dependencies {
    api(gradleApi())
}

gradlePlugin {
    plugins {
        create("BuildPlugin") {
            id = "BuildPlugin"
            implementationClass = "com.example.logic.BuildPlugin"
        }
    }
}

package com.example.logic

import org.gradle.api.Plugin
import org.gradle.api.Project

class BuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.setUpMetalavaTask()
    }
}
package com.example.logic

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.ByteArrayOutputStream
import javax.inject.Inject

internal fun Project.setUpMetalavaTask() {
    val javaExtension = extensions.getByType(JavaPluginExtension::class.java)
    val mainSourceSet = javaExtension.sourceSets.getByName("main")

    val sourcePaths: FileCollection =
        project.files(project.provider { mainSourceSet.allSource.srcDirs })
    val generateApi = tasks.register("generateApi", GenerateApiTask::class.java) {
        it.metalavaClasspath.from(getMetalavaClasspath())
        it.bootClasspath.from(bootClasspath())
        it.sourcePaths.from(sourcePaths)
        it.dependencyClasspath.from(mainSourceSet.compileClasspath)
        it.apiFile.set(layout.buildDirectory.file("current.txt"))
    }

    tasks.register("updateApi", UpdateApiTask::class.java) { task ->
        task.inputFile.set(generateApi.flatMap { it.apiFile })
        task.outputFile.set(layout.projectDirectory.file("api.txt"))
    }

    tasks.register("checkApi", CheckApiTask::class.java) {
        it.metalavaClasspath.from(getMetalavaClasspath())
        it.bootClasspath.from(bootClasspath())
        it.sourcePaths.from(sourcePaths)
        it.dependencyClasspath.from(mainSourceSet.compileClasspath)
        it.referenceApiFile.set(layout.projectDirectory.file("api.txt"))
    }
}

private const val METALAVA = "com.android.tools.metalava:metalava:1.0.0-alpha12"

private fun Project.getMetalavaClasspath(): FileCollection {
    val configuration =
        configurations.detachedConfiguration(dependencies.create(METALAVA))
    return project.files(configuration)
}

private fun Project.bootClasspath(): File {
    return File(rootDir, "android.jar")
}

@CacheableTask
abstract class CheckApiTask @Inject constructor(workerExecutor: WorkerExecutor) : MetalavaTask(workerExecutor) {
    @get:[InputFile PathSensitive(PathSensitivity.NONE)]
    abstract val referenceApiFile: RegularFileProperty
    @TaskAction
    fun execute() {
        runMetalava(listOf("--check-compatibility:api:released", referenceApiFile.get().asFile.absolutePath))
    }

}

@CacheableTask
abstract class GenerateApiTask @Inject constructor(workerExecutor: WorkerExecutor) : MetalavaTask(workerExecutor) {
    @get:OutputFile
    abstract val apiFile: RegularFileProperty

    @TaskAction
    fun execute() {
        runMetalava(
            listOf("--api", apiFile.get().asFile.absolutePath)
        )
    }
}


@CacheableTask
abstract class MetalavaTask @Inject constructor(private val workerExecutor: WorkerExecutor): DefaultTask() {
    /** Classpath containing Metalava and its dependencies. */
    @get:Classpath
    abstract val metalavaClasspath: ConfigurableFileCollection

    /** Android's boot classpath */
    @get:Classpath abstract val bootClasspath: ConfigurableFileCollection

    /** Dependencies (compiled classes) of the project. */
    @get:Classpath abstract val dependencyClasspath: ConfigurableFileCollection

    @get:[InputFiles PathSensitive(PathSensitivity.RELATIVE)]
    abstract val sourcePaths: ConfigurableFileCollection

    fun runMetalava(extraArgs: List<String>) {
        val allArgs = listOf(
            "--format=v4", "--warnings-as-errors",
            "--source-path",
            sourcePaths.filter { it.exists() }.joinToString(File.pathSeparator),
            "--classpath", (bootClasspath + dependencyClasspath.files).joinToString(File.pathSeparator)
        ) + extraArgs
        val workQueue = workerExecutor.processIsolation()
        workQueue.submit(MetalavaWorkAction::class.java) { parameters ->
            parameters.args.set(allArgs)
            parameters.metalavaClasspath.set(metalavaClasspath.files)
        }
    }
}

abstract class UpdateApiTask: DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun copy() {
        inputFile.get().asFile.copyTo(outputFile.get().asFile, overwrite = true)
    }
}

abstract class MetalavaWorkAction @Inject constructor(private val execOperations: ExecOperations) :
    WorkAction<MetalavaParams> {
    override fun execute() {
        val outputStream = ByteArrayOutputStream()
        var successful = false
        try {
            execOperations.javaexec {
                // Intellij core reflects into java.util.ResourceBundle
                it.jvmArgs = listOf("--add-opens", "java.base/java.util=ALL-UNNAMED")
                it.systemProperty("java.awt.headless", "true")
                it.classpath(parameters.metalavaClasspath.get())
                it.mainClass.set("com.android.tools.metalava.Driver")
                it.args = parameters.args.get()
                it.setStandardOutput(outputStream)
                it.setErrorOutput(outputStream)
            }
            successful = true
        } finally {
            if (!successful) {
                System.err.println(outputStream.toString(Charsets.UTF_8))
            }
        }
    }
}

interface MetalavaParams : WorkParameters {
    val args: ListProperty<String>
    val metalavaClasspath: SetProperty<File>
}
package dev.kosslan.compass.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Generates Kotlin bindings from the metadata uniffi embeds in a built library, using the
 * bindgen pinned by the workspace lockfile so it always matches the library.
 */
@CacheableTask
abstract class UniffiBindgen : DefaultTask() {
    @get:Inject
    abstract val exec: ExecOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @get:Internal
    abstract val workspace: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val library: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val config: RegularFileProperty

    @get:Input
    abstract val bindgen: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        files.delete { delete(output) }

        exec.exec {
            workingDir = workspace.get().asFile
            commandLine(
                "cargo",
                "run",
                "--locked",
                "--quiet",
                "--package",
                bindgen.get(),
                "--",
                "generate",
                "--library",
                library.get().asFile.absolutePath,
                "--language",
                "kotlin",
                "--no-format",
                "--out-dir",
                output.absolutePath,
            )
        }
    }
}

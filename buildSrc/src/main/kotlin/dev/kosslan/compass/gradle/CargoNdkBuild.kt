package dev.kosslan.compass.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "cargo keeps its own incremental build state")
abstract class CargoNdkBuild : DefaultTask() {
    @get:Inject
    abstract val exec: ExecOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @get:Internal
    abstract val workspace: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val crate: Property<String>

    @get:Input
    abstract val library: Property<String>

    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val platform: Property<Int>

    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * cargo-ndk copies out every shared library the build produced, including ones that
     * dependencies declare, so only the crate's own library is kept.
     */
    @TaskAction
    fun build() {
        val built = temporaryDir
        files.delete { delete(built, outputDirectory) }

        exec.exec {
            workingDir = workspace.get().asFile
            environment("ANDROID_NDK_HOME", ndkDirectory.get().asFile.absolutePath)
            commandLine(
                buildList {
                    addAll(listOf("cargo", "ndk", "--platform", platform.get().toString()))
                    addAll(listOf("--output-dir", built.absolutePath))
                    abis.get().forEach { addAll(listOf("--target", it)) }
                    addAll(listOf("build", "--locked", "--release", "--package", crate.get()))
                },
            )
        }

        files.copy {
            from(built) { include("*/${library.get()}") }
            into(outputDirectory)
        }
    }
}

import dev.kosslan.compass.gradle.CargoNdkBuild
import dev.kosslan.compass.gradle.UniffiBindgen

plugins {
    alias(libs.plugins.android.library)
}

val rust: Directory = rootProject.layout.projectDirectory.dir("rust")
val compass: Directory = rootProject.layout.projectDirectory.dir("compass")
val mobileCrate = "compass-mobile"
val mobileLibrary = "libcompass_mobile.so"
val targetAbis = providers.gradleProperty("compass.abis").map { it.split(',') }
val minimumSdk =
    libs.versions.android.minSdk
        .get()
        .toInt()

android {
    namespace = "dev.kosslan.compass.core"
    compileSdk {
        val (major, minor) =
            libs.versions.android.compileSdk
                .get()
                .split('.')
                .map(String::toInt)
        version = release(major) { minorApiLevel = minor }
    }
    buildToolsVersion =
        libs.versions.android.buildTools
            .get()
    ndkVersion =
        libs.versions.android.ndk
            .get()

    defaultConfig {
        minSdk = minimumSdk
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar(Char::uppercase)

        val cargo =
            tasks.register<CargoNdkBuild>("cargoBuild$suffix") {
                workspace = rust
                crate = mobileCrate
                library = mobileLibrary
                abis = targetAbis
                platform = minimumSdk
                ndkDirectory = androidComponents.sdkComponents.ndkDirectory
                sources.from(
                    rust.file("Cargo.toml"),
                    rust.file("Cargo.lock"),
                    rust.dir("src"),
                    rootProject.layout.projectDirectory.file("flake.lock"),
                    compass.file("Cargo.toml"),
                    compass.dir("crates"),
                )
            }

        val bindings =
            tasks.register<UniffiBindgen>("uniffiBindgen$suffix") {
                workspace = rust
                bindgen = "compass-mobile-bindgen"
                config = rust.file("uniffi.toml")
                library =
                    cargo.flatMap { task ->
                        val path =
                            task.abis.zip(
                                task.library,
                            ) { abis, name -> "${abis.first()}/$name" }
                        task.outputDirectory.file(path)
                    }
            }

        variant.sources.jniLibs?.addGeneratedSourceDirectory(cargo, CargoNdkBuild::outputDirectory)
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            bindings,
            UniffiBindgen::outputDirectory,
        )
    }
}

dependencies {
    api(libs.jna) {
        artifact {
            type = "aar"
        }
    }
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.annotation)
}

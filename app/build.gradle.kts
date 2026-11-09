plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersion = providers.gradleProperty("versionName").get()
val signingStore = providers.gradleProperty("signingStoreFile")

android {
    namespace = "dev.kosslan.compass"
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
        applicationId = "dev.kosslan.compass"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionName = appVersion
        versionCode =
            appVersion.split('.').map(String::toInt).let { (major, minor, patch) ->
                major * 10_000 + minor * 100 + patch
            }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (signingStore.isPresent) {
            create("release") {
                storeFile = file(signingStore.get())
                storePassword = providers.gradleProperty("signingStorePassword").get()
                keyAlias = providers.gradleProperty("signingKeyAlias").get()
                keyPassword = providers.gradleProperty("signingKeyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        disable +=
            listOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
                "OldTargetApi",
            )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}

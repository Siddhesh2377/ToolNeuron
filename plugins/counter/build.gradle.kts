plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dark.plugins.counter"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.dark.plugins.counter"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }
}

dependencies {
    compileOnly(project(":plugin-api"))

    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.ui)
    compileOnly(libs.androidx.compose.ui.graphics)
    compileOnly(libs.androidx.material3)

    compileOnly(libs.kotlinx.coroutines.android)
}

tasks.register<Zip>("packagePlugin") {
    group = "plugin"
    description = "Bundles this plugin into a .zip ready to install via PluginInstallScreen."
    dependsOn("assembleDebug")
    archiveFileName.set("counter.zip")
    destinationDirectory.set(layout.buildDirectory.dir("plugin"))

    val apkPath = layout.buildDirectory
        .file("outputs/apk/debug/counter-debug.apk")
        .map { it.asFile }

    from(apkPath.map { zipTree(it) }) {
        include("classes.dex", "classes*.dex", "lib/**/*.so")
    }
    from(layout.projectDirectory.file("manifest.json"))
}

import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    kotlin("plugin.serialization") version "2.1.21"
}
val localPropertiesFile = rootProject.file("local.properties")

android {
    namespace = "com.dark.neuroverse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dark.neurov"
        minSdk = 33
        targetSdk = 36
        versionCode = 3
        versionName = "0.3-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ALIAS", getProperty("ALIAS"))
    }
    buildTypes {
        release {
            isMinifyEnabled = true           // Enable code shrinking
            isShrinkResources = true         // Remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("org.jsoup:jsoup:1.21.1")



    //PROJECTS
    implementation(project(":task-manager"))
    implementation(project(":ai-manager"))
    implementation(project(":smollm"))
    implementation(project(":userData"))
    implementation(project(":vosk"))

    //DATABASE
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation("androidx.room:room-ktx:2.7.2")

    //UTILS
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.serialization.json)

    //KTX
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    //CORE-UI-LIBS
    implementation(libs.accompanist.insets)
    implementation(libs.accompanist.insets.ui)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.accompanist.navigation.animation)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.animation)
    implementation(libs.material)

    //TESTING
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    //DEBUG
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

fun getProperty(value: String): String {
    return if (localPropertiesFile.exists()) {
        val localProps = Properties().apply {
            load(FileInputStream(localPropertiesFile))
        }
        localProps.getProperty(value) ?: "\"sample_val\""
    } else {
        System.getenv(value) ?: "\"sample_val\""
    }
}
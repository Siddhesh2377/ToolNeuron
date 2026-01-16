import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.google.dagger.hilt)
}

val localPropertiesFile = rootProject.file("local.properties")

android {
    namespace = "com.dark.tool_neuron"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dark.tool_neuron"
        minSdk = 31
        targetSdk = 36
        versionCode = 14
        versionName = "1.1.0-Rag+Letex"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        buildConfigField("String", "ALIAS", getProperty("ALIAS"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}


dependencies {
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-android-compiler:2.57.2")

    // Hilt Navigation Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation ("org.apache.commons:commons-compress:1.28.0")
    implementation ("org.tukaani:xz:1.11")
    implementation(libs.okhttp)

    //Data-Ops
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.room.compiler)
    // Retrofit for API calls
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    // OkHttp for logging
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    //Projects
    implementation(":ai_gguf-release@aar")
    implementation(":ai_sd-release@aar")
    implementation(project(":memory-vault"))
    implementation(project(":neuron-packet"))

    // Core Android
    implementation(libs.androidx.core.ktx)

    // ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Material (for XML themes)
    implementation(libs.androidx.material)

    // Material 3 (for Compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.compose.ui.text)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
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
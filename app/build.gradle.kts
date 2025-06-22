import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}

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
        val localPropertiesFile = rootProject.file("local.properties")
        val apiKey = if (localPropertiesFile.exists()) {
            val localProps = Properties().apply {
                load(FileInputStream(localPropertiesFile))
            }
            localProps.getProperty("API_KEY") ?: "sample_dev_key"
        } else {
            System.getenv("API_KEY") ?: "sample_dev_key"
        }

        buildConfigField("String", "API_KEY", apiKey)
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

    //PROJECTS
    implementation(project(":ai-manager"))
    implementation(project(":smollm"))

    implementation("com.google.accompanist:accompanist-insets:0.30.1")
    implementation("com.google.accompanist:accompanist-insets-ui:0.36.0")

    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    //DATABASE
    implementation(libs.androidx.room.runtime)
    // Navigation Compose (NOT Multiplatform)
    implementation("androidx.navigation:navigation-compose:2.9.0")
    // Accompanist Navigation Animation
    implementation("com.google.accompanist:accompanist-navigation-animation:0.36.0")
    //noinspection KaptUsageInsteadOfKsp
    kapt(libs.androidx.room.compiler)

    //UTILS
    implementation(libs.androidx.datastore.preferences)

    //KTX
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    //CORE-UI-LIBS
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
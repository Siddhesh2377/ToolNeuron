import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.gms.services)
    kotlin("plugin.serialization") version "2.1.21"
}
val localPropertiesFile = rootProject.file("local.properties")

android {
    namespace = "com.dark.neuroverse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dark.neurov"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.3-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ALIAS", getProperty("ALIAS"))

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        prefab = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += arrayOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

    dependencies {
        //FIREBASE
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.firestore.ktx)
        implementation(libs.firebase.database)

        //NET
        implementation(libs.jsoup)
        implementation(libs.okhttp)
        implementation(libs.moshi)
        implementation(libs.moshi.kotlin)

        implementation(libs.androidx.lifecycle.runtime.compose)
        //CORE
        implementation(libs.kotlin.stdlib)
        implementation(libs.androidx.lifecycle.viewmodel.ktx)
        implementation("org.apache.commons:commons-compress:1.28.0")
        coreLibraryDesugaring(libs.desugar.jdk.libs)

        //LIBS
        implementation(":plugin-api-release@aar")
        implementation(":ai-core-release@aar")

        //PROJECTS
        api(project(":ai-module"))
        api(project(":plugins"))
        api(project(":data-hub-lib"))

        //UTILS
        implementation(libs.androidx.datastore.preferences)
        implementation(libs.androidx.biometric)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.gson)

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
        implementation(libs.androidx.material)
        implementation(libs.androidx.material3)
        implementation(libs.androidx.animation)

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
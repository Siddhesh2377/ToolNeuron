plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dark.plugin_api"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

    tasks.register<Copy>("exportPluginAar") {
        dependsOn("assembleRelease") // Ensure release AAR is built

        val aarFile = layout.buildDirectory.file("outputs/aar/plugin-api-release.aar").get().asFile


        if (!aarFile.exists()) {
            throw GradleException("❌ AAR file not found: ${aarFile.absolutePath}")
        }

        from(aarFile)
        rename { _ -> "plugin-1.0.0.aar" }
        into(layout.buildDirectory.dir("libs"))

        doLast {
            println("✅ Exported plugin AAR to libs/plugin-1.0.0.aar")
        }
    }



dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
}

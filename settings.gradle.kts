pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tool-Neuron"
include(":app")
include(":hxs")
include(":hxs_encryptor")
include(":download_manager")
include(":networking")
include(":native-server")
include(":plugin-exc")
include(":plugin-api")
include(":plugins:notes")
include(":plugins:counter")
include(":plugins:expense")

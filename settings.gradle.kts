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
        maven(url = "https://jitpack.io")
        flatDir {
            dir("${rootDir.path}/libs")
        }

    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven(url = "https://jitpack.io")
        flatDir {
            dir("${rootDir.path}/libs")
        }
    }
}

rootProject.name = "NeuroVerse"
include(":app")
include(":ai-module")
include(":plugins")
include(":data-hub-lib")
include(":plugin-api")
include(":ai-engine")
